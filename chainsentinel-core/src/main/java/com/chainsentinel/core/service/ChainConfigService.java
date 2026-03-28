package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.ChainConfigUpsertCommand;
import com.chainsentinel.core.service.dto.ChainConfigView;

public interface ChainConfigService {

    ChainConfigView upsert(ChainConfigUpsertCommand command);
}
