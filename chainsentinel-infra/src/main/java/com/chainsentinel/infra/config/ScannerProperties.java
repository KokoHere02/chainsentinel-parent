package com.chainsentinel.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "chainsentinel.scanner")
public class ScannerProperties {

    private boolean enabled = true;
    private String chain = "ETH";
    private String network = "sepolia";
    private String rpcUrl;
    private int windowSize = 200;
    private int confirmRequired = 12;
    private long initialStartBlock = 0;
  private int rpcRetryMax = 3;
  private long rpcRetryBackoffMs = 300;
  private boolean fullEthScan = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getChain() {
        return chain;
    }

    public void setChain(String chain) {
        this.chain = chain;
    }

    public String getNetwork() {
        return network;
    }

    public void setNetwork(String network) {
        this.network = network;
    }

    public String getRpcUrl() {
        return rpcUrl;
    }

    public void setRpcUrl(String rpcUrl) {
        this.rpcUrl = rpcUrl;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public int getConfirmRequired() {
        return confirmRequired;
    }

    public void setConfirmRequired(int confirmRequired) {
        this.confirmRequired = confirmRequired;
    }

    public long getInitialStartBlock() {
        return initialStartBlock;
    }

    public void setInitialStartBlock(long initialStartBlock) {
        this.initialStartBlock = initialStartBlock;
    }

  public int getRpcRetryMax() {
    return rpcRetryMax;
  }

  public void setRpcRetryMax(int rpcRetryMax) {
    this.rpcRetryMax = rpcRetryMax;
  }

  public long getRpcRetryBackoffMs() {
    return rpcRetryBackoffMs;
  }

  public void setRpcRetryBackoffMs(long rpcRetryBackoffMs) {
    this.rpcRetryBackoffMs = rpcRetryBackoffMs;
  }

  public boolean isFullEthScan() {
    return fullEthScan;
  }

  public void setFullEthScan(boolean fullEthScan) {
    this.fullEthScan = fullEthScan;
  }
}
