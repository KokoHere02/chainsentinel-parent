package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.config.HoldingSnapshotProperties;
import com.chainsentinel.infra.entity.AddressTokenHoldingEntity;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.repository.AddressTokenHoldingRepository;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AddressHoldingSnapshotServiceTest {

	@Mock
	private MonitorAddressRepository monitorAddressRepository;
	@Mock
	private ChainConfigRepository chainConfigRepository;
	@Mock
	private AddressTokenHoldingRepository addressTokenHoldingRepository;
	@Mock
	private MonitorAddressScopeRepository monitorAddressScopeRepository;
	@Mock
	private ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;

	@Test
	void shouldScanOnlyConfiguredScopeChainAndPersistWhenChanged() throws Exception {
		MonitorAddressScopeEntity scope = scope(101L, 11L, "ETH", "mainnet", true);
		when(monitorAddressScopeRepository.findByEnabledTrue()).thenReturn(List.of(scope));

		MonitorAddressEntity address = address(11L, "0x1111111111111111111111111111111111111111", true);
		when(monitorAddressRepository.findByIdInAndEnabledTrue(List.of(11L))).thenReturn(List.of(address));

		ChainConfigEntity eth = chain("ETH", "mainnet", "enc:eth", true);
		ChainConfigEntity bsc = chain("BSC", "mainnet", "enc:bsc", true);
		when(chainConfigRepository.findByEnabledTrue()).thenReturn(List.of(eth, bsc));

		try (RpcBalanceServer ethRpc = new RpcBalanceServer("0x2a");
			 RpcBalanceServer bscRpc = new RpcBalanceServer("0x99")) {
			when(chainConfigRpcUrlCodec.decryptIfNeeded("enc:eth", "ETH", "mainnet")).thenReturn(ethRpc.url());
			when(addressTokenHoldingRepository.findByMonitorScopeIdAndTokenContract(101L, "NATIVE"))
				.thenReturn(Optional.empty());

			HoldingSnapshotProperties properties = new HoldingSnapshotProperties();
			AddressHoldingSnapshotService service = new AddressHoldingSnapshotService(
				monitorAddressRepository,
				chainConfigRepository,
				addressTokenHoldingRepository,
				monitorAddressScopeRepository,
				chainConfigRpcUrlCodec,
				properties,
				new SimpleMeterRegistry()
			);

			AddressHoldingSnapshotService.SnapshotResult result = service.refreshNativeHoldings();

			assertEquals(1, result.scanned());
			assertEquals(1, result.changed());
			assertEquals(0, result.failed());
			assertEquals(1, ethRpc.requestCount());
			assertEquals(0, bscRpc.requestCount());
			verify(addressTokenHoldingRepository, times(1)).save(any(AddressTokenHoldingEntity.class));
			verify(chainConfigRpcUrlCodec, times(1)).decryptIfNeeded("enc:eth", "ETH", "mainnet");
			verify(chainConfigRpcUrlCodec, never()).decryptIfNeeded("enc:bsc", "BSC", "mainnet");
		}
	}

	@Test
	void shouldNotPersistWhenBalanceUnchanged() throws Exception {
		MonitorAddressScopeEntity scope = scope(202L, 22L, "ETH", "mainnet", true);
		when(monitorAddressScopeRepository.findByEnabledTrue()).thenReturn(List.of(scope));

		MonitorAddressEntity address = address(22L, "0x2222222222222222222222222222222222222222", true);
		when(monitorAddressRepository.findByIdInAndEnabledTrue(List.of(22L))).thenReturn(List.of(address));

		ChainConfigEntity eth = chain("ETH", "mainnet", "enc:eth", true);
		when(chainConfigRepository.findByEnabledTrue()).thenReturn(List.of(eth));

		AddressTokenHoldingEntity existing = new AddressTokenHoldingEntity();
		ReflectionTestUtils.setField(existing, "id", 9001L);
		existing.setMonitorScopeId(202L);
		existing.setTokenContract("NATIVE");
		existing.setBalanceRaw("42");
		when(addressTokenHoldingRepository.findByMonitorScopeIdAndTokenContract(202L, "NATIVE"))
			.thenReturn(Optional.of(existing));

		try (RpcBalanceServer ethRpc = new RpcBalanceServer("0x2a")) {
			when(chainConfigRpcUrlCodec.decryptIfNeeded("enc:eth", "ETH", "mainnet")).thenReturn(ethRpc.url());

			HoldingSnapshotProperties properties = new HoldingSnapshotProperties();
			AddressHoldingSnapshotService service = new AddressHoldingSnapshotService(
				monitorAddressRepository,
				chainConfigRepository,
				addressTokenHoldingRepository,
				monitorAddressScopeRepository,
				chainConfigRpcUrlCodec,
				properties,
				new SimpleMeterRegistry()
			);

			AddressHoldingSnapshotService.SnapshotResult result = service.refreshNativeHoldings();

			assertEquals(1, result.scanned());
			assertEquals(0, result.changed());
			assertEquals(0, result.failed());
			assertEquals(1, ethRpc.requestCount());
			verify(addressTokenHoldingRepository, never()).save(any(AddressTokenHoldingEntity.class));
		}
	}

	private static MonitorAddressScopeEntity scope(Long id, Long addressId, String chain, String network, boolean enabled) {
		MonitorAddressScopeEntity entity = new MonitorAddressScopeEntity();
		ReflectionTestUtils.setField(entity, "id", id);
		entity.setMonitorAddressId(addressId);
		entity.setChain(chain);
		entity.setNetwork(network);
		entity.setEnabled(enabled);
		return entity;
	}

	private static MonitorAddressEntity address(Long id, String value, boolean enabled) {
		MonitorAddressEntity entity = new MonitorAddressEntity();
		ReflectionTestUtils.setField(entity, "id", id);
		entity.setAddress(value);
		entity.setEnabled(enabled);
		return entity;
	}

	private static ChainConfigEntity chain(String chain, String network, String rpcUrl, boolean enabled) {
		ChainConfigEntity entity = new ChainConfigEntity();
		entity.setChain(chain);
		entity.setNetwork(network);
		entity.setRpcUrl(rpcUrl);
		entity.setConfirmRequired(12);
		entity.setEnabled(enabled);
		return entity;
	}

	private static final class RpcBalanceServer implements AutoCloseable {

		private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\"[^\"]+\"|\\d+)");
		private final HttpServer server;
		private final AtomicInteger requestCount = new AtomicInteger();
		private final String balanceHex;

		private RpcBalanceServer(String balanceHex) throws IOException {
			this.balanceHex = balanceHex;
			this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			this.server.createContext("/", this::handle);
			this.server.start();
		}

		private void handle(HttpExchange exchange) throws IOException {
			requestCount.incrementAndGet();
			String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String id = extractId(requestBody);
			String response = "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":\"" + balanceHex + "\"}";
			byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream output = exchange.getResponseBody()) {
				output.write(bytes);
			}
		}

		private String extractId(String body) {
			Matcher matcher = ID_PATTERN.matcher(body);
			if (!matcher.find()) {
				return "1";
			}
			return matcher.group(1);
		}

		private int requestCount() {
			return requestCount.get();
		}

		private String url() {
			return "http://127.0.0.1:" + server.getAddress().getPort();
		}

		@Override
		public void close() {
			server.stop(0);
		}
	}
}
