package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.PriceProviderConfigCreateCommand;
import com.chainsentinel.core.service.dto.PriceProviderConfigUpdateCommand;
import com.chainsentinel.core.service.dto.PriceProviderConfigView;
import java.util.List;

public interface PriceProviderConfigService {

	PriceProviderConfigView create(PriceProviderConfigCreateCommand command);

	PriceProviderConfigView update(Long id, PriceProviderConfigUpdateCommand command);

	void delete(Long id);

	PriceProviderConfigView get(Long id);

	List<PriceProviderConfigView> list(Boolean enabled, String keyword, int limit);

	PriceProviderConfigView setEnabled(Long id, boolean enabled);
}
