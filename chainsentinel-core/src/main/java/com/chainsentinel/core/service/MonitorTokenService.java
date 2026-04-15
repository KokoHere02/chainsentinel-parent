package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.MonitorTokenUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorTokenView;
import java.util.List;

public interface MonitorTokenService {

	MonitorTokenView upsert(MonitorTokenUpsertCommand command);

	List<MonitorTokenView> list(String chain, String keyword, Boolean enabled, int limit);
}