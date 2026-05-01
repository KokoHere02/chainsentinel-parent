package com.chainsentinel.web.auth.audit;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class AuditEventPublisher {

	private final ApplicationEventPublisher applicationEventPublisher;

	public AuditEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	public void publish(AuditEvent event) {
		applicationEventPublisher.publishEvent(event);
	}
}
