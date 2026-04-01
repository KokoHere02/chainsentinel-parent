package com.chainsentinel.price.provider.okx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.config.PriceProperties;
import com.chainsentinel.price.provider.okx.dto.OkxTickerResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OkxPriceProviderTest {

    @Mock
    private OkxApiClient okxApiClient;

    @Test
    void shouldReturnQuoteWhenOkxResponseIsValid() {
        PriceProperties properties = new PriceProperties();
        properties.getOkx().setEnabled(true);

        OkxTickerResponse response = new OkxTickerResponse();
        response.setCode("0");
        OkxTickerResponse.OkxTickerData data = new OkxTickerResponse.OkxTickerData();
        data.setInstId("BTC-USDT");
        data.setLast("70000.1");
        data.setTs("1711910400000");
        response.setData(List.of(data));

        when(okxApiClient.fetchTicker("BTC-USDT")).thenReturn(Optional.of(response));

        OkxPriceProvider provider = new OkxPriceProvider(properties, okxApiClient, new SimpleMeterRegistry());
        Optional<com.chainsentinel.price.api.dto.PriceQuote> quote = provider.getQuote(
                new PriceQuery("ETH", PriceInstType.SPOT, "BTC", "USDT", null)
        );

        assertTrue(quote.isPresent());
        assertEquals("BTC", quote.get().baseSymbol());
        assertEquals("USDT", quote.get().quoteSymbol());
        assertEquals("70000.1", quote.get().price().toPlainString());
    }
}
