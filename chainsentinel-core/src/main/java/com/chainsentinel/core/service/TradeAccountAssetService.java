package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.TradeAccountAssetSyncView;
import com.chainsentinel.core.service.dto.TradeAccountBalanceSnapshotView;
import com.chainsentinel.core.service.dto.TradePositionSnapshotView;
import java.util.List;

public interface TradeAccountAssetService {

	TradeAccountAssetSyncView sync(Long accountId, Long operatorUserId);

	List<TradeAccountBalanceSnapshotView> listLatestBalances(Long accountId);

	List<TradePositionSnapshotView> listLatestPositions(Long accountId);
}
