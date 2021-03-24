package conflux.dex.model;

import javax.validation.constraints.Size;

import conflux.dex.common.Utils;
import conflux.dex.controller.AddressTool;

public class User implements Comparable<User> {
	public static final int MAX_NAME_LEN = 64;
	
	/**
	 * User id. (auto-generated)
	 */
	private long id;
	/**
	 * User name.
	 */
	@Size(min = 1, max = 64)
	private String name;
	private String base32address;
	
	public User() {}
	
	public User(String name) {
		this.name = name;
	}

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
		this.base32address = AddressTool.toBase32(name);
	}

	public String getBase32address() {
		return base32address;
	}

	public void setBase32address(String base32address) {
		this.base32address = base32address;
	}

	@Override
	public int compareTo(User o) {
		return this.name.compareToIgnoreCase(o.name);
	}
	
	@Override
	public String toString() {
		return Utils.toJson(this);
	}
}
