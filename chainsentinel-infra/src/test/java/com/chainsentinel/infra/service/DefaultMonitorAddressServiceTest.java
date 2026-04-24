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
import com.chainsentinel.infra.entity.MonitorAddressScopeEntity;
import com.chainsentinel.infra.entity.MonitorScopeTokenEntity;
import com.chainsentinel.infra.repository.MonitorAddressRepository;
import com.chainsentinel.infra.repository.MonitorAddressScopeRepository;
import com.chainsentinel.infra.repository.MonitorScopeTokenRepository;
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
	@Mock
	private MonitorAddressScopeRepository monitorAddressScopeRepository;
	@Mock
	private MonitorScopeTokenRepository monitorScopeTokenRepository;

	@Test
	void shouldNormalizeAndUpdateExistingAddress() {
		MonitorAddressEntity existing = new MonitorAddressEntity();
		ReflectionTestUtils.setField(existing, "id", 1L);
		existing.setAddress("0xabc");
		existing.setEnabled(true);

		when(monitorAddressRepository.findByAddress("0xabc")).thenReturn(Optional.of(existing));
		when(monitorAddressRepository.save(any(MonitorAddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		DefaultMonitorAddressService service = new DefaultMonitorAddressService(
			monitorAddressRepository,
			monitorAddressScopeRepository,
			monitorScopeTokenRepository
		);
		MonitorAddressUpsertCommand command = new MonitorAddressUpsertCommand(" 0xAbC ", "vip", null);

		MonitorAddressView view = service.upsert(command);

		ArgumentCaptor<MonitorAddressEntity> captor = ArgumentCaptor.forClass(MonitorAddressEntity.class);
		verify(monitorAddressRepository).save(captor.capture());
		MonitorAddressEntity saved = captor.getValue();
		assertEquals("0xabc", saved.getAddress());
		assertEquals("vip", saved.getTag());
		assertFalse(saved.getEnabled());

		assertEquals(1L, view.id());
		assertEquals("0xabc", view.address());
		assertEquals("vip", view.tag());
		assertFalse(view.enabled());
	}

	@Test
	void shouldCreateNewAddressWhenNotExist() {
		when(monitorAddressRepository.findByAddress("0xdef")).thenReturn(Optional.empty());
		when(monitorAddressRepository.save(any(MonitorAddressEntity.class))).thenAnswer(invocation -> {
			MonitorAddressEntity entity = invocation.getArgument(0);
			ReflectionTestUtils.setField(entity, "id", 2L);
			return entity;
		});

		DefaultMonitorAddressService service = new DefaultMonitorAddressService(
			monitorAddressRepository,
			monitorAddressScopeRepository,
			monitorScopeTokenRepository
		);
		MonitorAddressUpsertCommand command = new MonitorAddressUpsertCommand("0xDeF", "new", true);

		MonitorAddressView view = service.upsert(command);

		assertEquals(2L, view.id());
		assertEquals("0xdef", view.address());
		assertEquals("new", view.tag());
		assertTrue(view.enabled());
	}

	@Test
	void shouldListAddressesWithNormalizedFilters() {
		MonitorAddressEntity entity = new MonitorAddressEntity();
		ReflectionTestUtils.setField(entity, "id", 3L);
		entity.setAddress("0xaaa");
		entity.setTag("wallet");
		entity.setEnabled(true);
		when(monitorAddressRepository.listByFilters(any(), any(), any(Pageable.class)))
			.thenReturn(List.of(entity));

		DefaultMonitorAddressService service = new DefaultMonitorAddressService(
			monitorAddressRepository,
			monitorAddressScopeRepository,
			monitorScopeTokenRepository
		);
		List<MonitorAddressView> result = service.list(" 0xAA ", true, 20);

		assertEquals(1, result.size());
		assertEquals("0xaaa", result.get(0).address());

		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		verify(monitorAddressRepository).listByFilters(org.mockito.ArgumentMatchers.eq("0xaa"), org.mockito.ArgumentMatchers.eq(true), pageableCaptor.capture());
		assertEquals(PageRequest.of(0, 20), pageableCaptor.getValue());
	}

	@Test
	void shouldUpsertAddress() {
		when(monitorAddressRepository.findByAddress("0xabc")).thenReturn(Optional.empty());
		when(monitorAddressRepository.save(any(MonitorAddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		DefaultMonitorAddressService service = new DefaultMonitorAddressService(
			monitorAddressRepository,
			monitorAddressScopeRepository,
			monitorScopeTokenRepository
		);
		MonitorAddressUpsertCommand command = new MonitorAddressUpsertCommand("0xAbC", "wallet", true);
		service.upsert(command);

		verify(monitorAddressRepository).findByAddress("0xabc");
	}

	@Test
	void shouldDisableScopesAndTokensWhenAddressDisabled() {
		MonitorAddressEntity existing = new MonitorAddressEntity();
		ReflectionTestUtils.setField(existing, "id", 10L);
		existing.setAddress("0xabc");
		existing.setEnabled(true);
		when(monitorAddressRepository.findByAddress("0xabc")).thenReturn(Optional.of(existing));
		when(monitorAddressRepository.save(any(MonitorAddressEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		MonitorAddressScopeEntity scope = new MonitorAddressScopeEntity();
		ReflectionTestUtils.setField(scope, "id", 100L);
		scope.setMonitorAddressId(10L);
		scope.setEnabled(true);
		when(monitorAddressScopeRepository.findByMonitorAddressId(10L)).thenReturn(List.of(scope));

		MonitorScopeTokenEntity token = new MonitorScopeTokenEntity();
		ReflectionTestUtils.setField(token, "id", 1000L);
		token.setMonitorScopeId(100L);
		token.setEnabled(true);
		when(monitorScopeTokenRepository.findByMonitorScopeIdInOrderByMonitorScopeIdAscIdAsc(List.of(100L)))
			.thenReturn(List.of(token));

		DefaultMonitorAddressService service = new DefaultMonitorAddressService(
			monitorAddressRepository,
			monitorAddressScopeRepository,
			monitorScopeTokenRepository
		);
		service.upsert(new MonitorAddressUpsertCommand("0xAbC", "wallet", false));

		assertFalse(scope.getEnabled());
		assertFalse(token.getEnabled());
		verify(monitorAddressScopeRepository).saveAll(List.of(scope));
		verify(monitorScopeTokenRepository).saveAll(List.of(token));
	}
}
