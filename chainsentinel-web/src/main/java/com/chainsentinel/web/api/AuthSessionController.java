package com.chainsentinel.web.api;

import com.chainsentinel.web.api.support.RequestTraceFilter;
import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import com.chainsentinel.web.auth.AuthContext;
import com.chainsentinel.web.auth.AuthErrorCode;
import com.chainsentinel.web.auth.AuthException;
import com.chainsentinel.web.auth.AuthPrincipal;
import com.chainsentinel.web.auth.AuthService;
import com.chainsentinel.web.auth.RequireRoles;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth/sessions")
@Validated
@RequireRoles
public class AuthSessionController {

	private final AuthService authService;

	public AuthSessionController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping
	@RateLimit(name = "auth.session.list", permits = 30, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many session list requests")
	public List<AuthService.SessionView> list(HttpServletRequest request) {
		return authService.listActiveSessions(currentUserId(request));
	}

	@DeleteMapping("/{tokenId}")
	@RateLimit(name = "auth.session.revokeOne", permits = 20, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many session revoke requests")
	public SessionRevokeResponse revokeOne(@PathVariable("tokenId") String tokenId, HttpServletRequest request) {
		String traceId = traceId(request);
		Long userId = currentUserId(request);
		authService.revokeSession(userId, tokenId, request, traceId);
		return new SessionRevokeResponse(1);
	}

	@DeleteMapping
	@RateLimit(name = "auth.session.revokeAll", permits = 10, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many session revoke-all requests")
	public SessionRevokeResponse revokeAll(HttpServletRequest request) {
		String traceId = traceId(request);
		Long userId = currentUserId(request);
		int count = authService.revokeAllSessions(userId, request, traceId);
		return new SessionRevokeResponse(count);
	}

	private Long currentUserId(HttpServletRequest request) {
		AuthPrincipal principal = AuthContext.get();
		if (principal == null || principal.userId() == null) {
			throw new AuthException(AuthErrorCode.AUTH_TOKEN_INVALID, HttpStatus.UNAUTHORIZED, "Invalid bearer token");
		}
		return principal.userId();
	}

	private String traceId(HttpServletRequest request) {
		Object traceValue = request.getAttribute(RequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
		return traceValue == null ? "-" : String.valueOf(traceValue);
	}

	public record SessionRevokeResponse(int revokedCount) {
	}
}
