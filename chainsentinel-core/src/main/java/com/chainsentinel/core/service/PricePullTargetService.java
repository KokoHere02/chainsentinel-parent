package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.PricePullTargetCreateCommand;
import com.chainsentinel.core.service.dto.PricePullTargetUpdateCommand;
import com.chainsentinel.core.service.dto.PricePullTargetView;
import java.util.List;

public interface PricePullTargetService {

	PricePullTargetView create(PricePullTargetCreateCommand command);

	PricePullTargetView update(Long id, PricePullTargetUpdateCommand command);

	void delete(Long id);

	PricePullTargetView get(Long id);

	List<PricePullTargetView> list(Long providerConfigId, Boolean enabled, String keyword, int limit);

	PricePullTargetView setEnabled(Long id, boolean enabled);
}