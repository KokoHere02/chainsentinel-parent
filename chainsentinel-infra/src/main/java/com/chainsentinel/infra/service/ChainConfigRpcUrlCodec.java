package com.chainsentinel.infra.service;

import com.chainsentinel.common.crypto.AesGcmCryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ChainConfigRpcUrlCodec {

private static final Logger log = LoggerFactory.getLogger(ChainConfigRpcUrlCodec.class);
private static final String ENCRYPTION_PREFIX = "v1:";

private final AesGcmCryptoUtil aesGcmCryptoUtil;

public ChainConfigRpcUrlCodec(AesGcmCryptoUtil aesGcmCryptoUtil) {
this.aesGcmCryptoUtil = aesGcmCryptoUtil;
}

public String encrypt(String rpcUrl) {
return aesGcmCryptoUtil.encrypt(rpcUrl);
}

public String decryptIfNeeded(String value, String chain, String network) {
if (!StringUtils.hasText(value)) {
return value;
}
if (!value.startsWith(ENCRYPTION_PREFIX)) {
return value;
}
try {
return aesGcmCryptoUtil.decrypt(value);
} catch (RuntimeException ex) {
log.error("Failed to decrypt rpcUrl for {}-{}", chain, network, ex);
return null;
}
}
}