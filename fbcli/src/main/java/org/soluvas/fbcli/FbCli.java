package org.soluvas.fbcli;

import java.net.URI;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.jboss.weld.environment.se.bindings.Parameters;
import org.jboss.weld.environment.se.events.ContainerInitialized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;

/**
 * @author ceefour
 */
public class FbCli {
	private transient Logger log = LoggerFactory.getLogger(FbCli.class);
	@Inject @Parameters String[] args;
	@Inject @Named("facebook_accessToken") String accessToken; 
	
	public void run(@Observes ContainerInitialized e) {
		log.info("fbcli starting");
		if (args.length < 1)
			throw new RuntimeException("Requires command line arguments.");
		
		if ("friends".equals(args[0])) {
			try {
				// this works: 
				HttpClient httpClient = new ContentEncodingHttpClient();
				try {
					// this doesn't work:
					// HttpClient httpClient = new DecompressingHttpClient(new DefaultHttpClient());
					URI friendsUri = new URIBuilder("https://graph.facebook.com/me/friends").addParameter("access_token", accessToken).build();
					HttpGet getReq = new HttpGet(friendsUri);
					HttpResponse friendsResp = httpClient.execute(getReq);
					
					JsonFactory jsonFactory = new JsonFactory();
					JsonParser jsonParser = jsonFactory.createJsonParser(friendsResp.getEntity().getContent());
					JsonGenerator jsonGenerator = jsonFactory.createJsonGenerator(System.out).useDefaultPrettyPrinter();
					while (jsonParser.nextToken() != null)
						jsonGenerator.copyCurrentStructure(jsonParser);
				} finally {
					httpClient.getConnectionManager().shutdown();
				}
			} catch (Exception ex) {
				throw new RuntimeException(ex);
			}
		}
	}
}
