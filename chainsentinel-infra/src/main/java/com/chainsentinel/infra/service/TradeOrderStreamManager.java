package com.chainsentinel.infra.service;

public interface TradeOrderStreamManager {

	void syncAccount(Long accountId);

	void disconnectAccount(Long accountId);

	TradeOrderStreamStatus status(Long accountId);

	java.util.List<TradeOrderStreamStatus> statuses();
}
