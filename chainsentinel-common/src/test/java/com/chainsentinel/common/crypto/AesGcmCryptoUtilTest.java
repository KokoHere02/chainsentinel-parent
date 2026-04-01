package com.chainsentinel.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmCryptoUtilTest {

    private static final String BASE64_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Test
    void shouldEncryptAndDecryptSuccessfully() {
        AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
        String cipherText = cryptoUtil.encrypt("secret-value");

        assertEquals("secret-value", cryptoUtil.decrypt(cipherText));
    }

    @Test
    void shouldGenerateDifferentCipherTextForSamePlaintext() {
        AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);

        String first = cryptoUtil.encrypt("same-value");
        String second = cryptoUtil.encrypt("same-value");

        assertNotEquals(first, second);
    }

    @Test
    void shouldFailDecryptWhenAadMismatch() {
        AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);
        String cipherText = cryptoUtil.encrypt("secret-value", "wallet:1");

        assertThrows(IllegalStateException.class, () -> cryptoUtil.decrypt(cipherText, "wallet:2"));
    }

    @Test
    void shouldRejectInvalidCipherTextVersion() {
        AesGcmCryptoUtil cryptoUtil = AesGcmCryptoUtil.fromBase64Key(BASE64_KEY);

        assertThrows(IllegalArgumentException.class, () -> cryptoUtil.decrypt("v2:anything"));
    }
}
