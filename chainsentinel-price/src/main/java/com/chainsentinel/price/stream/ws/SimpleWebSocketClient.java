package com.chainsentinel.price.stream.ws;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleWebSocketClient {

	private static final Logger log = LoggerFactory.getLogger(SimpleWebSocketClient.class);

	private final HttpClient httpClient;
	private final Duration connectTimeout;
	private final AtomicBoolean connected = new AtomicBoolean(false);
	private volatile WebSocket webSocket;

	public SimpleWebSocketClient(Duration connectTimeout) {
		this(connectTimeout, defaultProxySelector());
	}

	public SimpleWebSocketClient(Duration connectTimeout, ProxySelector proxySelector) {
		HttpClient.Builder builder = HttpClient.newBuilder();
		if (proxySelector != null) {
			builder.proxy(proxySelector);
		}
		this.httpClient = builder.build();
		this.connectTimeout = connectTimeout;
	}

	public void connect(String url, WebSocketMessageHandler handler) {
		if (connected.get()) {
			return;
		}
		WebSocket.Builder builder = httpClient.newWebSocketBuilder();
		if (connectTimeout != null) {
			builder.connectTimeout(connectTimeout);
		}
		CompletableFuture<WebSocket> future = builder.buildAsync(URI.create(url), new Listener() {
			@Override
			public void onOpen(WebSocket webSocket) {
				SimpleWebSocketClient.this.webSocket = webSocket;
				connected.set(true);
				handler.onOpen();
				webSocket.request(1);
			}

			@Override
			public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
				try {
					handler.onText(data.toString());
				} catch (Exception ex) {
					log.warn("ws.onText.failed error={}", ex.getMessage());
				}
				webSocket.request(1);
				return null;
			}

			@Override
			public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
				connected.set(false);
				handler.onClose(statusCode, reason);
				return null;
			}

			@Override
			public void onError(WebSocket webSocket, Throwable error) {
				connected.set(false);
				handler.onError(error);
			}
		});

		future.exceptionally(ex -> {
			connected.set(false);
			handler.onError(ex);
			return null;
		});
	}

	public boolean isConnected() {
		return connected.get();
	}

	public void sendText(String text) {
		WebSocket socket = this.webSocket;
		if (socket == null) {
			return;
		}
		socket.sendText(text, true);
	}

	public void close() {
		WebSocket socket = this.webSocket;
		if (socket != null) {
			socket.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
		}
		connected.set(false);
	}

	private static ProxySelector defaultProxySelector() {
		String preferred = System.getProperty("ws.proxy.type");
		if (preferred != null) {
			String type = preferred.trim().toLowerCase(Locale.ROOT);
			if ("http".equals(type)) {
				ProxySelector selector = buildHttpProxySelector();
				if (selector != null) {
					return selector;
				}
				return ProxySelector.getDefault();
			}
			if ("socks".equals(type)) {
				ProxySelector selector = buildSocksProxySelector();
				if (selector != null) {
					return selector;
				}
				return ProxySelector.getDefault();
			}
		}
		ProxySelector selector = buildSocksProxySelector();
		if (selector != null) {
			return selector;
		}
		selector = buildHttpProxySelector();
		if (selector != null) {
			return selector;
		}
		return ProxySelector.getDefault();
	}

	private static ProxySelector buildSocksProxySelector() {
		String host = firstNonBlank(System.getProperty("ws.proxy.host"), System.getProperty("socksProxyHost"));
		if (host == null || host.isBlank()) {
			return null;
		}
		int port = parsePort(firstNonBlank(System.getProperty("ws.proxy.port"), System.getProperty("socksProxyPort")), 1080);
		log.info("ws.proxy.socks enabled host={} port={}", host, port);
		InetSocketAddress address = new InetSocketAddress(host, port);
		return new ProxySelector() {
			@Override
			public List<Proxy> select(URI uri) {
				return Collections.singletonList(new Proxy(Proxy.Type.SOCKS, address));
			}

			@Override
			public void connectFailed(URI uri, java.net.SocketAddress sa, java.io.IOException ioe) {
				log.warn("ws.proxy.socks.failed uri={} error={}", uri, ioe.getMessage());
			}
		};
	}

	private static ProxySelector buildHttpProxySelector() {
		String host = firstNonBlank(System.getProperty("ws.proxy.host"), System.getProperty("https.proxyHost"), System.getProperty("http.proxyHost"));
		if (host == null || host.isBlank()) {
			return null;
		}
		int port = parsePort(firstNonBlank(System.getProperty("ws.proxy.port"), System.getProperty("https.proxyPort"), System.getProperty("http.proxyPort")), 8080);
		log.info("ws.proxy.http enabled host={} port={}", host, port);
		return ProxySelector.of(new InetSocketAddress(host, port));
	}

	private static String firstNonBlank(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
	}

	private static int parsePort(String value, int defaultPort) {
		if (value == null || value.isBlank()) {
			return defaultPort;
		}
		try {
			return Integer.parseInt(value.trim());
		} catch (Exception ex) {
			return defaultPort;
		}
	}
}
