package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.MonitorScopeTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorScopeTokenView;
import java.util.List;

public interface MonitorScopeTokenService {

	MonitorScopeTokenView upsert(MonitorScopeTokenUpsertCommand command);

	List<MonitorScopeTokenView> list(Long monitorScopeId, String keyword, Boolean enabled, int limit);
}

