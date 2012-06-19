package org.soluvas.fbcli;

import java.io.Serializable;

/**
 * @author ceefour
 *
 */
@SuppressWarnings("serial")
public class UserRef implements Serializable {

	private Long id;
	private String name;
	
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	@Override
	public String toString() {
		return String.format("UserRef [id=%s, name=%s]", id, name);
	}
	
}
