package com.chainsentinel.web.auth;

import com.chainsentinel.web.api.support.RequestTraceFilter;
import com.chainsentinel.web.auth.audit.AuditEvent;
import com.chainsentinel.web.auth.audit.AuditEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

	private final JwtTokenService jwtTokenService;
	private final AuditEventPublisher auditEventPublisher;

	public AuthInterceptor(JwtTokenService jwtTokenService, AuditEventPublisher auditEventPublisher) {
		this.jwtTokenService = jwtTokenService;
		this.auditEventPublisher = auditEventPublisher;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if (!(handler instanceof HandlerMethod handlerMethod)) {
			return true;
		}
		RequireRoles requireRoles = resolveConfig(handlerMethod);
		if (requireRoles == null) {
			return true;
		}
		String token = resolveBearerToken(request);
		AuthPrincipal principal;
		try {
			principal = jwtTokenService.verifyAndParse(token);
		} catch (IllegalArgumentException ex) {
			publishDenyEvent(request, "AUTH_TOKEN_INVALID", "FAIL", "invalid_token", null, null);
			throw new AuthException(AuthErrorCode.AUTH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "Invalid bearer token");
		}
		AuthContext.set(principal);
		AuthRole[] required = requireRoles.value();
		if (required.length == 0) {
			return true;
		}
		Set<AuthRole> roleSet = principal.roles();
		boolean allowed = Arrays.stream(required).anyMatch(roleSet::contains);
		if (!allowed) {
			publishDenyEvent(
				request,
				"AUTH_ROLE_FORBIDDEN",
				"FAIL",
				"insufficient_role",
				principal.userId(),
				principal.username()
			);
			throw new AuthException(AuthErrorCode.AUTH_ROLE_FORBIDDEN, HttpStatus.FORBIDDEN, "Insufficient role");
		}
		return true;
	}

	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
		AuthContext.clear();
	}

	private RequireRoles resolveConfig(HandlerMethod method) {
		RequireRoles onMethod = method.getMethodAnnotation(RequireRoles.class);
		if (onMethod != null) {
			return onMethod;
		}
		return method.getBeanType().getAnnotation(RequireRoles.class);
	}

	private String resolveBearerToken(HttpServletRequest request) {
		String authorization = request.getHeader("Authorization");
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			publishDenyEvent(request, "AUTH_TOKEN_MISSING", "FAIL", "missing_bearer_token", null, null);
			throw new AuthException(AuthErrorCode.AUTH_TOKEN_MISSING, HttpStatus.UNAUTHORIZED, "Missing bearer token");
		}
		String token = authorization.substring(7).trim();
		if (token.isEmpty()) {
			publishDenyEvent(request, "AUTH_TOKEN_MISSING", "FAIL", "empty_bearer_token", null, null);
			throw new AuthException(AuthErrorCode.AUTH_TOKEN_MISSING, HttpStatus.UNAUTHORIZED, "Missing bearer token");
		}
		return token;
	}

	private void publishDenyEvent(
		HttpServletRequest request,
		String action,
		String result,
		String reason,
		Long userId,
		String username
	) {
		Object traceValue = request.getAttribute(RequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
		String traceId = traceValue == null ? "-" : String.valueOf(traceValue);
		auditEventPublisher.publish(new AuditEvent(
			action,
			userId,
			username,
			result,
			reason,
			traceId,
			request.getRemoteAddr(),
			request.getRequestURI(),
			request.getMethod()
		));
	}
}
