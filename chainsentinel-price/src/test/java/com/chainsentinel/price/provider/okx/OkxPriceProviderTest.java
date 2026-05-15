package com.chainsentinel.price.provider.okx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.chainsentinel.price.api.PublicMarketDataClient;
import com.chainsentinel.price.api.PublicMarketDataClientRouter;
import com.chainsentinel.price.api.dto.PriceInstType;
import com.chainsentinel.price.api.dto.PriceQuery;
import com.chainsentinel.price.api.dto.PriceQuote;
import com.chainsentinel.price.config.PriceProviderRuntimeConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OkxPriceProviderTest {

    @Mock
    private PublicMarketDataClient marketDataClient;

    @Mock
    private PriceProviderRuntimeConfig runtimeConfig;

    @Test
    void shouldReturnQuoteWhenOkxResponseIsValid() {
        PriceQuery query = new PriceQuery("ETH", PriceInstType.SPOT, "BTC", "USDT", null);
        when(runtimeConfig.providerEnabled("okx")).thenReturn(true);
        when(marketDataClient.supportsProvider("okx")).thenReturn(true);
        when(marketDataClient.getQuote(query)).thenReturn(Optional.of(
            new PriceQuote("BTC", "USDT", new BigDecimal("70000.1"), 1711910400000L, "okx", false)
        ));

        OkxPriceProvider provider = new OkxPriceProvider(
            runtimeConfig,
            new PublicMarketDataClientRouter(List.of(marketDataClient)),
            new SimpleMeterRegistry()
        );
        Optional<PriceQuote> quote = provider.getQuote(query);

        assertTrue(quote.isPresent());
        assertEquals("BTC", quote.get().baseSymbol());
        assertEquals("USDT", quote.get().quoteSymbol());
        assertEquals("70000.1", quote.get().price().toPlainString());
    }
}
