package org.soluvas.fbcli;

import java.io.File;
import java.io.FileWriter;
import java.net.URI;
import java.util.List;

import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.http.client.utils.URIBuilder;
import org.jboss.weld.environment.se.bindings.Parameters;
import org.jboss.weld.environment.se.events.ContainerInitialized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.dispatch.Await;
import akka.dispatch.Future;
import akka.util.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * @author ceefour
 */
public class FbCli {
	private transient Logger log = LoggerFactory.getLogger(FbCli.class);
	@Inject @Parameters String[] args;
	@Inject @Named("facebook_accessToken") String accessToken;
	
	@Inject FriendListDownloader friendListDownloader;
	@Inject FbGetUser getUserCmd;
	@Inject UserListParser userListParser;
	
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
				List<UserRef> userList = Await.result( userListParser.parse(new File("output/friends-1.js")), Duration.Inf() );
				log.info("users: {}", userList);
			} else if ("user-getmany".equals(args[0])) {
				// Get many user 
			}
		} catch (Exception ex) {
			log.error("Error executing command", ex);
			throw new RuntimeException("Error executing command", ex);
		}
	}
}
