package com.chainsentinel.web.auth.audit;

import com.chainsentinel.infra.entity.AuthAuditLogEntity;
import com.chainsentinel.infra.repository.AuthAuditLogRepository;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class AuthAuditEventListener {

	private final AuthAuditLogRepository authAuditLogRepository;

	public AuthAuditEventListener(AuthAuditLogRepository authAuditLogRepository) {
		this.authAuditLogRepository = authAuditLogRepository;
	}

	@Async("auditEventExecutor")
	@EventListener
	public void handle(AuditEvent event) {
		AuthAuditLogEntity log = new AuthAuditLogEntity();
		log.setAction(event.action());
		log.setUserId(event.userId());
		log.setUsername(event.username());
		log.setResult(event.result());
		log.setReason(event.reason());
		log.setTraceId(event.traceId());
		log.setRequestIp(event.requestIp());
		log.setRequestPath(event.requestPath());
		log.setRequestMethod(event.requestMethod());
		authAuditLogRepository.save(log);
	}
}
