package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.MonitorTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorTokenView;

public interface MonitorTokenService {

	MonitorTokenView upsert(MonitorTokenUpsertCommand command);
}