package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.MonitorAddressService;
import com.chainsentinel.core.service.dto.MonitorAddressUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressView;
import com.chainsentinel.infra.entity.MonitorAddressEntity;
import com.chainsentinel.infra.repository.MonitorAddressRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultMonitorAddressService implements MonitorAddressService {

	private final MonitorAddressRepository monitorAddressRepository;

	public DefaultMonitorAddressService(MonitorAddressRepository monitorAddressRepository) {
		this.monitorAddressRepository = monitorAddressRepository;
	}

	@Override
	@Transactional
	public MonitorAddressView upsert(MonitorAddressUpsertCommand command) {
		String chain = command.chain().trim().toUpperCase();
		String address = command.address().trim().toLowerCase();

		MonitorAddressEntity entity = monitorAddressRepository.findByChainAndAddress(chain, address)
			.orElseGet(MonitorAddressEntity::new);

		entity.setChain(chain);
		entity.setAddress(address);
		entity.setTag(command.tag());
		entity.setEnabled(Boolean.TRUE.equals(command.enabled()));

		MonitorAddressEntity saved = monitorAddressRepository.save(entity);
		return new MonitorAddressView(saved.getId(), saved.getChain(), saved.getAddress(), saved.getTag(),
			saved.getEnabled());
	}

}
