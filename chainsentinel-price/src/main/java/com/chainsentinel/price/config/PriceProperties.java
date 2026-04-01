package com.chainsentinel.price.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.price")
public class PriceProperties {

    private long cacheTtlMs = 5000L;
    private Okx okx = new Okx();
    private Map<String, Integer> providerPriority = new LinkedHashMap<>();

    public long getCacheTtlMs() {
        return cacheTtlMs;
    }

    public void setCacheTtlMs(long cacheTtlMs) {
        this.cacheTtlMs = cacheTtlMs;
    }

    public Okx getOkx() {
        return okx;
    }

    public void setOkx(Okx okx) {
        this.okx = okx;
    }

    public Map<String, Integer> getProviderPriority() {
        return providerPriority;
    }

    public void setProviderPriority(Map<String, Integer> providerPriority) {
        this.providerPriority = providerPriority;
    }

    public static class Okx {
        private boolean enabled = true;
        private String baseUrl = "https://www.okx.com";
        private int timeoutMs = 1500;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }
}
