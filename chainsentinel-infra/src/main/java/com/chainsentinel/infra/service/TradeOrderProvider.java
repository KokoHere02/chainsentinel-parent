package com.chainsentinel.infra.service;

import com.chainsentinel.core.service.dto.TradeOrderCreateCommand;
import com.chainsentinel.infra.entity.TradeAccountEntity;
import com.chainsentinel.infra.entity.TradeOrderEntity;

public interface TradeOrderProvider {

	String provider();

	TradeProviderSubmitResult submit(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		TradeOrderCreateCommand command
	);

	TradeProviderCancelResult cancel(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		TradeOrderEntity order
	);

	TradeProviderOrderState queryOrder(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		TradeOrderEntity order
	);

	java.util.List<TradeProviderFillState> listFills(
		TradeAccountEntity account,
		String apiSecret,
		String passphrase,
		TradeOrderEntity order
	);
}
