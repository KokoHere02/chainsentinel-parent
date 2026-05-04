package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.TradeOrderEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeOrderRepository extends JpaRepository<TradeOrderEntity, Long> {

	Optional<TradeOrderEntity> findByClientOrderId(String clientOrderId);

	Optional<TradeOrderEntity> findByProviderAndProviderOrderId(String provider, String providerOrderId);

	Optional<TradeOrderEntity> findByAccountIdAndClientOrderId(Long accountId, String clientOrderId);

	@Query("""
		select t
		from TradeOrderEntity t
		where (:accountId is null or t.accountId = :accountId)
		  and (:status is null or t.status = :status)
		  and (:symbol is null or lower(t.symbol) = :symbol)
		order by t.id desc
		""")
	List<TradeOrderEntity> listByFilters(
		@Param("accountId") Long accountId,
		@Param("status") String status,
		@Param("symbol") String symbol,
		Pageable pageable
	);
}
