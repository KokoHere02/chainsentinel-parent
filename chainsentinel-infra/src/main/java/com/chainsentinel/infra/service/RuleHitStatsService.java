package com.chainsentinel.infra.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.projection.RuleHitCountProjection;

import org.springframework.stereotype.Service;

@Service
public class RuleHitStatsService {

	private final AlertRuleRepository alertRuleRepository;
	private final AlertEventRepository alertEventRepository;

	public RuleHitStatsService(AlertRuleRepository alertRuleRepository, AlertEventRepository alertEventRepository) {
		this.alertRuleRepository = alertRuleRepository;
		this.alertEventRepository = alertEventRepository;
	}

	public List<RuleHitStatsView> list(boolean enabledOnly) {
		List<AlertRuleEntity> rules = enabledOnly
			? alertRuleRepository.findByEnabledTrue()
			: alertRuleRepository.findAll();
		Map<Long, Long> hit24h = toCountMap(alertEventRepository.countHitsByRuleSince(Instant.now().minus(24,
			ChronoUnit.HOURS)));
		Map<Long, Long> hit7d = toCountMap(alertEventRepository.countHitsByRuleSince(Instant.now().minus(7,
			ChronoUnit.DAYS)));

		return rules.stream()
			.sorted((a, b) -> {
				Long aId = a.getId() == null ? Long.MIN_VALUE : a.getId();
				Long bId = b.getId() == null ? Long.MIN_VALUE : b.getId();
				return bId.compareTo(aId);
			})
			.map(rule -> new RuleHitStatsView(
				rule.getId(),
				rule.getName(),
				rule.getType(),
				Boolean.TRUE.equals(rule.getEnabled()),
				hit24h.getOrDefault(rule.getId(), 0L),
				hit7d.getOrDefault(rule.getId(), 0L)
			))
			.toList();
	}

	private Map<Long, Long> toCountMap(List<RuleHitCountProjection> rows) {
		return rows.stream().collect(Collectors.toMap(RuleHitCountProjection::getRuleId,
			RuleHitCountProjection::getHitCount, Long::sum));
	}

	public record RuleHitStatsView(
		Long ruleId,
		String ruleName,
		AlertRuleType type,
		boolean enabled,
		Long hitCount24h,
		Long hitCount7d
	) {
	}

}
