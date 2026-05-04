package com.chainsentinel.infra.repository;

import java.util.List;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.infra.entity.AlertRuleEntity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertRuleRepository extends JpaRepository<AlertRuleEntity, Long> {

	List<AlertRuleEntity> findByTypeAndEnabledTrue(AlertRuleType type);

	List<AlertRuleEntity> findByEnabledTrue();

	@Query("""
		select r
		from AlertRuleEntity r
		where (:type is null or r.type = :type)
		  and (:enabled is null or r.enabled = :enabled)
		  and (:keyword is null or lower(r.name) like concat('%', :keyword, '%'))
		order by r.id desc
		""")
	List<AlertRuleEntity> listByFilters(
		@Param("type") AlertRuleType type,
		@Param("enabled") Boolean enabled,
		@Param("keyword") String keyword,
		Pageable pageable
	);

	@Query("""
		select count(r)
		from AlertRuleEntity r
		where r.enabled = true
		""")
	long countEnabled();
}
