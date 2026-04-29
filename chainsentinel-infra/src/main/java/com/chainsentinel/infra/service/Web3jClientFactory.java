package com.chainsentinel.infra.service;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.protocol.websocket.WebSocketService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Web3jClientFactory {

	private static final Logger log = LoggerFactory.getLogger(Web3jClientFactory.class);

	public Web3jClient open(String rpcUrl) {
		String validated = UrlSchemeSupport.requireSupported(rpcUrl, "rpcUrl");
		String scheme = UrlSchemeSupport.schemeOf(validated);
		if ("ws".equals(scheme) || "wss".equals(scheme)) {
			try {
				WebSocketService ws = new WebSocketService(validated, false);
				ws.connect();
				Web3j web3j = Web3j.build(ws);
				return new Web3jClient(web3j, () -> {
					web3j.shutdown();
					try {
						ws.close();
					} catch (Exception ex) {
						log.warn("web3j.ws.close.failed rpcUrl={} error={}", validated, ex.getMessage());
					}
				});
			} catch (Exception ex) {
				throw new IllegalStateException("failed to connect websocket rpc: " + ex.getMessage(), ex);
			}
		}

		Web3j web3j = Web3j.build(new HttpService(validated));
		return new Web3jClient(web3j, web3j::shutdown);
	}

	public record Web3jClient(Web3j web3j, Runnable close) {
	}
}
