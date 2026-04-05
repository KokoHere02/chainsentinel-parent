package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.ChainConfigService;
import com.chainsentinel.core.service.dto.ChainConfigUpsertCommand;
import com.chainsentinel.core.service.dto.ChainConfigView;
import com.chainsentinel.infra.entity.ChainConfigEntity;
import com.chainsentinel.infra.repository.ChainConfigRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultChainConfigService implements ChainConfigService {

private final ChainConfigRepository chainConfigRepository;
private final ChainConfigRpcUrlCodec chainConfigRpcUrlCodec;

public DefaultChainConfigService(
ChainConfigRepository chainConfigRepository,
ChainConfigRpcUrlCodec chainConfigRpcUrlCodec
) {
this.chainConfigRepository = chainConfigRepository;
this.chainConfigRpcUrlCodec = chainConfigRpcUrlCodec;
}

@Override
@Transactional
public ChainConfigView upsert(ChainConfigUpsertCommand command) {
ChainConfigEntity entity = chainConfigRepository
.findByChainAndNetwork(command.chain(), command.network())
.orElseGet(ChainConfigEntity::new);

String encryptedRpcUrl = chainConfigRpcUrlCodec.encrypt(command.rpcUrl());

entity.setChain(command.chain());
entity.setNetwork(command.network());
entity.setRpcUrl(encryptedRpcUrl);
entity.setConfirmRequired(command.confirmRequired());
entity.setEnabled(command.enabled());

ChainConfigEntity saved = chainConfigRepository.save(entity);
return toView(saved);
}

@Override
@Transactional(readOnly = true)
public List<ChainConfigView> list() {
return chainConfigRepository.findAllByOrderByChainAscNetworkAsc().stream()
.map(this::toView)
.toList();
}

@Override
@Transactional(readOnly = true)
public Optional<ChainConfigView> find(String chain, String network) {
return chainConfigRepository.findByChainAndNetwork(chain, network)
.map(this::toView);
}

private ChainConfigView toView(ChainConfigEntity entity) {
String rpcUrl = chainConfigRpcUrlCodec.decryptIfNeeded(entity.getRpcUrl(), entity.getChain(), entity.getNetwork());
if (rpcUrl == null) {
rpcUrl = entity.getRpcUrl();
}
return new ChainConfigView(
entity.getId(),
entity.getChain(),
entity.getNetwork(),
rpcUrl,
entity.getConfirmRequired(),
entity.getEnabled()
);
}
}