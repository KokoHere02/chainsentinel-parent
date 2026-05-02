package com.chainsentinel.web.auth.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import com.chainsentinel.infra.repository.AuthAuditLogRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthAuditEventListenerTest {

	@Mock
	private AuthAuditLogRepository authAuditLogRepository;

	private SimpleMeterRegistry meterRegistry;
	private AuthAuditEventListener listener;

	@BeforeEach
	void setUp() {
		meterRegistry = new SimpleMeterRegistry();
		listener = new AuthAuditEventListener(authAuditLogRepository, meterRegistry);
	}

	@Test
	void shouldPersistAuditEventAndIncrementSuccessMetric() {
		listener.handle(new AuditEvent(
			"LOGIN_SUCCESS",
			1L,
			"admin",
			"SUCCESS",
			"",
			"t1",
			"127.0.0.1",
			"/api/auth/login",
			"POST"
		));

		verify(authAuditLogRepository).save(any());
		Assertions.assertEquals(
			1.0,
			meterRegistry.get("chainsentinel_auth_audit_event_total")
				.tag("status", "success")
				.tag("action", "LOGIN_SUCCESS")
				.counter()
				.count()
		);
	}

	@Test
	void shouldCountFailureWhenPersistThrows() {
		doThrow(new RuntimeException("db down")).when(authAuditLogRepository).save(any());

		listener.handle(new AuditEvent(
			"LOGIN_FAIL",
			1L,
			"admin",
			"FAIL",
			"password_invalid",
			"t2",
			"127.0.0.1",
			"/api/auth/login",
			"POST"
		));

		Assertions.assertEquals(
			1.0,
			meterRegistry.get("chainsentinel_auth_audit_event_total")
				.tag("status", "failed")
				.tag("action", "LOGIN_FAIL")
				.counter()
				.count()
		);
	}
}
