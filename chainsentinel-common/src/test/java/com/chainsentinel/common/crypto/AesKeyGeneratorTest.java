package com.chainsentinel.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesKeyGeneratorTest {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	@Test
	void shouldGenerateValidAesBase64Keys() {
		printKey(16);
		printKey(24);
		printKey(32);
	}

	private void printKey(int sizeBytes) {
		byte[] key = new byte[sizeBytes];
		SECURE_RANDOM.nextBytes(key);
		String base64 = Base64.getEncoder().encodeToString(key);

		System.out.println("AES-" + (sizeBytes * 8) + " key-base64: " + base64);
		assertEquals(sizeBytes, Base64.getDecoder().decode(base64).length);
	}
}
