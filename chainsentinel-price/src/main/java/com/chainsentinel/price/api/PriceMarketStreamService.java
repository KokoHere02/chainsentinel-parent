package com.chainsentinel.price.api;

import com.chainsentinel.price.api.dto.PriceMarketSubscription;
import java.util.List;

public interface PriceMarketStreamService {

	void refreshSubscriptions(List<PriceMarketSubscription> subscriptions);
}
