package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.ChainConfigService;
import com.chainsentinel.core.service.dto.ChainConfigUpsertCommand;
import com.chainsentinel.core.service.dto.ChainConfigView;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultChainConfigService implements ChainConfigService {

    private final ChainConfigRepository chainConfigRepository;

    public DefaultChainConfigService(ChainConfigRepository chainConfigRepository) {
        this.chainConfigRepository = chainConfigRepository;
    }

    @Override
    @Transactional
    public ChainConfigView upsert(ChainConfigUpsertCommand command) {
        ChainConfigEntity entity = chainConfigRepository
                .findByChainAndNetwork(command.chain(), command.network())
                .orElseGet(ChainConfigEntity::new);

        entity.setChain(command.chain());
        entity.setNetwork(command.network());
        entity.setRpcUrl(command.rpcUrl());
        entity.setConfirmRequired(command.confirmRequired());
        entity.setEnabled(command.enabled());

        ChainConfigEntity saved = chainConfigRepository.save(entity);
        return new ChainConfigView(
                saved.getId(),
                saved.getChain(),
                saved.getNetwork(),
                saved.getRpcUrl(),
                saved.getConfirmRequired(),
                saved.getEnabled()
        );
    }
}
