package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.service.dto.MonitorAddressUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressView;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DefaultMonitorAddressServiceTest {

	@Mock
	private MonitorAddressRepository monitorAddressRepository;

	@Test
	void shouldNormalizeAndUpdateExistingAddress() {
		MonitorAddressEntity existing = new MonitorAddressEntity();
		ReflectionTestUtils.setField(existing, "id", 1L);
		existing.setChain("ETH");
		existing.setAddress("0xabc");
		existing.setEnabled(true);

		when(monitorAddressRepository.findByChainAndAddress("ETH", "0xabc")).thenReturn(Optional.of(existing));
		when(monitorAddressRepository.save(any(MonitorAddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		DefaultMonitorAddressService service = new DefaultMonitorAddressService(monitorAddressRepository);
		MonitorAddressUpsertCommand command = new MonitorAddressUpsertCommand(" eth ", " 0xAbC ", "vip", null);

		MonitorAddressView view = service.upsert(command);

		ArgumentCaptor<MonitorAddressEntity> captor = ArgumentCaptor.forClass(MonitorAddressEntity.class);
		verify(monitorAddressRepository).save(captor.capture());
		MonitorAddressEntity saved = captor.getValue();
		assertEquals("ETH", saved.getChain());
		assertEquals("0xabc", saved.getAddress());
		assertEquals("vip", saved.getTag());
		assertFalse(saved.getEnabled());

		assertEquals(1L, view.id());
		assertEquals("ETH", view.chain());
		assertEquals("0xabc", view.address());
		assertEquals("vip", view.tag());
		assertFalse(view.enabled());
	}

	@Test
	void shouldCreateNewAddressWhenNotExist() {
		when(monitorAddressRepository.findByChainAndAddress("BSC", "0xdef")).thenReturn(Optional.empty());
		when(monitorAddressRepository.save(any(MonitorAddressEntity.class))).thenAnswer(invocation -> {
			MonitorAddressEntity entity = invocation.getArgument(0);
			ReflectionTestUtils.setField(entity, "id", 2L);
			return entity;
		});

		DefaultMonitorAddressService service = new DefaultMonitorAddressService(monitorAddressRepository);
		MonitorAddressUpsertCommand command = new MonitorAddressUpsertCommand("bsc", "0xDeF", "new", true);

		MonitorAddressView view = service.upsert(command);

		assertEquals(2L, view.id());
		assertEquals("BSC", view.chain());
		assertEquals("0xdef", view.address());
		assertEquals("new", view.tag());
		assertTrue(view.enabled());
	}

	@Test
	void shouldListAddressesWithNormalizedFilters() {
		MonitorAddressEntity entity = new MonitorAddressEntity();
		ReflectionTestUtils.setField(entity, "id", 3L);
		entity.setChain("ETH");
		entity.setAddress("0xaaa");
		entity.setTag("wallet");
		entity.setEnabled(true);

		when(monitorAddressRepository.listByFilters(any(), any(), any(), any(Pageable.class)))
			.thenReturn(List.of(entity));

		DefaultMonitorAddressService service = new DefaultMonitorAddressService(monitorAddressRepository);
		List<MonitorAddressView> result = service.list(" eth ", " 0xAA ", true, 20);

		assertEquals(1, result.size());
		assertEquals("ETH", result.get(0).chain());
		assertEquals("0xaaa", result.get(0).address());

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(monitorAddressRepository).listByFilters(org.mockito.ArgumentMatchers.eq("ETH"), org.mockito.ArgumentMatchers.eq("0xaa"), org.mockito.ArgumentMatchers.eq(true), pageableCaptor.capture());
		assertEquals(PageRequest.of(0, 20), pageableCaptor.getValue());
	}
}
