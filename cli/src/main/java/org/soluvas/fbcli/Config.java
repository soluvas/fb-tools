package org.soluvas.fbcli;

import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.params.BasicHttpParams;

import akka.actor.ActorSystem;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * @author ceefour
 *
 */
@ApplicationScoped
public class Config {

	@Produces @Named("facebook_accessToken") private String facebookAccessToken;
	@Produces private HttpClient httpClient;
	@Produces private ExecutorService executor;
	
	@PostConstruct public void init() throws IOException {
		Properties props = new Properties();
		props.load(new FileReader("fbcli.properties"));
		facebookAccessToken = props.getProperty("facebook.accessToken");
		httpClient = new DecompressingHttpClient(new DefaultHttpClient(new PoolingClientConnectionManager(), new BasicHttpParams()));
		executor = Executors.newFixedThreadPool(8, new ThreadFactoryBuilder().setNameFormat("fbcli-%d").build());
	}
	
	@Produces @Singleton ActorSystem createActorSystem() {
		return ActorSystem.create("fbcli");
	}
	
	public void destroyActorSystem(@Disposes @Singleton ActorSystem actorSystem) {
		actorSystem.shutdown();
		actorSystem.awaitTermination();
	}
	
	@PreDestroy public void destroy() {
		if (httpClient != null)
			httpClient.getConnectionManager().shutdown();
	}
}
