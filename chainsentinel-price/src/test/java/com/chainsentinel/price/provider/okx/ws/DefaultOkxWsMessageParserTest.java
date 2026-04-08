package com.chainsentinel.price.provider.okx.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chainsentinel.price.stream.PriceStreamQuote;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultOkxWsMessageParserTest {

	@Test
	void shouldParseTickerMessage() {
		String payload = """
		{
		  \"arg\": {\"channel\": \"tickers\", \"instId\": \"BTC-USDT\"},
		  \"data\": [
		    {
		      \"instType\": \"SPOT\",
		      \"instId\": \"BTC-USDT\",
		      \"last\": \"65432.1\",
		      \"ts\": \"1704876947000\"
		    }
		  ]
		}
		""";

		DefaultOkxWsMessageParser parser = new DefaultOkxWsMessageParser();
		Optional<PriceStreamQuote> quoteOpt = parser.parse(payload);

		assertTrue(quoteOpt.isPresent());
		PriceStreamQuote quote = quoteOpt.get();
		assertEquals("okx_ws", quote.providerName());
		assertEquals("BTC", quote.baseSymbol());
		assertEquals("USDT", quote.quoteSymbol());
		assertEquals(1704876947000L, quote.ts());
	}
}