package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.MonitorTokenService;
import com.chainsentinel.core.service.dto.MonitorTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorTokenView;
import com.chainsentinel.infra.entity.MonitorTokenEntity;
import com.chainsentinel.infra.repository.MonitorTokenRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultMonitorTokenService implements MonitorTokenService {

	private final MonitorTokenRepository monitorTokenRepository;

	public DefaultMonitorTokenService(MonitorTokenRepository monitorTokenRepository) {
		this.monitorTokenRepository = monitorTokenRepository;
	}

	@Override
	@Transactional
	public MonitorTokenView upsert(MonitorTokenUpsertCommand command) {
		String chain = command.chain().trim().toUpperCase();
		String tokenContract = command.tokenContract().trim().toLowerCase();

		MonitorTokenEntity entity = monitorTokenRepository.findByChainAndTokenContract(chain, tokenContract)
			.orElseGet(MonitorTokenEntity::new);

		entity.setChain(chain);
		entity.setTokenContract(tokenContract);
		entity.setSymbol(command.symbol());
		entity.setEnabled(Boolean.TRUE.equals(command.enabled()));

		MonitorTokenEntity saved = monitorTokenRepository.save(entity);
		return new MonitorTokenView(
			saved.getId(),
			saved.getChain(),
			saved.getTokenContract(),
			saved.getSymbol(),
			saved.getEnabled()
		);
	}

}
