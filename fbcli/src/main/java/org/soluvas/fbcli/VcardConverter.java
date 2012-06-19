package org.soluvas.fbcli;

import info.ineighborhood.cardme.io.VCardWriter;
import info.ineighborhood.cardme.vcard.VCardImpl;
import info.ineighborhood.cardme.vcard.VCardVersion;
import info.ineighborhood.cardme.vcard.types.AddressType;
import info.ineighborhood.cardme.vcard.types.BeginType;
import info.ineighborhood.cardme.vcard.types.EndType;
import info.ineighborhood.cardme.vcard.types.ExtendedType;
import info.ineighborhood.cardme.vcard.types.FormattedNameType;
import info.ineighborhood.cardme.vcard.types.NameType;
import info.ineighborhood.cardme.vcard.types.NoteType;

import java.io.File;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import akka.dispatch.Future;
import akka.dispatch.Futures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Convert Facebook JSON to vCard.
 * @author ceefour
 */
public class VcardConverter {
	
	private transient Logger log = LoggerFactory.getLogger(VcardConverter.class);
	private ObjectMapper mapper = new ObjectMapper();
	@Inject private ActorSystem actorSystem;
	private VCardWriter vCardWriter = new VCardWriter(VCardVersion.V3_0);
	
	public Future<Boolean> toVcard(final File inputJson, final File outputVcard) {
		return Futures.future(new Callable<Boolean>() {
			@Override
			public Boolean call() throws Exception {
				JsonNode json = mapper.readTree(inputJson);
				VCardImpl vcard = new VCardImpl();
				vcard.setBegin(new BeginType());
				vcard.setFormattedName(new FormattedNameType(json.get("name").asText()));
				vcard.setName(new NameType(json.get("last_name").asText(), json.get("first_name").asText()));
				if (json.has("gender"))
						vcard.addExtendedType(new ExtendedType("X-GENDER", StringUtils.capitalize(json.get("gender").asText())));
				if (json.has("bio"))
						vcard.addNote(new NoteType(json.get("bio").asText()));
				if (json.has("hometown")) {
					AddressType address = new AddressType();
					address.setLocality(json.get("hometown").get("name").asText());
					vcard.addAddress(address);
				}
				vcard.setEnd(new EndType());
				vCardWriter.setVCard(vcard);
				log.info("Writing vCard for {} {} to {}", new Object[] { vcard.getName().getGivenName(), vcard.getName().getGivenName(),
						outputVcard });
				FileUtils.write(outputVcard, vCardWriter.buildVCardString());
				// You can't have email :(
//				List<Property> props = Lists.newArrayList();
//				props.add(new Fn(json.get("first_name").asText()));
//				props.add(new Fn(json.get("last_name").asText()));
//				props.add(new Name(json.get("name").asText()));
//				VCard vcard = new VCard();
				return true;
			}
		}, actorSystem.dispatcher());
	}

}
