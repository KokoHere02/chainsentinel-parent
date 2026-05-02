package com.chainsentinel.web.auth.audit;

import com.chainsentinel.infra.entity.AuthAuditLogEntity;
import com.chainsentinel.infra.repository.AuthAuditLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AuthAuditEventListener {

	private static final Logger log = LoggerFactory.getLogger(AuthAuditEventListener.class);
	private static final String METRIC_AUDIT_EVENT_TOTAL = "chainsentinel_auth_audit_event_total";

	private final AuthAuditLogRepository authAuditLogRepository;
	private final MeterRegistry meterRegistry;

	public AuthAuditEventListener(AuthAuditLogRepository authAuditLogRepository, MeterRegistry meterRegistry) {
		this.authAuditLogRepository = authAuditLogRepository;
		this.meterRegistry = meterRegistry;
	}

	@Async("auditEventExecutor")
	@EventListener
	public void handle(AuditEvent event) {
		try {
			AuthAuditLogEntity logEntity = new AuthAuditLogEntity();
			logEntity.setAction(event.action());
			logEntity.setUserId(event.userId());
			logEntity.setUsername(event.username());
			logEntity.setResult(event.result());
			logEntity.setReason(event.reason());
			logEntity.setTraceId(event.traceId());
			logEntity.setRequestIp(event.requestIp());
			logEntity.setRequestPath(event.requestPath());
			logEntity.setRequestMethod(event.requestMethod());
			authAuditLogRepository.save(logEntity);
			meterRegistry.counter(METRIC_AUDIT_EVENT_TOTAL, "status", "success", "action", safeTag(event.action())).increment();
		} catch (Exception ex) {
			meterRegistry.counter(METRIC_AUDIT_EVENT_TOTAL, "status", "failed", "action", safeTag(event.action())).increment();
			log.error(
				"auth.audit.persist.failed action={} userId={} username={} traceId={} path={} method={} reason={}",
				event.action(),
				event.userId(),
				event.username(),
				event.traceId(),
				event.requestPath(),
				event.requestMethod(),
				event.reason(),
				ex
			);
		}
	}

	private String safeTag(String value) {
		if (value == null || value.isBlank()) {
			return "UNKNOWN";
		}
		return value;
	}
}
