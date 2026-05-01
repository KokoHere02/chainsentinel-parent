package com.chainsentinel.web.api;

import com.chainsentinel.web.api.support.RequestTraceFilter;
import com.chainsentinel.web.api.support.ratelimit.RateLimit;
import com.chainsentinel.web.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	@RateLimit(name = "auth.login", permits = 20, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many login requests")
	public AuthService.LoginResult login(
		@RequestBody @Valid LoginRequest request,
		HttpServletRequest httpServletRequest
	) {
		Object traceValue = httpServletRequest.getAttribute(RequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
		String traceId = traceValue == null ? "-" : String.valueOf(traceValue);
		return authService.login(request.username(), request.password(), httpServletRequest, traceId);
	}

	@PostMapping("/refresh")
	@RateLimit(name = "auth.refresh", permits = 30, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many refresh requests")
	public AuthService.LoginResult refresh(
		@RequestBody @Valid RefreshRequest request,
		HttpServletRequest httpServletRequest
	) {
		Object traceValue = httpServletRequest.getAttribute(RequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
		String traceId = traceValue == null ? "-" : String.valueOf(traceValue);
		return authService.refresh(request.refreshToken(), httpServletRequest, traceId);
	}

	@PostMapping("/logout")
	@RateLimit(name = "auth.logout", permits = 30, windowSeconds = 60, scope = RateLimit.Scope.IP, message = "Too many logout requests")
	public ResponseEntity<Void> logout(
		@RequestBody @Valid RefreshRequest request,
		HttpServletRequest httpServletRequest
	) {
		Object traceValue = httpServletRequest.getAttribute(RequestTraceFilter.REQUEST_ATTR_REQUEST_ID);
		String traceId = traceValue == null ? "-" : String.valueOf(traceValue);
		authService.logout(request.refreshToken(), httpServletRequest, traceId);
		return ResponseEntity.noContent().build();
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password) {
	}

	public record RefreshRequest(@NotBlank String refreshToken) {
	}
}
