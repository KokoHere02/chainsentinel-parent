package com.chainsentinel.core.service;

import com.chainsentinel.core.service.dto.PriceSnapshotUpsertCommand;
import com.chainsentinel.core.service.dto.PriceSnapshotView;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PriceSnapshotService {

	PriceSnapshotView upsertMinuteSnapshot(PriceSnapshotUpsertCommand command);

	Optional<PriceSnapshotView> findLatestByAssetId(Long assetId);

	List<PriceSnapshotView> findRecentByProviderAndInstId(
		String providerName,
		String instId,
		LocalDateTime from,
		LocalDateTime to,
		int limit
	);
}
