package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.TradePositionSnapshotEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface TradePositionSnapshotRepository extends JpaRepository<TradePositionSnapshotEntity, Long> {

	@Query("""
		select max(t.snapshotTime)
		from TradePositionSnapshotEntity t
		where t.accountId = :accountId
		""")
	Instant findLatestSnapshotTimeByAccountId(@Param("accountId") Long accountId);

	List<TradePositionSnapshotEntity> findByAccountIdAndSnapshotTimeOrderBySymbolAsc(Long accountId, Instant snapshotTime);

	TradePositionSnapshotEntity findTopByAccountIdAndSymbolOrderBySnapshotTimeDescIdDesc(Long accountId, String symbol);
}
