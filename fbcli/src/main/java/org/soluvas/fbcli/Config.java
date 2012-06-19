package org.soluvas.fbcli;

import java.io.IOException;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;

import akka.actor.ActorSystem;

/**
 * @author ceefour
 *
 */
@ApplicationScoped
public class Config {

	@Produces @Named("facebook_accessToken") private String facebookAccessToken;
	@Produces private HttpClient httpClient;
	@Produces private ActorSystem actorSystem;
	
	@PostConstruct public void init() throws IOException {
		Properties props = new Properties();
		props.load(getClass().getResourceAsStream("/fbcli.properties"));
		facebookAccessToken = props.getProperty("facebook.accessToken");
		
		actorSystem = ActorSystem.create(Config.class.getSimpleName());
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
}
