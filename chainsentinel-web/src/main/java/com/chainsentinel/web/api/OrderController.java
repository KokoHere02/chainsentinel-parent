package com.chainsentinel.web.api;

import com.chainsentinel.web.api.support.RequestTraceFilter;
import com.chainsentinel.web.auth.AuthContext;
import com.chainsentinel.web.auth.AuthPrincipal;
import com.chainsentinel.web.auth.AuthRole;
import com.chainsentinel.web.auth.RequireRoles;
import com.chainsentinel.web.auth.audit.AuditEvent;
import com.chainsentinel.web.auth.audit.AuditEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

	private final AuditEventPublisher auditEventPublisher;

	public OrderController(AuditEventPublisher auditEventPublisher) {
		this.auditEventPublisher = auditEventPublisher;
	}

	@PostMapping
	@RequireRoles({ AuthRole.ADMIN, AuthRole.TRADER })
	public OrderCreateResponse create(@RequestBody @Valid OrderCreateRequest request, HttpServletRequest httpRequest) {
		AuthPrincipal principal = AuthContext.get();
		String orderId = UUID.randomUUID().toString().replace("-", "");
		Object traceValue = httpRequest.getAttribute(RequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
		String traceId = traceValue == null ? "-" : String.valueOf(traceValue);
		auditEventPublisher.publish(new AuditEvent(
			"ORDER_CREATE_SUCCESS",
			principal == null ? null : principal.userId(),
			principal == null ? null : principal.username(),
			"SUCCESS",
			request.symbol() + "|" + request.side(),
			traceId,
			httpRequest.getRemoteAddr(),
			httpRequest.getRequestURI(),
			httpRequest.getMethod()
		));
		return new OrderCreateResponse(
			orderId,
			request.symbol(),
			request.side(),
			request.quantity(),
			principal == null ? null : principal.userId(),
			Instant.now()
		);
	}

	public record OrderCreateRequest(
		@NotBlank String symbol,
		@NotBlank String side,
		@NotNull BigDecimal quantity
	) {
	}

	public record OrderCreateResponse(
		String orderId,
		String symbol,
		String side,
		BigDecimal quantity,
		Long operatorUserId,
		Instant createdAt
	) {
	}
}
