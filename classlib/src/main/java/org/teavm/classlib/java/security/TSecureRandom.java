package org.teavm.classlib.java.security;

public class TSecureRandom {
	private static int nextInt = 0;

	public int nextInt(int bound) {
		nextInt++;
		return nextInt;
	}
}
