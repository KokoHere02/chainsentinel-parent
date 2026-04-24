package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.AddressTokenHoldingView;
import java.util.List;

public interface AddressHoldingQueryService {

	List<AddressTokenHoldingView> list(String chain, String network, String address, int limit);
}

