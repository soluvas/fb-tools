package org.soluvas.fbcli;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;
import org.jboss.weld.environment.se.bindings.Parameters;
import org.jboss.weld.environment.se.events.ContainerInitialized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import akka.dispatch.Await;
import akka.dispatch.Future;
import akka.dispatch.Futures;
import akka.util.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Lists;

/**
 * @author ceefour
 */
public class FbCli {
	private transient Logger log = LoggerFactory.getLogger(FbCli.class);
	@Inject @Parameters String[] args;
	@Inject @Named("facebook_accessToken") String accessToken;
	private HttpClient httpClient;
	private ActorSystem actorSystem;
	
	@PostConstruct public void init() {
		actorSystem = ActorSystem.create(FbCli.class.getSimpleName());
		// this works: 
		httpClient = new ContentEncodingHttpClient(new PoolingClientConnectionManager(), new BasicHttpParams());
		// this doesn't work:
//		 HttpClient httpClient = new DecompressingHttpClient(new DefaultHttpClient(new PoolingClientConnectionManager(), new BasicHttpParams()));

	}
	
	@PreDestroy public void destroy() {
		if (httpClient != null)
			httpClient.getConnectionManager().shutdown();
		if (actorSystem != null)
		actorSystem.shutdown();
	}
	
	public Future<JsonNode> fetchFriendsPage(final URI uri) {
		return Futures.future(new Callable<JsonNode>() {
			@Override
			public JsonNode call() throws Exception {
				log.info("Fetching friends page {}", uri);
				HttpGet getReq = new HttpGet(uri);
				HttpResponse friendsResp = httpClient.execute(getReq);
				
				ObjectMapper mapper = new ObjectMapper();
				mapper.enable(SerializationFeature.INDENT_OUTPUT);
				JsonNode json = mapper.readTree(friendsResp.getEntity().getContent());
				return json;
			}
		}, actorSystem.dispatcher());
	}
	
	public Future<List<JsonNode>> fetchFriendsPages(final URI uri) {
		return Futures.future(new Callable<List<JsonNode>>() {
			@Override
			public List<JsonNode> call() throws Exception {
				ArrayList<JsonNode> pages = Lists.newArrayList();
				URI currentUri = uri;
				ObjectMapper mapper = new ObjectMapper();
				mapper.enable(SerializationFeature.INDENT_OUTPUT);
				while (currentUri != null) {
					Future<JsonNode> page = fetchFriendsPage(currentUri);
					currentUri = null;
					JsonNode json = Await.result(page, Duration.Inf());
					pages.add(json);
					
//					mapper.writeValue(System.out, json);
					
					if (json.has("paging")) {
						JsonNode pagingNode = json.get("paging");
						if (pagingNode.has("next")) {
							currentUri = URI.create(pagingNode.get("next").asText());
						}
					}
					
					if (currentUri == null) {
						log.info("It was the last page.");
					}
				}
				return pages;
			}
		}, actorSystem.dispatcher());
	}
	
	public void run(@Observes ContainerInitialized e) {
		log.info("fbcli starting");
		if (args.length < 1)
			throw new RuntimeException("Requires command line arguments.");
		
		if ("friends".equals(args[0])) {
			try {
				URI friendsUri = new URIBuilder("https://graph.facebook.com/me/friends")
					.addParameter("access_token", accessToken)
					.addParameter("limit", "100")
					.build();
				Future<List<JsonNode>> pagesFuture = fetchFriendsPages(friendsUri);
				List<JsonNode> pages = Await.result(pagesFuture, Duration.Inf());
				
				ObjectMapper mapper = new ObjectMapper();
				mapper.enable(SerializationFeature.INDENT_OUTPUT);
				for (int i = 0; i < pages.size(); i++) {
					File file = new File("output/friends-" + (i+1) + ".js");
					log.info("Saving page #{} to {}", i+1, file);
					mapper.writeValue(new FileWriter(file), pages.get(i));
				}
				log.info("Saved {} pages", pages.size());
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}
}
