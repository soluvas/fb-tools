package org.soluvas.fbcli;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import akka.dispatch.Future;
import akka.dispatch.Futures;
import akka.dispatch.Mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author ceefour
 *
 */
public class UserListParser {
	
	private transient Logger log = LoggerFactory.getLogger(UserListParser.class);
	@Inject private ActorSystem actorSystem;
	private ObjectMapper mapper = new ObjectMapper();
	
	/**
	 * Parse a list of JSON users.
	 * @param rootNode should contain a 'data' property, which contains an array of objects containing id and name.
	 */
	public Future<List<UserRef>> parse(final JsonNode rootNode) {
		return Futures.future(new Callable<List<UserRef>>() {
			@Override
			public List<UserRef> call() throws Exception {
				List<UserRef> userList = mapper.convertValue(rootNode.get("data"), new TypeReference<List<UserRef>>() { });
				log.debug("Parsed list of {} Facebook users", userList.size());
				return userList;
			}
		}, actorSystem.dispatcher());
	}

	public Future<List<UserRef>> parse(final File file) {
		return Futures.future(new Callable<JsonNode>() {
			@Override
			public JsonNode call() throws Exception {
				JsonNode jsonNode = mapper.readTree(file);
				return jsonNode;
			}
		}, actorSystem.dispatcher()).flatMap(new Mapper<JsonNode, Future<List<UserRef>>>() {
			@Override
			public Future<List<UserRef>> apply(JsonNode jsonNode) {
				return parse(jsonNode);
			}
		});
	}
}
