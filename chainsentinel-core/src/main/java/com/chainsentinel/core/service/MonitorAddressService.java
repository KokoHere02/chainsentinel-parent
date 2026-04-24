package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.MonitorAddressUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressView;
import java.util.List;

public interface MonitorAddressService {

	MonitorAddressView upsert(MonitorAddressUpsertCommand command);

	List<MonitorAddressView> list(String keyword, Boolean enabled, int limit);
}
