package com.chainsentinel.web.api;

import com.chainsentinel.web.api.support.RequestTraceFilter;
import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import com.chainsentinel.web.auth.AuthContext;
import com.chainsentinel.web.auth.AuthErrorCode;
import com.chainsentinel.web.auth.AuthException;
import com.chainsentinel.web.auth.AuthPrincipal;
import com.chainsentinel.web.auth.AuthService;
import com.chainsentinel.web.auth.RequireRoles;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
@RequireRoles
public class AuthProfileController {

	private final AuthService authService;

	public AuthProfileController(AuthService authService) {
		this.authService = authService;
	}

	@GetMapping("/me")
	@RateLimit(name = "auth.me", permits = 60, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many me requests")
	public AuthService.MeView me(HttpServletRequest request) {
		return authService.me(currentUserId());
	}

	@PatchMapping("/password")
	@RateLimit(name = "auth.password", permits = 10, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many password change requests")
	public ResponseEntity<Void> changePassword(
		@RequestBody @Valid ChangePasswordRequest request,
		HttpServletRequest httpRequest
	) {
		authService.changePassword(currentUserId(), request.currentPassword(), request.newPassword(), httpRequest, traceId(httpRequest));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/logout-all")
	@RateLimit(name = "auth.logoutAll", permits = 10, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many logout-all requests")
	public AuthSessionController.SessionRevokeResponse logoutAll(HttpServletRequest request) {
		int count = authService.revokeAllSessions(currentUserId(), request, traceId(request));
		return new AuthSessionController.SessionRevokeResponse(count);
	}

	@GetMapping("/audit")
	@RateLimit(name = "auth.audit", permits = 30, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many audit requests")
	public List<AuthService.AuditLogView> audit(
		@RequestParam(name = "limit", defaultValue = "50") @Min(1) @Max(200) int limit
	) {
		return authService.listMyAuditLogs(currentUserId(), limit);
	}

	private Long currentUserId() {
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

	public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
	}
}
