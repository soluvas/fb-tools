package org.soluvas.fbcli;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.client.utils.URIBuilder;
import org.jboss.weld.environment.se.bindings.Parameters;
import org.jboss.weld.environment.se.events.ContainerInitialized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.soluvas.slug.SlugUtils;

import akka.actor.ActorSystem;
import akka.dispatch.Await;
import akka.dispatch.Future;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;
import akka.util.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * @author ceefour
 */
public class FbCli {
	private transient Logger log = LoggerFactory.getLogger(FbCli.class);
	@Inject @Parameters String[] args;
	@Inject @Named("facebook_accessToken")
	String accessToken;
	@Inject 
	ActorSystem actorSystem;
	private ObjectMapper mapper;
	
	@Inject 
	FriendListDownloader friendListDownloader;
	@Inject 
	FbGetUser getUserCmd;
	@Inject 
	UserListParser userListParser;
	@Inject 
	VcardConverter vcardConverter;
	@Inject 
	PhotoDownloader photoDownloader;
	
	@PostConstruct public void init() {
		mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
	}
	
	public void run(@Observes ContainerInitialized e) {
		log.info("fbcli starting");
		if (args.length < 1)
			throw new RuntimeException("Requires command line arguments.");
		
		try {
			if ("friends".equals(args[0])) {
				URI friendsUri = new URIBuilder("https://graph.facebook.com/me/friends")
					.addParameter("access_token", accessToken)
					.addParameter("limit", "100")
					.build();
				Future<List<JsonNode>> pagesFuture = friendListDownloader.fetchFriendsPages(friendsUri);
				List<JsonNode> pages = Await.result(pagesFuture, Duration.Inf());
				
				ObjectMapper mapper = new ObjectMapper();
				mapper.enable(SerializationFeature.INDENT_OUTPUT);
				for (int i = 0; i < pages.size(); i++) {
					File file = new File("output/friends-" + (i+1) + ".js");
					log.info("Saving page #{} to {}", i+1, file);
					mapper.writeValue(new FileWriter(file), pages.get(i));
				}
				log.info("Saved {} pages", pages.size());
			} else if ("user-get".equals(args[0])) {
				// Get single user and print to console 
				JsonNode jsonNode = Await.result( getUserCmd.getUser(args[1]), Duration.Inf() );
				ObjectMapper mapper = new ObjectMapper();
				mapper.enable(SerializationFeature.INDENT_OUTPUT);
				mapper.writeValue(System.out, jsonNode);
			} else if ("userlist-parse".equals(args[0])) {
				// Parse user list from JSON files 
				String[] fileNames = Arrays.copyOfRange(args, 1, args.length);
				List<File> files = Lists.transform(Arrays.asList(fileNames), new Function<String, File>() {
					@Override
					public File apply(String input) {
						return new File(input);
					}
				});
				List<UserRef> userList = Await.result(userListParser.parse(files), Duration.Inf());
				log.info("Parsed {} users", userList.size());
			} else if ("user-getmanyfromfiles".equals(args[0])) {
				// Get many user, list of user IDs parsed from JSON files 
				// Parse user list from JSON files 
				String[] fileNames = Arrays.copyOfRange(args, 1, args.length);
				List<File> files = Lists.transform(Arrays.asList(fileNames), new Function<String, File>() {
					@Override
					public File apply(String input) {
						return new File(input);
					}
				});
				Future<List<UserRef>> userListFuture = userListParser.parse(files);
				List<UserRef> userList = Await.result(userListFuture, Duration.Inf());
				log.info("Parsed {} users", userList.size());
				
				// Get each user by ID and save to file
				Future<Iterable<File>> filesFuture = Futures.traverse(userList, new akka.japi.Function<UserRef, Future<File>>() {
					@Override
					public Future<File> apply(UserRef userRef) {
						final File file = new File("output", "facebook_" + userRef.getId() + "_" +
									SlugUtils.generateId(userRef.getName(), 0) + ".js");
						if (file.exists()) {
							log.warn("{} exists, skipping", file);
							return Futures.successful(file, actorSystem.dispatcher());
						}
						return getUserCmd.getUser(userRef.getId()).map(new Mapper<JsonNode, File>() {
							@Override
							public File apply(JsonNode node) {
								try {
									mapper.writeValue(file, node);
									return file;
								} catch (Exception e) {
									throw new RuntimeException("Cannot generate JSON to " + file, e);
								}
							}
						});
					}
				}, actorSystem.dispatcher());
				Iterable<File> outFiles = Await.result(filesFuture, Duration.Inf());
				log.info("Written {} user JSON files", Iterables.size(outFiles));
			} else if ("userjson-tovcard".equals(args[0])) {
				// Convert a user JSON files to vCard
				String[] fileNames = Arrays.copyOfRange(args, 1, args.length);
				List<File> files = Lists.transform(Arrays.asList(fileNames), new Function<String, File>() {
					@Override
					public File apply(String input) {
						return new File(input);
					}
				});
				Future<Iterable<File>> vcardsFuture = Futures.traverse(files, new akka.japi.Function<File, Future<File>>() {
					@Override
					public Future<File> apply(File jsonFile) {
						File vcardFile = new File(jsonFile.getParentFile(), "vcard/" + FilenameUtils.getBaseName(jsonFile.getName()) + ".vcf");
						return vcardConverter.toVcard(jsonFile, vcardFile);
					}
				}, actorSystem.dispatcher());
				Iterable<File> vcards = Await.result(vcardsFuture, Duration.Inf());
				log.info("Saved {} vCard files", Iterables.size(vcards));
			} else if ("userphoto-uri".equals(args[0])) {
				// Convert a user JSON files to vCard
				String[] paths = Arrays.copyOfRange(args, 1, args.length);
				Future<Iterable<String>> photoUrisFuture = Futures.traverse(Arrays.asList(paths), new akka.japi.Function<String, Future<String>>() {
					@Override
					public Future<String> apply(String path) {
						return photoDownloader.getNormalPictureUri(path);
					}
				}, actorSystem.dispatcher());
				Iterable<String> photoUris = Await.result(photoUrisFuture, Duration.Inf());
				log.info("Photo URIs: {}", photoUris);
			}
		} catch (Exception ex) {
			log.error("Error executing command", ex);
			throw new RuntimeException("Error executing command", ex);
		} finally {
			actorSystem.shutdown();
		}
	}
}
