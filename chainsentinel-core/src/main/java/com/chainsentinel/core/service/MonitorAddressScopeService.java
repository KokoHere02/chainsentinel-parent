package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.MonitorAddressScopeUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressScopeView;
import java.util.List;

public interface MonitorAddressScopeService {

	MonitorAddressScopeView upsert(MonitorAddressScopeUpsertCommand command);

	List<MonitorAddressScopeView> list(Long monitorAddressId, String chain, String network, Boolean enabled, int limit);
}

