package org.teavm.classlib.java.util;


public class TUUID {
	private static long counter = 0;
	private String id;

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public TUUID randomUUID() {
		TUUID uid = new TUUID();
		uid.setId(counter + "");
		counter++;
		return uid; 
	}
	
	@Override
	public java.lang.String toString() {
		return id;
	}
}
