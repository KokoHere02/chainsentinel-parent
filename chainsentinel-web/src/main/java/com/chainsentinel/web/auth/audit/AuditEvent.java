package com.chainsentinel.web.auth.audit;

public record AuditEvent(
	String action,
	Long userId,
	String username,
	String result,
	String reason,
	String traceId,
	String requestIp,
	String requestPath,
	String requestMethod
) {
}
