package com.chainsentinel.infra.repository.projection;

public interface RuleHitCountProjection {

	Long getRuleId();

	Long getHitCount();
}
