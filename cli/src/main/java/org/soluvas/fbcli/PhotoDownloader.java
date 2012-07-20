package org.soluvas.fbcli;

import java.io.File;
import java.net.URI;
import java.util.concurrent.Callable;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.params.BasicHttpParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import akka.dispatch.Future;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;

/**
 * Downloads Facebook user photo.
 * @author ceefour
 */
public class PhotoDownloader {

	private transient Logger log = LoggerFactory.getLogger(PhotoDownloader.class);

	@Inject private ActorSystem actorSystem;
	@Inject private HttpClient httpClient;
	@Inject @Named("facebook_accessToken") String accessToken;
	
	/**
	 * Return Facebook user's (thumbnail) picture URI based on path (e.g. <tt>ceefour</tt> or ID).
	 * @param fbUsername
	 * @return
	 */
	public Future<String> getThumbnailPictureUri(final String path) {
		return Futures.future(new Callable<String>() {
			@Override
			public String call() throws Exception {
				URI uri = URI.create("http://graph.facebook.com/" + path + "/picture");
				log.info("Fetching photo URI {}", uri);
				HttpGet getReq = new HttpGet(uri);
				getReq.setParams(new BasicHttpParams().setBooleanParameter(ClientPNames.HANDLE_REDIRECTS, false));
				HttpResponse resp = httpClient.execute(getReq);
				try {
					if (resp.getStatusLine().getStatusCode() != 302)
						throw new RuntimeException("GET " + uri + " expects 302 but returned " + resp.getStatusLine());
					final Header[] locationHeader = resp.getHeaders("Location");
					if (locationHeader.length < 1)
						throw new RuntimeException("Response do not contain Location header: " + resp);
					return locationHeader[0].getValue();
				} finally {
					HttpClientUtils.closeQuietly(resp);
				}
			}
		}, actorSystem.dispatcher());
	}
	
	/**
	 * Return Facebook user's (normal) picture URI based on path (e.g. <tt>ceefour</tt> or ID).
	 * 
	 * Original picture URI cannot be retrieved.
	 * @param fbUsername
	 * @return
	 */
	public Future<String> getNormalPictureUri(final String path) {
		return getThumbnailPictureUri(path).map(new Mapper<String, String>() {
			@Override
			public String apply(String uri) {
				return uri.replace("_q.", "_n.");
			}
		});
	}
	
	public Future<File> download(final String photoUri, final File outputFile) {
		return Futures.future(new Callable<File>() {
			@Override
			public File call() throws Exception {
				URI uri = URI.create(photoUri);
				log.debug("Downloading photo {} to {}", uri, outputFile);
				HttpGet getReq = new HttpGet(uri);
				HttpResponse resp = httpClient.execute(getReq);
				try {
					if (resp.getStatusLine().getStatusCode() != 200)
						throw new RuntimeException("GET " + uri + " expects 200 but returned " + resp.getStatusLine());
					FileUtils.copyInputStreamToFile(resp.getEntity().getContent(), outputFile);
					log.info("Downloaded photo {} to {}", uri, outputFile);
					return outputFile;
				} finally {
					HttpClientUtils.closeQuietly(resp);
				}
			}
		}, actorSystem.dispatcher());
	}
}