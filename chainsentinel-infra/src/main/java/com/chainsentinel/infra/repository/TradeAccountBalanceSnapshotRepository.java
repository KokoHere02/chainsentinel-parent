package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.TradeAccountBalanceSnapshotEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface TradeAccountBalanceSnapshotRepository extends JpaRepository<TradeAccountBalanceSnapshotEntity, Long> {

	@Query("""
		select max(t.snapshotTime)
		from TradeAccountBalanceSnapshotEntity t
		where t.accountId = :accountId
		""")
	Instant findLatestSnapshotTimeByAccountId(@Param("accountId") Long accountId);

	List<TradeAccountBalanceSnapshotEntity> findByAccountIdAndSnapshotTimeOrderByAssetAsc(Long accountId, Instant snapshotTime);

	TradeAccountBalanceSnapshotEntity findTopByAccountIdAndAssetOrderBySnapshotTimeDescIdDesc(Long accountId, String asset);
}
