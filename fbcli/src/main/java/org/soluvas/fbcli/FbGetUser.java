package org.soluvas.fbcli;

import java.net.URI;
import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import akka.dispatch.Future;
import akka.dispatch.Futures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Get a single Facebook user as {@link JsonNode}.
 * @author ceefour
 */
public class FbGetUser {

	private transient Logger log = LoggerFactory.getLogger(FbGetUser.class);

	private ObjectMapper mapper;
	@Inject private ActorSystem actorSystem;
	@Inject private HttpClient httpClient;
	@Inject @Named("facebook_accessToken") String accessToken;
	
	public FbGetUser() {
		mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
	}
	
	/**
	 * Return Facebook user's {@link JsonNode} based on path (e.g. <tt>ceefour</tt> or ID).
	 * @param fbUsername
	 * @return
	 */
	public Future<JsonNode> getUserByPath(final String path) {
		return Futures.future(new Callable<JsonNode>() {
			@Override
			public JsonNode call() throws Exception {
				URI uri = new URIBuilder("https://graph.facebook.com/" + path)
					.addParameter("access_token", accessToken).build();
				log.info("Fetching user page {}", uri);
				HttpGet getReq = new HttpGet(uri);
				HttpResponse resp = httpClient.execute(getReq);
				JsonNode json = mapper.readTree(resp.getEntity().getContent());
				return json;
			}
		}, actorSystem.dispatcher());
	}
	
	/**
	 * Return Facebook user's {@link JsonNode} based on Facebook username.
	 * @param fbUsername
	 * @return
	 */
	public Future<JsonNode> getUser(String fbUsername) {
		return getUserByPath(fbUsername);
	}

	/**
	 * Return Facebook user's {@link JsonNode} based on Facebook numeric ID.
	 * @param fbId
	 * @return
	 */
	public Future<JsonNode> getUser(Long fbId) {
		return getUserByPath(String.valueOf(fbId));
	}
	
}
