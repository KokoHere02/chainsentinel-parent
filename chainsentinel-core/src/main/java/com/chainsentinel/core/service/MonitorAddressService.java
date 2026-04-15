package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.MonitorAddressUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressView;
import java.util.List;

public interface MonitorAddressService {

	MonitorAddressView upsert(MonitorAddressUpsertCommand command);

	List<MonitorAddressView> search(String chain, String keyword, int limit, boolean enabledOnly);

	List<MonitorAddressView> list(String chain, String keyword, Boolean enabled, int limit);
}