package com.chainsentinel.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
* Symmetric crypto utility for sensitive data storage.
*/
public final class AesGcmCryptoUtil {

	private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
	private static final String KEY_ALGORITHM = "AES";
	private static final String VERSION_PREFIX = "v1:";
	private static final int IV_LENGTH = 12;
	private static final int TAG_LENGTH_BITS = 128;
	private static final Base64.Decoder BASE64_DECODER = Base64.getDecoder();
	private static final Base64.Encoder BASE64_ENCODER = Base64.getEncoder();

	private final SecretKeySpec keySpec;
	private final SecureRandom secureRandom;

	private AesGcmCryptoUtil(byte[] rawKey) {
		validateAesKeyLength(rawKey.length);
		this.keySpec = new SecretKeySpec(rawKey, KEY_ALGORITHM);
		this.secureRandom = new SecureRandom();
	}

	public static AesGcmCryptoUtil fromBase64Key(String base64Key) {
		if (base64Key == null || base64Key.isBlank()) {
			throw new IllegalArgumentException("base64Key must not be blank");
		}
		try {
			return new AesGcmCryptoUtil(BASE64_DECODER.decode(base64Key));
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("base64Key is not valid base64", ex);
		}
	}

	public String encrypt(String plaintext) {
		return encrypt(plaintext, null);
	}

	public String encrypt(String plaintext, String aad) {
		if (plaintext == null) {
			throw new IllegalArgumentException("plaintext must not be null");
		}
		try {
			byte[] iv = new byte[IV_LENGTH];
			secureRandom.nextBytes(iv);

			Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			if (aad != null && !aad.isEmpty()) {
				cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
			}
			byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

			byte[] payload = new byte[IV_LENGTH + cipherBytes.length];
			System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
			System.arraycopy(cipherBytes, 0, payload, IV_LENGTH, cipherBytes.length);

			return VERSION_PREFIX + BASE64_ENCODER.encodeToString(payload);
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("encrypt failed", ex);
		}
	}

	public String decrypt(String cipherText) {
		return decrypt(cipherText, null);
	}

	public String decrypt(String cipherText, String aad) {
		if (cipherText == null || cipherText.isBlank()) {
			throw new IllegalArgumentException("cipherText must not be blank");
		}
		if (!cipherText.startsWith(VERSION_PREFIX)) {
			throw new IllegalArgumentException("unsupported cipherText version");
		}
		String encoded = cipherText.substring(VERSION_PREFIX.length());
		try {
			byte[] payload = BASE64_DECODER.decode(encoded);
			if (payload.length <= IV_LENGTH) {
				throw new IllegalArgumentException("cipherText payload too short");
			}
			byte[] iv = new byte[IV_LENGTH];
			byte[] encrypted = new byte[payload.length - IV_LENGTH];
			System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
			System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);

			Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
			if (aad != null && !aad.isEmpty()) {
				cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
			}
			byte[] plainBytes = cipher.doFinal(encrypted);
			return new String(plainBytes, StandardCharsets.UTF_8);
		} catch (IllegalArgumentException ex) {
			throw ex;
		} catch (GeneralSecurityException ex) {
			throw new IllegalStateException("decrypt failed", ex);
		}
	}

	private static void validateAesKeyLength(int keyLengthBytes) {
		if (keyLengthBytes != 16 && keyLengthBytes != 24 && keyLengthBytes != 32) {
			throw new IllegalArgumentException("AES key length must be 16, 24, or 32 bytes");
		}
	}
}
