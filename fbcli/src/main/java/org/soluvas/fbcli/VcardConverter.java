package org.soluvas.fbcli;

import java.io.File;
import java.net.URL;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.inject.Inject;

import net.sourceforge.cardme.io.VCardWriter;
import net.sourceforge.cardme.vcard.EncodingType;
import net.sourceforge.cardme.vcard.VCardImpl;
import net.sourceforge.cardme.vcard.VCardVersion;
import net.sourceforge.cardme.vcard.types.AddressType;
import net.sourceforge.cardme.vcard.types.BeginType;
import net.sourceforge.cardme.vcard.types.BirthdayType;
import net.sourceforge.cardme.vcard.types.EndType;
import net.sourceforge.cardme.vcard.types.ExtendedType;
import net.sourceforge.cardme.vcard.types.FormattedNameType;
import net.sourceforge.cardme.vcard.types.NameType;
import net.sourceforge.cardme.vcard.types.NicknameType;
import net.sourceforge.cardme.vcard.types.NoteType;
import net.sourceforge.cardme.vcard.types.PhotoType;
import net.sourceforge.cardme.vcard.types.UIDType;
import net.sourceforge.cardme.vcard.types.URLType;
import net.sourceforge.cardme.vcard.types.parameters.AddressParameterType;
import net.sourceforge.cardme.vcard.types.parameters.PhotoParameterType;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.soluvas.slug.SlugUtils;

import akka.actor.ActorSystem;
import akka.dispatch.Future;
import akka.dispatch.Futures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicate;

/**
 * Convert Facebook JSON to vCard.
 * @author ceefour
 */
public class VcardConverter {
	
	private transient Logger log = LoggerFactory.getLogger(VcardConverter.class);
	private ObjectMapper mapper = new ObjectMapper();
	@Inject private ActorSystem actorSystem;
	
	private Set<String> personIds  = new ConcurrentSkipListSet<String>();
	private Set<String> screenNames = new ConcurrentSkipListSet<String>();
	
	public Future<File> toVcard(final File inputJson, final File outputVcard,
			final File photoFile) {
		return Futures.future(new Callable<File>() {
			@Override
			public File call() throws Exception {
				JsonNode json = mapper.readTree(inputJson);
				
				// Generate ID and screenName
				final String name = json.get("name").asText();
				final String personId = SlugUtils.generateValidId(name, new Predicate<String>() {
					@Override
					public boolean apply(@Nullable String id) {
						return !personIds.contains(id);
					}
				});
				final String screenName = SlugUtils.generateValidScreenName(name, new Predicate<String>() {
					@Override
					public boolean apply(@Nullable String screenName) {
						return !screenNames.contains(screenName);
					}
				});
				
				// Put the ID and screenName to Set so it won't get reused
				personIds.add(personId);
				screenNames.add(screenName);
				
				VCardImpl vcard = new VCardImpl();
				vcard.setBegin(new BeginType());
				vcard.setFormattedName(new FormattedNameType(name));
				String firstAndMiddleName = json.get("first_name").asText() + ( json.has("middle_name") ? " " + json.get("middle_name").asText() : "" );
				vcard.setName(new NameType(json.get("last_name").asText(), firstAndMiddleName));
				
				vcard.setUID(new UIDType(personId));
				vcard.addExtendedType(new ExtendedType("X-SCREENNAME", screenName));
				
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
					final String birthday = json.get("birthday").asText();
					try {
						DateTime birthDate = DateTime.parse(birthday,
								new DateTimeFormatterBuilder().appendMonthOfYear(2).appendLiteral('/')
								.appendDayOfMonth(2).appendLiteral('/').appendYear(4, 4).toFormatter());
						vcard.setBirthday(new BirthdayType(birthDate.toGregorianCalendar()));
					} catch (Exception e) {
						Matcher partialMatcher = Pattern.compile("(\\d+)\\/(\\d+)").matcher(birthday);
						if (partialMatcher.matches()) {
							GregorianCalendar calendar = new GregorianCalendar();
							calendar.clear();
							calendar.set(Calendar.MONTH, Integer.valueOf(partialMatcher.group(1)) - 1);
							calendar.set(Calendar.DAY_OF_MONTH, Integer.valueOf(partialMatcher.group(2)));
							calendar.set(Calendar.YEAR, 1);
							vcard.setBirthday(new BirthdayType(calendar));
						} else {
							log.warn("Invalid birthdate format for {}: {} - {}", new Object[] { vcard.getFormattedName().getFormattedName(), birthday, e });
						}
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
