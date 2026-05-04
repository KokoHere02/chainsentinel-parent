package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.TradeAccountConnectivityTestView;
import com.chainsentinel.core.service.dto.TradeAccountCreateCommand;
import com.chainsentinel.core.service.dto.TradeAccountStreamStatusView;
import com.chainsentinel.core.service.dto.TradeAccountUpdateCommand;
import com.chainsentinel.core.service.dto.TradeAccountView;
import java.util.List;

public interface TradeAccountService {

	TradeAccountView create(TradeAccountCreateCommand command, Long operatorUserId);

	TradeAccountView update(Long id, TradeAccountUpdateCommand command, Long operatorUserId);

	void delete(Long id);

	TradeAccountView get(Long id);

	List<TradeAccountView> list(Boolean enabled, String provider, String keyword, int limit);

	TradeAccountView setEnabled(Long id, boolean enabled, Long operatorUserId);

	TradeAccountConnectivityTestView testConnectivity(Long id);

	TradeAccountStreamStatusView streamStatus(Long id);

	List<TradeAccountStreamStatusView> streamStatuses();
}
