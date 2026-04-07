package com.chainsentinel.infra.repository.projection;

public interface AlertFailureSummaryProjection {

	String getSendStatus();

	String getLastError();

	Long getFailureCount();
}
