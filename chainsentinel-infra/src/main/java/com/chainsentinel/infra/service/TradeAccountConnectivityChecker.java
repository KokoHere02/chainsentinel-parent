package com.chainsentinel.infra.service;

import com.chainsentinel.infra.entity.TradeAccountEntity;

public interface TradeAccountConnectivityChecker {

	String provider();

	TradeConnectivityCheckResult test(TradeAccountEntity account, String apiSecret, String passphrase);
}
