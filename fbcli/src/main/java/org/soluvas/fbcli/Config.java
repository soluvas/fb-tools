package org.soluvas.fbcli;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.inject.Singleton;

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
	
	@PostConstruct public void init() throws IOException {
		Properties props = new Properties();
		props.load(new FileReader("fbcli.properties"));
		facebookAccessToken = props.getProperty("facebook.accessToken");
		
		// this works: 
		httpClient = new ContentEncodingHttpClient(new PoolingClientConnectionManager(), new BasicHttpParams());
		// this doesn't work:
//		 HttpClient httpClient = new DecompressingHttpClient(new DefaultHttpClient(new PoolingClientConnectionManager(), new BasicHttpParams()));
	}
	
	@Produces @Singleton ActorSystem createActorSystem() {
		return ActorSystem.create("fbcli");
	}
	
	public void destroyActorSystem(@Disposes @Singleton ActorSystem actorSystem) {
		actorSystem = null;
		actorSystem.shutdown();
	}
	
	@PreDestroy public void destroy() {
		if (httpClient != null)
			httpClient.getConnectionManager().shutdown();
	}
}
