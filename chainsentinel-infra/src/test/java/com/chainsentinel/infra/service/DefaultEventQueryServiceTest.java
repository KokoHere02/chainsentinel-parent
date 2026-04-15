package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.EventStatus;
import com.chainsentinel.core.model.TokenType;
import com.chainsentinel.core.service.dto.EventQuery;
import com.chainsentinel.core.service.dto.EventView;
import com.chainsentinel.infra.entity.AssetEventEntity;
import com.chainsentinel.infra.repository.AssetEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class DefaultEventQueryServiceTest {

	@Mock
	private AssetEventRepository assetEventRepository;

	@InjectMocks
	private DefaultEventQueryService service;

	@Test
	void shouldQueryEventsAndMapToView() {
		AssetEventEntity entity = new AssetEventEntity();
		entity.setChain("ETH");
		entity.setNetwork("sepolia");
		entity.setBlockNumber(100L);
		entity.setTxHash("0xtx");
		entity.setLogIndex(1);
		entity.setFromAddress("0xfrom");
		entity.setToAddress("0xto");
		entity.setTokenType(TokenType.ERC20);
		entity.setSymbol("USDT");
		entity.setAmount("123450000");
		entity.setStatus(EventStatus.CONFIRMED);
		entity.setConfirmations(12);
		entity.setOccurredAt(Instant.parse("2026-03-28T10:00:00Z"));

		Page<AssetEventEntity> page = new PageImpl<>(List.of(entity));
		when(assetEventRepository.findAll(org.mockito.ArgumentMatchers.<Specification<AssetEventEntity>>any(), org.mockito.ArgumentMatchers.any(PageRequest.class)))
			.thenReturn(page);

		EventQuery query = new EventQuery("ETH", "0xto", EventStatus.CONFIRMED,
			Instant.parse("2026-03-28T00:00:00Z"), Instant.parse("2026-03-29T00:00:00Z"));
		PageRequest pageable = PageRequest.of(0, 20);

		Page<EventView> result = service.query(query, pageable);

		assertEquals(1, result.getContent().size());
		EventView view = result.getContent().get(0);
		assertEquals("ETH", view.chain());
		assertEquals("sepolia", view.network());
		assertEquals("0xtx", view.txHash());
		assertEquals("123450000", view.amount());
		assertEquals(EventStatus.CONFIRMED, view.status());

		ArgumentCaptor<Specification<AssetEventEntity>> specCaptor = ArgumentCaptor.forClass(Specification.class);
		verify(assetEventRepository).findAll(specCaptor.capture(), org.mockito.ArgumentMatchers.eq(pageable));
	}
}