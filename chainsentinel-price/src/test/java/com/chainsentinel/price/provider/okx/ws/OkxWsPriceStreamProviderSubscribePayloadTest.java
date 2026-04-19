package com.chainsentinel.price.provider.okx.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OkxWsPriceStreamProviderSubscribePayloadTest {

	@Mock
	private PriceProviderRuntimeConfig runtimeConfig;

	@Mock
	private OkxWsMessageParser messageParser;

	@Test
	void shouldBuildSubscribePayloadWithInstTypeAndInstId() throws Exception {
		OkxWsPriceStreamProvider provider = new OkxWsPriceStreamProvider(
			runtimeConfig,
			messageParser,
			new SimpleMeterRegistry(),
			new OkxWsQuoteGuardProperties()
		);
		Method collectMethod = OkxWsPriceStreamProvider.class.getDeclaredMethod("collectSubscribeArgs", List.class);
		collectMethod.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<Object> targets = (List<Object>) collectMethod.invoke(provider, List.of(
			new PriceQuery("OFFCHAIN", PriceInstType.SWAP, "BTC", "USDT", null)
		));
		assertEquals(1, targets.size());

		Method payloadMethod = findPayloadMethod();
		String payload = (String) payloadMethod.invoke(provider, targets.get(0));
		assertNotNull(payload);

		JsonNode root = new ObjectMapper().readTree(payload);
		JsonNode arg = root.path("args").get(0);
		assertEquals("subscribe", root.path("op").asText());
		assertEquals("tickers", arg.path("channel").asText());
		assertEquals("SWAP", arg.path("instType").asText());
		assertEquals("BTC-USDT", arg.path("instId").asText());
	}

	private Method findPayloadMethod() {
		for (Method method : OkxWsPriceStreamProvider.class.getDeclaredMethods()) {
			if ("buildSubscribePayload".equals(method.getName()) && method.getParameterCount() == 1) {
				method.setAccessible(true);
				return method;
			}
		}
		throw new IllegalStateException("buildSubscribePayload method not found");
	}
}