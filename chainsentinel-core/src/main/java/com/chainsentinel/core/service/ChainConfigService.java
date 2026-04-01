package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.ChainConfigUpsertCommand;
import com.chainsentinel.core.service.dto.ChainConfigView;
import java.util.List;
import java.util.Optional;

public interface ChainConfigService {

  ChainConfigView upsert(ChainConfigUpsertCommand command);

  List<ChainConfigView> list();

  Optional<ChainConfigView> find(String chain, String network);
}