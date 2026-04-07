package com.chainsentinel.infra.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.chainsentinel.core.model.AlertRuleType;
import com.chainsentinel.infra.entity.AlertRuleEntity;
import com.chainsentinel.infra.repository.AlertEventRepository;
import com.chainsentinel.infra.repository.AlertRuleRepository;
import com.chainsentinel.infra.repository.projection.RuleHitCountProjection;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RuleHitStatsServiceTest {

  @Mock
  private AlertRuleRepository alertRuleRepository;

  @Mock
  private AlertEventRepository alertEventRepository;

  @Test
  void shouldReturnRuleStatsWithZeroFallback() {
    RuleHitStatsService service = new RuleHitStatsService(alertRuleRepository, alertEventRepository);

    AlertRuleEntity rule1 = new AlertRuleEntity();
    ReflectionTestUtils.setField(rule1, "id", 1L);
    rule1.setName("r1");
    rule1.setType(AlertRuleType.PRICE_THRESHOLD);
    rule1.setEnabled(true);

    AlertRuleEntity rule2 = new AlertRuleEntity();
    ReflectionTestUtils.setField(rule2, "id", 2L);
    rule2.setName("r2");
    rule2.setType(AlertRuleType.ADDRESS);
    rule2.setEnabled(true);

    when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(rule1, rule2));
    when(alertEventRepository.countHitsByRuleSince(org.mockito.ArgumentMatchers.any(Instant.class)))
      .thenReturn(
        List.of(new Row(1L, 5L)),
        List.of(new Row(1L, 9L), new Row(2L, 3L))
      );

    List<RuleHitStatsService.RuleHitStatsView> views = service.list(true);

    assertEquals(2, views.size());
    RuleHitStatsService.RuleHitStatsView first = views.get(0);
    RuleHitStatsService.RuleHitStatsView second = views.get(1);

    assertEquals(2L, first.ruleId());
    assertEquals(0L, first.hitCount24h());
    assertEquals(3L, first.hitCount7d());

    assertEquals(1L, second.ruleId());
    assertEquals(5L, second.hitCount24h());
    assertEquals(9L, second.hitCount7d());
  }

  private static final class Row implements RuleHitCountProjection {
    private final Long ruleId;
    private final Long hitCount;

    private Row(Long ruleId, Long hitCount) {
      this.ruleId = ruleId;
      this.hitCount = hitCount;
    }

    @Override
    public Long getRuleId() {
      return ruleId;
    }

    @Override
    public Long getHitCount() {
      return hitCount;
    }
  }
}
