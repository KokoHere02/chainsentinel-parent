package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.TradeFillEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeFillRepository extends JpaRepository<TradeFillEntity, Long> {

	Optional<TradeFillEntity> findByOrderIdAndProviderFillId(Long orderId, String providerFillId);

	List<TradeFillEntity> findByOrderIdOrderByFilledAtAscIdAsc(Long orderId);

	@Query("""
		select f
		from TradeFillEntity f, TradeOrderEntity o
		where f.orderId = o.id
		  and o.accountId = :accountId
		order by f.filledAt asc, f.id asc
		""")
	List<TradeFillEntity> listByAccountIdOrderByFilledAtAscIdAsc(@Param("accountId") Long accountId);
}
