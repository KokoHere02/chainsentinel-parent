package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.core.service.dto.EventQuery;
import com.chainsentinel.core.service.dto.EventView;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultEventQueryServiceTest {

    @Mock
    private AssetEventRepository assetEventRepository;

    @Test
    void shouldMapEntityPageToViewPage() {
        DefaultEventQueryService service = new DefaultEventQueryService(assetEventRepository);

        AssetEventEntity entity = new AssetEventEntity();
        ReflectionTestUtils.setField(entity, "id", 10L);
        entity.setChain("ETH");
        entity.setNetwork("mainnet");
        entity.setBlockNumber(1000L);
        entity.setTxHash("0xtx");
        entity.setLogIndex(2);
        entity.setFromAddress("0xfrom");
        entity.setToAddress("0xto");
        entity.setTokenType(TokenType.ERC20);
        entity.setSymbol("USDT");
        entity.setAmount(new BigDecimal("123.45"));
        entity.setStatus(EventStatus.CONFIRMED);
        entity.setConfirmations(12);
        Instant occurredAt = Instant.parse("2026-03-28T10:00:00Z");
        entity.setOccurredAt(occurredAt);

        Pageable pageable = PageRequest.of(0, 20);
        Page<AssetEventEntity> page = new PageImpl<>(List.of(entity), pageable, 1);
        when(assetEventRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        EventQuery query = new EventQuery("ETH", "0xto", EventStatus.CONFIRMED, null, null);
        Page<EventView> result = service.query(query, pageable);

        assertEquals(1, result.getTotalElements());
        EventView view = result.getContent().get(0);
        assertEquals(10L, view.id());
        assertEquals("ETH", view.chain());
        assertEquals("mainnet", view.network());
        assertEquals(1000L, view.blockNumber());
        assertEquals("0xtx", view.txHash());
        assertEquals(2, view.logIndex());
        assertEquals("0xfrom", view.fromAddress());
        assertEquals("0xto", view.toAddress());
        assertEquals(TokenType.ERC20, view.tokenType());
        assertEquals("USDT", view.symbol());
        assertEquals(new BigDecimal("123.45"), view.amount());
        assertEquals(EventStatus.CONFIRMED, view.status());
        assertEquals(12, view.confirmations());
        assertEquals(occurredAt, view.occurredAt());

        verify(assetEventRepository).findAll(any(Specification.class), eq(pageable));
    }
}
