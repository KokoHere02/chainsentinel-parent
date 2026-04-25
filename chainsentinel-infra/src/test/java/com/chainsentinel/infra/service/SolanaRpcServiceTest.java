package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SolanaRpcServiceTest {

	private HttpServer server;

	@AfterEach
	void tearDown() {
		if (server != null) {
			server.stop(0);
		}
	}

	@Test
	void shouldGetBalanceLamports() throws Exception {
		server = startServer("""
			{"jsonrpc":"2.0","result":{"context":{"slot":123},"value":"123456"},"id":1}
			""");

		SolanaRpcService service = new SolanaRpcService(new ObjectMapper());
		BigInteger balance = service.getBalanceLamports(url(), "7kbnvuGBxxj8AG9qp8Scn56muWGaRaFqxg1FsRp3PaFT");

		assertEquals(new BigInteger("123456"), balance);
	}

	@Test
	void shouldThrowWhenRpcReturnsError() throws Exception {
		server = startServer("""
			{"jsonrpc":"2.0","error":{"code":-32601,"message":"Method not found"},"id":1}
			""");

		SolanaRpcService service = new SolanaRpcService(new ObjectMapper());

		IllegalStateException ex = assertThrows(IllegalStateException.class,
			() -> service.getLatestSlot(url()));
		assertEquals(true, ex.getMessage().contains("Method not found"));
	}

	@Test
	void shouldParseNativeTransfersFromTransaction() throws Exception {
		server = startServer(Map.of(
			"getTransaction", """
				{"jsonrpc":"2.0","result":{
				  "slot":200,
				  "blockTime":1715000000,
				  "meta":{"err":null},
				  "transaction":{"message":{"instructions":[
				    {"program":"system","parsed":{"type":"transfer","info":{"source":"From111111111111111111111111111111111","destination":"To11111111111111111111111111111111111","lamports":"5000"}}},
				    {"program":"spl-token","parsed":{"type":"transferChecked","info":{
				      "source":"TokenFrom1111111111111111111111111111111",
				      "destination":"TokenTo11111111111111111111111111111111",
				      "mint":"So11111111111111111111111111111111111111112",
				      "tokenAmount":{"amount":"4200","decimals":6}
				    }}},
				    {"program":"memo","parsed":{"type":"memo","info":{"memo":"x"}}}
				  ]}}
				},"id":1}
				"""
		));

		SolanaRpcService service = new SolanaRpcService(new ObjectMapper());
		var transfers = service.getNativeTransfersBySignature(url(), "sig-1");

		assertEquals(1, transfers.size());
		assertEquals("From111111111111111111111111111111111", transfers.get(0).source());
		assertEquals("To11111111111111111111111111111111111", transfers.get(0).destination());
		assertEquals(new BigInteger("5000"), transfers.get(0).lamports());
		assertEquals(0, transfers.get(0).logIndex());
		assertEquals(200, transfers.get(0).slot());

		var tokenTransfers = service.getTokenTransfersBySignature(url(), "sig-1");
		assertEquals(1, tokenTransfers.size());
		assertEquals("So11111111111111111111111111111111111111112", tokenTransfers.get(0).mint());
		assertEquals(new BigInteger("4200"), tokenTransfers.get(0).amount());
		assertEquals(6, tokenTransfers.get(0).decimals());
		assertEquals(1, tokenTransfers.get(0).logIndex());

		var all = service.getTransfersBySignature(url(), "sig-1");
		assertEquals(1, all.nativeTransfers().size());
		assertEquals(1, all.tokenTransfers().size());
	}

	@Test
	void shouldGetSplTokenBalanceByOwnerAndMint() throws Exception {
		server = startServer(Map.of(
			"getTokenAccountsByOwner", """
				{"jsonrpc":"2.0","result":{
				  "context":{"slot":777},
				  "value":[
				    {"account":{"data":{"parsed":{"info":{"tokenAmount":{"amount":"100","decimals":6}}}}}},
				    {"account":{"data":{"parsed":{"info":{"tokenAmount":{"amount":"250","decimals":6}}}}}}
				  ]
				},"id":1}
				"""
		));

		SolanaRpcService service = new SolanaRpcService(new ObjectMapper());
		SolanaRpcService.SplTokenBalance balance = service.getSplTokenBalanceByOwnerAndMint(
			url(),
			"7kbnvuGBxxj8AG9qp8Scn56muWGaRaFqxg1FsRp3PaFT",
			"So11111111111111111111111111111111111111112"
		);

		assertEquals(new BigInteger("350"), balance.amount());
		assertEquals(6, balance.decimals());
	}

	private HttpServer startServer(String body) throws IOException {
		HttpServer local = HttpServer.create(new InetSocketAddress(0), 0);
		local.createContext("/", new FixedResponseHandler(body));
		local.start();
		return local;
	}

	private HttpServer startServer(Map<String, String> responses) throws IOException {
		HttpServer local = HttpServer.create(new InetSocketAddress(0), 0);
		local.createContext("/", new MethodDispatchResponseHandler(responses));
		local.start();
		return local;
	}

	private String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
	}

	private record FixedResponseHandler(String body) implements HttpHandler {

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		}
	}

	private record MethodDispatchResponseHandler(Map<String, String> responses) implements HttpHandler {

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String response = responses.entrySet().stream()
				.filter(entry -> requestBody.contains("\"method\":\"" + entry.getKey() + "\""))
				.map(Map.Entry::getValue)
				.findFirst()
				.orElse("{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32601,\"message\":\"Method not found\"},\"id\":1}");
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		}
	}
}
