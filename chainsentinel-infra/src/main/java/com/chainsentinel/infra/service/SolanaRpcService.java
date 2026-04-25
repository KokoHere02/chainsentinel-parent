package com.chainsentinel.infra.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SolanaRpcService {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

	private final HttpClient httpClient;
	private final ObjectMapper objectMapper;

	@Autowired
	public SolanaRpcService(ObjectMapper objectMapper) {
		this(objectMapper, HttpClient.newBuilder()
			.connectTimeout(CONNECT_TIMEOUT)
			.build());
	}

	SolanaRpcService(ObjectMapper objectMapper, HttpClient httpClient) {
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
	}

	public BigInteger getBalanceLamports(String rpcUrl, String address) throws IOException {
		validateAddress(address);
		ArrayNode params = objectMapper.createArrayNode();
		params.add(address);
		ObjectNode options = params.addObject();
		options.put("commitment", "confirmed");
		JsonNode result = call(rpcUrl, "getBalance", params);
		String valueText = result.path("value").asText(null);
		if (!StringUtils.hasText(valueText) || !valueText.chars().allMatch(Character::isDigit)) {
			throw new IllegalStateException("invalid solana getBalance response: missing value");
		}
		return new BigInteger(valueText);
	}

	public long getLatestSlot(String rpcUrl) throws IOException {
		ArrayNode params = objectMapper.createArrayNode();
		ObjectNode options = params.addObject();
		options.put("commitment", "confirmed");
		JsonNode result = call(rpcUrl, "getSlot", params);
		if (!result.isIntegralNumber()) {
			throw new IllegalStateException("invalid solana getSlot response: result is not integral");
		}
		return result.longValue();
	}

	public List<SolanaSignatureInfo> getSignaturesForAddress(
		String rpcUrl,
		String address,
		int limit
	) throws IOException {
		validateAddress(address);
		int normalizedLimit = Math.max(1, Math.min(1_000, limit));
		ArrayNode params = objectMapper.createArrayNode();
		params.add(address);
		ObjectNode options = params.addObject();
		options.put("limit", normalizedLimit);
		options.put("commitment", "confirmed");
		JsonNode result = call(rpcUrl, "getSignaturesForAddress", params);
		if (!result.isArray()) {
			throw new IllegalStateException("invalid solana getSignaturesForAddress response: result is not array");
		}
		List<SolanaSignatureInfo> signatures = new ArrayList<>();
		for (JsonNode item : result) {
			String signature = item.path("signature").asText(null);
			if (!StringUtils.hasText(signature)) {
				continue;
			}
			long slot = item.path("slot").asLong(-1L);
			if (slot < 0) {
				continue;
			}
			boolean success = item.path("err").isNull() || item.path("err").isMissingNode();
			long blockTime = item.path("blockTime").asLong(0L);
			Instant occurredAt = blockTime > 0 ? Instant.ofEpochSecond(blockTime) : null;
			signatures.add(new SolanaSignatureInfo(signature, slot, occurredAt, success));
		}
		return signatures;
	}

	public List<SolanaNativeTransfer> getNativeTransfersBySignature(String rpcUrl, String signature) throws IOException {
		return getTransfersBySignature(rpcUrl, signature).nativeTransfers();
	}

	public List<SolanaTokenTransfer> getTokenTransfersBySignature(String rpcUrl, String signature) throws IOException {
		return getTransfersBySignature(rpcUrl, signature).tokenTransfers();
	}

	public SolanaTransactionTransfers getTransfersBySignature(String rpcUrl, String signature) throws IOException {
		if (!StringUtils.hasText(signature)) {
			throw new IllegalArgumentException("signature must not be blank");
		}
		ArrayNode params = objectMapper.createArrayNode();
		params.add(signature.trim());
		ObjectNode options = params.addObject();
		options.put("encoding", "jsonParsed");
		options.put("commitment", "confirmed");
		options.put("maxSupportedTransactionVersion", 0);
		JsonNode result = call(rpcUrl, "getTransaction", params);

		long slot = result.path("slot").asLong(-1L);
		if (slot < 0) {
			throw new IllegalStateException("invalid solana getTransaction response: missing slot");
		}
		long blockTime = result.path("blockTime").asLong(0L);
		Instant occurredAt = blockTime > 0 ? Instant.ofEpochSecond(blockTime) : null;
		boolean success = result.path("meta").path("err").isNull() || result.path("meta").path("err").isMissingNode();

		JsonNode instructions = result.path("transaction").path("message").path("instructions");
		if (!instructions.isArray()) {
			return new SolanaTransactionTransfers(List.of(), List.of());
		}

		List<SolanaNativeTransfer> nativeTransfers = new ArrayList<>();
		List<SolanaTokenTransfer> tokenTransfers = new ArrayList<>();
		int logIndex = 0;
		for (JsonNode instruction : instructions) {
			String program = instruction.path("program").asText("");
			JsonNode parsed = instruction.path("parsed");
			String type = parsed.path("type").asText("");
			JsonNode info = parsed.path("info");

			if ("system".equalsIgnoreCase(program) && "transfer".equalsIgnoreCase(type)) {
				String source = info.path("source").asText(null);
				String destination = info.path("destination").asText(null);
				String lamportsText = info.path("lamports").asText(null);
				if (StringUtils.hasText(source) && StringUtils.hasText(destination)
					&& StringUtils.hasText(lamportsText) && lamportsText.chars().allMatch(Character::isDigit)) {
					nativeTransfers.add(new SolanaNativeTransfer(
						signature.trim(),
						slot,
						occurredAt,
						success,
						logIndex,
						source,
						destination,
						new BigInteger(lamportsText)
					));
				}
			}

			if ("spl-token".equalsIgnoreCase(program)
				&& ("transfer".equalsIgnoreCase(type) || "transferChecked".equalsIgnoreCase(type))) {
				String source = info.path("source").asText(null);
				String destination = info.path("destination").asText(null);
				String mint = info.path("mint").asText(null);
				JsonNode tokenAmount = info.path("tokenAmount");
				String amountText = tokenAmount.path("amount").asText(null);
				int decimals = tokenAmount.path("decimals").asInt(-1);

				if (StringUtils.hasText(source) && StringUtils.hasText(destination) && StringUtils.hasText(mint)
					&& StringUtils.hasText(amountText) && amountText.chars().allMatch(Character::isDigit) && decimals >= 0) {
					tokenTransfers.add(new SolanaTokenTransfer(
						signature.trim(),
						slot,
						occurredAt,
						success,
						logIndex,
						source,
						destination,
						mint,
						new BigInteger(amountText),
						decimals
					));
				}
			}

			logIndex++;
		}
		return new SolanaTransactionTransfers(nativeTransfers, tokenTransfers);
	}

	public SplTokenBalance getSplTokenBalanceByOwnerAndMint(
		String rpcUrl,
		String ownerAddress,
		String mintAddress
	) throws IOException {
		validateAddress(ownerAddress);
		validateMint(mintAddress);

		ArrayNode params = objectMapper.createArrayNode();
		params.add(ownerAddress.trim());
		ObjectNode filter = objectMapper.createObjectNode();
		filter.put("mint", mintAddress.trim());
		params.add(filter);
		ObjectNode options = objectMapper.createObjectNode();
		options.put("encoding", "jsonParsed");
		options.put("commitment", "confirmed");
		params.add(options);

		JsonNode result = call(rpcUrl, "getTokenAccountsByOwner", params);
		JsonNode value = result.path("value");
		if (!value.isArray()) {
			throw new IllegalStateException("invalid getTokenAccountsByOwner response: result.value is not array");
		}
		return sumTokenBalanceFromAccounts(value);
	}

	private SplTokenBalance sumTokenBalanceFromAccounts(JsonNode accounts) {
		BigInteger total = BigInteger.ZERO;
		Integer decimals = null;
		for (JsonNode item : accounts) {
			JsonNode tokenAmount = item.path("account")
				.path("data")
				.path("parsed")
				.path("info")
				.path("tokenAmount");
			String amountText = tokenAmount.path("amount").asText(null);
			if (!StringUtils.hasText(amountText) || !amountText.chars().allMatch(Character::isDigit)) {
				continue;
			}
			total = total.add(new BigInteger(amountText));
			if (decimals == null) {
				int d = tokenAmount.path("decimals").asInt(-1);
				if (d >= 0) {
					decimals = d;
				}
			}
		}
		return new SplTokenBalance(total, decimals);
	}

	private JsonNode call(String rpcUrl, String method, JsonNode params) throws IOException {
		String validated = UrlSchemeSupport.requireSupported(rpcUrl, "rpcUrl");
		String scheme = UrlSchemeSupport.schemeOf(validated);
		if (!"http".equals(scheme) && !"https".equals(scheme)) {
			throw new IllegalArgumentException("solana rpc url must use http/https");
		}

		JsonNode payload = objectMapper.createObjectNode()
			.put("jsonrpc", "2.0")
			.put("id", 1)
			.put("method", method)
			.set("params", params);

		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(validated))
			.timeout(REQUEST_TIMEOUT)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
			.build();

		HttpResponse<String> response;
		try {
			response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IOException("solana rpc request interrupted", ex);
		}

		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("solana rpc http status=" + response.statusCode());
		}

		JsonNode root = objectMapper.readTree(response.body());
		JsonNode error = root.path("error");
		if (!error.isMissingNode() && !error.isNull()) {
			int code = error.path("code").asInt();
			String message = error.path("message").asText("unknown");
			throw new IllegalStateException("solana rpc error method=" + method + " code=" + code + " message=" + message);
		}

		JsonNode result = root.path("result");
		if (result.isMissingNode() || result.isNull()) {
			throw new IllegalStateException("solana rpc response missing result");
		}
		return result;
	}

	private void validateAddress(String address) {
		if (!StringUtils.hasText(address)) {
			throw new IllegalArgumentException("address must not be blank");
		}
		String trimmed = address.trim();
		if (trimmed.length() < 32 || trimmed.length() > 44) {
			throw new IllegalArgumentException("invalid solana address length");
		}
		for (int i = 0; i < trimmed.length(); i++) {
			char c = trimmed.charAt(i);
			boolean digit = c >= '1' && c <= '9';
			boolean upper = c >= 'A' && c <= 'Z' && c != 'I' && c != 'O';
			boolean lower = c >= 'a' && c <= 'z' && c != 'l';
			if (!digit && !upper && !lower) {
				throw new IllegalArgumentException("invalid solana address format");
			}
		}
	}

	private void validateMint(String mint) {
		if (!StringUtils.hasText(mint)) {
			throw new IllegalArgumentException("mint must not be blank");
		}
		String trimmed = mint.trim();
		if (trimmed.length() < 32 || trimmed.length() > 64) {
			throw new IllegalArgumentException("invalid mint length");
		}
	}

	public record SolanaSignatureInfo(
		String signature,
		long slot,
		Instant occurredAt,
		boolean success
	) {
	}

	public record SolanaNativeTransfer(
		String signature,
		long slot,
		Instant occurredAt,
		boolean success,
		int logIndex,
		String source,
		String destination,
		BigInteger lamports
	) {
	}

	public record SolanaTokenTransfer(
		String signature,
		long slot,
		Instant occurredAt,
		boolean success,
		int logIndex,
		String source,
		String destination,
		String mint,
		BigInteger amount,
		int decimals
	) {
	}

	public record SolanaTransactionTransfers(
		List<SolanaNativeTransfer> nativeTransfers,
		List<SolanaTokenTransfer> tokenTransfers
	) {
	}

	public record SplTokenBalance(
		BigInteger amount,
		Integer decimals
	) {
	}
}
