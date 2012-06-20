package org.soluvas.fbcli;

import info.ineighborhood.cardme.io.VCardWriter;
import info.ineighborhood.cardme.vcard.EncodingType;
import info.ineighborhood.cardme.vcard.VCardImpl;
import info.ineighborhood.cardme.vcard.VCardVersion;
import info.ineighborhood.cardme.vcard.types.AddressType;
import info.ineighborhood.cardme.vcard.types.BeginType;
import info.ineighborhood.cardme.vcard.types.BirthdayType;
import info.ineighborhood.cardme.vcard.types.EndType;
import info.ineighborhood.cardme.vcard.types.ExtendedType;
import info.ineighborhood.cardme.vcard.types.FormattedNameType;
import info.ineighborhood.cardme.vcard.types.NameType;
import info.ineighborhood.cardme.vcard.types.NicknameType;
import info.ineighborhood.cardme.vcard.types.NoteType;
import info.ineighborhood.cardme.vcard.types.PhotoType;
import info.ineighborhood.cardme.vcard.types.URLType;
import info.ineighborhood.cardme.vcard.types.parameters.AddressParameterType;
import info.ineighborhood.cardme.vcard.types.parameters.ParameterTypeStyle;
import info.ineighborhood.cardme.vcard.types.parameters.PhotoParameterType;

import java.io.File;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	DateFormat fbDateFormat = new SimpleDateFormat("mm/dd/yyyy");
	
	public Future<File> toVcard(final File inputJson, final File outputVcard,
			final File photoFile) {
		return Futures.future(new Callable<File>() {
			@Override
			public File call() throws Exception {
				JsonNode json = mapper.readTree(inputJson);
				VCardImpl vcard = new VCardImpl();
				vcard.setBegin(new BeginType());
				vcard.setFormattedName(new FormattedNameType(json.get("name").asText()));
				String firstAndMiddleName = json.get("first_name").asText() + ( json.has("middle_name") ? " " + json.get("middle_name").asText() : "" );
				vcard.setName(new NameType(json.get("last_name").asText(), firstAndMiddleName));
				vcard.addExtendedType(new ExtendedType("X-FACEBOOK-ID", json.get("id").asText()));
				if (json.has("username")) {
					NicknameType nickname = new NicknameType();
					nickname.addNickname(json.get("username").asText());
					vcard.setNicknames(nickname);
					vcard.addExtendedType(new ExtendedType("X-FACEBOOK-USERNAME", json.get("username").asText()));
				}
				if (json.has("gender"))
						vcard.addExtendedType(new ExtendedType("X-GENDER", StringUtils.capitalize(json.get("gender").asText())));
				if (json.has("birthday")) {
					try {
						Date birthDate = fbDateFormat.parse( json.get("birthday").asText() );
						vcard.setBirthday(new BirthdayType(birthDate));
					} catch (Exception e) {
						log.warn("Invalid birthdate format for {}: {}", vcard.getFormattedName().getFormattedName(), json.get("birthday"));
					}
				}
				if (json.has("bio")) {
					// WARNING: Some characters are unreadable by Thunderbird
					vcard.addNote(new NoteType(json.get("bio").asText()));
				}
				if (json.has("location")) {
					AddressType address = new AddressType();
					address.addAddressParameterType(AddressParameterType.INTL);
					address.addAddressParameterType(AddressParameterType.POSTAL);
					address.addAddressParameterType(AddressParameterType.PARCEL);
					address.addAddressParameterType(AddressParameterType.WORK);
					address.addAddressParameterType(AddressParameterType.PREF);
					final String location = json.get("location").get("name").asText();
					Matcher locationMatcher = Pattern.compile("(.*), (.+)").matcher(location);
					if (locationMatcher.matches()) {
						address.setLocality(locationMatcher.group(1));
						address.setCountryName(locationMatcher.group(2));
					} else {
						address.setLocality(location);
					}
					vcard.addAddress(address);
				}
				if (json.has("hometown")) {
					AddressType address = new AddressType();
					address.addAddressParameterType(AddressParameterType.INTL);
					address.addAddressParameterType(AddressParameterType.POSTAL);
					address.addAddressParameterType(AddressParameterType.PARCEL);
					address.addAddressParameterType(AddressParameterType.HOME);
					final String hometown = json.get("hometown").get("name").asText();
					Matcher locationMatcher = Pattern.compile("(.*), (.+)").matcher(hometown);
					if (locationMatcher.matches()) {
						address.setLocality(locationMatcher.group(1));
						address.setCountryName(locationMatcher.group(2));
					} else {
						address.setLocality(hometown);
					}
					vcard.addAddress(address);
				}
				if (json.has("link")) {
					final String linkUri = json.get("link").asText();
					vcard.addURL(new URLType(new URL(linkUri)));
					//vcard.addPhoto(new PhotoType(URI.create(linkUri + "/picture"), EncodingType.SEVEN_BIT, PhotoParameterType.VALUE));
				}
				if (photoFile != null) {
					// WARNING: Thunderbird cannot read vCard 3.0 with base64-encoded photo :(
					log.info("Reading photo {} for {} {}", new Object[] {
							photoFile, vcard.getFormattedName().getFormattedName() });
					byte[] photoData = FileUtils.readFileToByteArray(photoFile);
					vcard.addPhoto(new PhotoType(photoData, EncodingType.BASE64, PhotoParameterType.ENCODING));
				}
				vcard.setEnd(new EndType());
				
				VCardWriter vCardWriter = new VCardWriter(VCardVersion.V3_0);
				vCardWriter.setVCard(vcard);
				log.info("Writing vCard for {} to {}", new Object[] { vcard.getFormattedName().getFormattedName(),
						outputVcard });
				FileUtils.write(outputVcard, vCardWriter.buildVCardString());
				// You can't have email :(
//				List<Property> props = Lists.newArrayList();
//				props.add(new Fn(json.get("first_name").asText()));
//				props.add(new Fn(json.get("last_name").asText()));
//				props.add(new Name(json.get("name").asText()));
//				VCard vcard = new VCard();
				return outputVcard;
			}
		}, actorSystem.dispatcher());
	}

}
