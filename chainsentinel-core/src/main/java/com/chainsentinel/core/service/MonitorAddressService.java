package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.MonitorAddressUpsertCommand;
import com.chainsentinel.core.service.dto.MonitorAddressView;

public interface MonitorAddressService {

	MonitorAddressView upsert(MonitorAddressUpsertCommand command);
}
