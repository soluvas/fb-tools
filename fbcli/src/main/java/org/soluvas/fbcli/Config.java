package org.soluvas.fbcli;

import java.io.IOException;
import java.util.Properties;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Produces;
import javax.inject.Named;

/**
 * @author ceefour
 *
 */
public class Config {

	@Produces @Named("facebook_accessToken") private String facebookAccessToken;
	
	@PostConstruct public void init() throws IOException {
		Properties props = new Properties();
		props.load(getClass().getResourceAsStream("/fbcli.properties"));
		facebookAccessToken = props.getProperty("facebook.accessToken");
	}
	
}
