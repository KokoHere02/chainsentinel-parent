package com.chainsentinel.common.crypto.config;

import com.chainsentinel.common.crypto.AesGcmCryptoUtil;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoConfiguration {

    @Bean
    public AesGcmCryptoUtil aesGcmCryptoUtil(CryptoProperties properties) {
        if (!StringUtils.hasText(properties.getKeyBase64())) {
            throw new IllegalStateException("chainsentinel.security.crypto.key-base64 must be configured");
        }
        return AesGcmCryptoUtil.fromBase64Key(properties.getKeyBase64());
    }
}