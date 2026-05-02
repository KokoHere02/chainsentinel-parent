package com.chainsentinel.web.auth;

import com.chainsentinel.infra.entity.AuthRefreshTokenEntity;
import com.chainsentinel.infra.entity.AuthUserEntity;
import com.chainsentinel.infra.repository.AuthRefreshTokenRepository;
import com.chainsentinel.infra.repository.AuthUserRepository;
import com.chainsentinel.infra.repository.AuthUserRoleRepository;
import com.chainsentinel.web.auth.audit.AuditEvent;
import com.chainsentinel.web.auth.audit.AuditEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import com.chainsentinel.infra.entity.AuthAuditLogEntity;
import com.chainsentinel.infra.repository.AuthAuditLogRepository;

@Service
public class AuthService {

	private final AuthUserRepository authUserRepository;
	private final AuthUserRoleRepository authUserRoleRepository;
	private final AuthRefreshTokenRepository authRefreshTokenRepository;
	private final AuthAuditLogRepository authAuditLogRepository;
	private final AuditEventPublisher auditEventPublisher;
	private final PasswordPolicyValidator passwordPolicyValidator;
	private final UsernamePolicyValidator usernamePolicyValidator;
	private final JwtTokenService jwtTokenService;
	private final AuthProperties authProperties;
	private final ConcurrentHashMap<String, LoginFailState> loginFailStates = new ConcurrentHashMap<>();

	public AuthService(
		AuthUserRepository authUserRepository,
		AuthUserRoleRepository authUserRoleRepository,
		AuthRefreshTokenRepository authRefreshTokenRepository,
		AuthAuditLogRepository authAuditLogRepository,
		AuditEventPublisher auditEventPublisher,
		PasswordPolicyValidator passwordPolicyValidator,
		UsernamePolicyValidator usernamePolicyValidator,
		JwtTokenService jwtTokenService,
		AuthProperties authProperties
	) {
		this.authUserRepository = authUserRepository;
		this.authUserRoleRepository = authUserRoleRepository;
		this.authRefreshTokenRepository = authRefreshTokenRepository;
		this.authAuditLogRepository = authAuditLogRepository;
		this.auditEventPublisher = auditEventPublisher;
		this.passwordPolicyValidator = passwordPolicyValidator;
		this.usernamePolicyValidator = usernamePolicyValidator;
		this.jwtTokenService = jwtTokenService;
		this.authProperties = authProperties;
	}

	public LoginResult login(String username, String password, HttpServletRequest request, String traceId) {
		String normalizedUsername = usernamePolicyValidator.normalize(username);
		String failKey = buildFailKey(normalizedUsername, request.getRemoteAddr());
		checkLoginLocked(failKey);
		AuthUserEntity user = authUserRepository.findByUsernameAndEnabledTrue(normalizedUsername)
			.orElseThrow(() -> unauthorized("LOGIN_FAIL", null, normalizedUsername, "user_not_found", request, traceId, failKey));
		if (!BCrypt.checkpw(password, user.getPasswordHash())) {
			throw unauthorized("LOGIN_FAIL", user.getId(), user.getUsername(), "password_invalid", request, traceId, failKey);
		}
		Set<AuthRole> roles = authUserRoleRepository.findRoleCodesByUserId(user.getId()).stream()
			.map(AuthRole::valueOf)
			.collect(Collectors.toSet());
		if (roles.isEmpty()) {
			throw unauthorized("LOGIN_FAIL", user.getId(), user.getUsername(), "role_missing", request, traceId, failKey);
		}
		clearFailState(failKey);
		AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getUsername(), roles);
		String token = jwtTokenService.issueToken(principal);
		String refreshToken = createRefreshToken(user.getId(), request);
		audit("LOGIN_SUCCESS", user.getId(), user.getUsername(), "SUCCESS", "", request, traceId);
		return new LoginResult(token, refreshToken, "Bearer", user.getId(), user.getUsername(), roles);
	}

	public LoginResult refresh(String refreshToken, HttpServletRequest request, String traceId) {
		AuthRefreshTokenEntity storedToken = authRefreshTokenRepository.findByTokenIdAndRevokedFalse(refreshToken)
			.orElseThrow(() -> refreshInvalid("REFRESH_FAIL", null, null, "refresh_token_not_found", request, traceId));
		if (storedToken.getExpiresAt().isBefore(Instant.now())) {
			storedToken.setRevoked(true);
			storedToken.setRevokedAt(Instant.now());
			authRefreshTokenRepository.save(storedToken);
			throw refreshInvalid("REFRESH_FAIL", storedToken.getUserId(), null, "refresh_token_expired", request, traceId);
		}

		AuthUserEntity user = authUserRepository.findByIdAndEnabledTrue(storedToken.getUserId())
			.orElseThrow(() -> refreshInvalid("REFRESH_FAIL", storedToken.getUserId(), null, "user_not_found", request, traceId));
		Set<AuthRole> roles = authUserRoleRepository.findRoleCodesByUserId(user.getId()).stream()
			.map(AuthRole::valueOf)
			.collect(Collectors.toSet());
		if (roles.isEmpty()) {
			throw refreshInvalid("REFRESH_FAIL", user.getId(), user.getUsername(), "role_missing", request, traceId);
		}

		storedToken.setRevoked(true);
		storedToken.setRevokedAt(Instant.now());
		authRefreshTokenRepository.save(storedToken);

		AuthPrincipal principal = new AuthPrincipal(user.getId(), user.getUsername(), roles);
		String newAccessToken = jwtTokenService.issueToken(principal);
		String newRefreshToken = createRefreshToken(user.getId(), request);
		audit("REFRESH_SUCCESS", user.getId(), user.getUsername(), "SUCCESS", "", request, traceId);
		return new LoginResult(newAccessToken, newRefreshToken, "Bearer", user.getId(), user.getUsername(), roles);
	}

	public void logout(String refreshToken, HttpServletRequest request, String traceId) {
		AuthRefreshTokenEntity storedToken = authRefreshTokenRepository.findByTokenIdAndRevokedFalse(refreshToken)
			.orElseThrow(() -> refreshInvalid("LOGOUT_FAIL", null, null, "refresh_token_not_found", request, traceId));
		storedToken.setRevoked(true);
		storedToken.setRevokedAt(Instant.now());
		authRefreshTokenRepository.save(storedToken);
		audit("LOGOUT_SUCCESS", storedToken.getUserId(), null, "SUCCESS", "", request, traceId);
	}

	public List<SessionView> listActiveSessions(Long userId) {
		return authRefreshTokenRepository.findByUserIdAndRevokedFalseAndExpiresAtAfter(userId, Instant.now()).stream()
			.map(token -> new SessionView(
				maskTokenId(token.getTokenId()),
				token.getTokenId(),
				token.getIssuedIp(),
				token.getIssuedUa(),
				token.getCreatedAt(),
				token.getExpiresAt()
			))
			.toList();
	}

	public void revokeSession(Long userId, String tokenId, HttpServletRequest request, String traceId) {
		AuthRefreshTokenEntity token = authRefreshTokenRepository.findByUserIdAndTokenIdAndRevokedFalse(userId, tokenId)
			.orElseThrow(() -> refreshInvalid("SESSION_REVOKE_FAIL", userId, null, "session_not_found", request, traceId));
		token.setRevoked(true);
		token.setRevokedAt(Instant.now());
		authRefreshTokenRepository.save(token);
		audit("SESSION_REVOKE_SUCCESS", userId, null, "SUCCESS", "", request, traceId);
	}

	public int revokeAllSessions(Long userId, HttpServletRequest request, String traceId) {
		List<AuthRefreshTokenEntity> tokens = authRefreshTokenRepository.findByUserIdAndRevokedFalseAndExpiresAtAfter(userId, Instant.now());
		for (AuthRefreshTokenEntity token : tokens) {
			token.setRevoked(true);
			token.setRevokedAt(Instant.now());
		}
		authRefreshTokenRepository.saveAll(tokens);
		audit("SESSION_REVOKE_ALL_SUCCESS", userId, null, "SUCCESS", "count=" + tokens.size(), request, traceId);
		return tokens.size();
	}

	public MeView me(Long userId) {
		AuthUserEntity user = getUserOrThrow(userId);
		Set<AuthRole> roles = getRoles(userId);
		int activeSessionCount = authRefreshTokenRepository.findByUserIdAndRevokedFalseAndExpiresAtAfter(userId, Instant.now()).size();
		return new MeView(user.getId(), user.getUsername(), Boolean.TRUE.equals(user.getEnabled()), roles, activeSessionCount);
	}

	public void changePassword(
		Long userId,
		String currentPassword,
		String newPassword,
		HttpServletRequest request,
		String traceId
	) {
		AuthUserEntity user = getUserOrThrow(userId);
		if (!BCrypt.checkpw(currentPassword, user.getPasswordHash())) {
			audit("PASSWORD_CHANGE_FAIL", userId, user.getUsername(), "FAIL", "current_password_invalid", request, traceId);
			throw new AuthException(AuthErrorCode.AUTH_PASSWORD_INVALID, HttpStatus.UNAUTHORIZED, "Current password is invalid");
		}
		passwordPolicyValidator.validate(newPassword);
		user.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
		authUserRepository.save(user);
		int revokedCount = revokeAllSessions(userId, request, traceId);
		audit("PASSWORD_CHANGE_SUCCESS", userId, user.getUsername(), "SUCCESS", "revoked_sessions=" + revokedCount, request, traceId);
	}

	public List<AuditLogView> listMyAuditLogs(Long userId, int limit) {
		List<AuthAuditLogEntity> logs = limit <= 50
			? authAuditLogRepository.findTop50ByUserIdOrderByIdDesc(userId)
			: authAuditLogRepository.findTop200ByUserIdOrderByIdDesc(userId);
		return logs.stream()
			.limit(limit)
			.map(log -> new AuditLogView(
				log.getAction(),
				log.getResult(),
				log.getReason(),
				log.getTraceId(),
				log.getRequestIp(),
				log.getRequestPath(),
				log.getRequestMethod(),
				log.getCreatedAt()
			))
			.toList();
	}

	private AuthException unauthorized(
		String action,
		Long userId,
		String username,
		String reason,
		HttpServletRequest request,
		String traceId,
		String failKey
	) {
		audit(action, userId, username, "FAIL", reason, request, traceId);
		recordLoginFailure(failKey);
		return new AuthException(
			AuthErrorCode.AUTH_INVALID_CREDENTIALS,
			HttpStatus.UNAUTHORIZED,
			"Invalid username or password"
		);
	}

	private AuthException refreshInvalid(
		String action,
		Long userId,
		String username,
		String reason,
		HttpServletRequest request,
		String traceId
	) {
		audit(action, userId, username, "FAIL", reason, request, traceId);
		return new AuthException(
			AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID,
			HttpStatus.UNAUTHORIZED,
			"Invalid refresh token"
		);
	}

	private void audit(
		String action,
		Long userId,
		String username,
		String result,
		String reason,
		HttpServletRequest request,
		String traceId
	) {
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

	private String createRefreshToken(Long userId, HttpServletRequest request) {
		String tokenId = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
		AuthRefreshTokenEntity token = new AuthRefreshTokenEntity();
		token.setUserId(userId);
		token.setTokenId(tokenId);
		token.setRevoked(false);
		token.setIssuedIp(request.getRemoteAddr());
		token.setIssuedUa(request.getHeader("User-Agent"));
		token.setExpiresAt(Instant.now().plusSeconds(authProperties.getRefreshTokenTtlSeconds()));
		authRefreshTokenRepository.save(token);
		return tokenId;
	}

	private void checkLoginLocked(String failKey) {
		LoginFailState state = loginFailStates.get(failKey);
		if (state == null) {
			return;
		}
		long now = Instant.now().getEpochSecond();
		if (state.lockedUntilEpochSecond > now) {
			throw new AuthException(
				AuthErrorCode.AUTH_LOGIN_LOCKED,
				HttpStatus.TOO_MANY_REQUESTS,
				"Login temporarily locked due to too many failed attempts"
			);
		}
	}

	private void recordLoginFailure(String failKey) {
		long now = Instant.now().getEpochSecond();
		loginFailStates.compute(failKey, (k, oldState) -> {
			LoginFailState state = oldState == null ? new LoginFailState(0, now, 0) : oldState;
			if (now - state.windowStartEpochSecond >= authProperties.getLoginFailWindowSeconds()) {
				state = new LoginFailState(0, now, 0);
			}
			int nextCount = state.failCount + 1;
			long lockUntil = state.lockedUntilEpochSecond;
			if (nextCount >= authProperties.getLoginFailMaxAttempts()) {
				lockUntil = now + authProperties.getLoginLockSeconds();
			}
			return new LoginFailState(nextCount, state.windowStartEpochSecond, lockUntil);
		});
	}

	private void clearFailState(String failKey) {
		loginFailStates.remove(failKey);
	}

	private String buildFailKey(String username, String ip) {
		String normalizedUser = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
		String normalizedIp = ip == null ? "unknown" : ip.trim().toLowerCase(Locale.ROOT);
		return normalizedUser + "|" + normalizedIp;
	}

	private String maskTokenId(String tokenId) {
		if (tokenId == null || tokenId.isBlank()) {
			return "";
		}
		if (tokenId.length() <= 12) {
			return "****";
		}
		return tokenId.substring(0, 6) + "****" + tokenId.substring(tokenId.length() - 4);
	}

	private AuthUserEntity getUserOrThrow(Long userId) {
		return authUserRepository.findById(userId)
			.orElseThrow(() -> new AuthException(AuthErrorCode.AUTH_USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
	}

	private Set<AuthRole> getRoles(Long userId) {
		return authUserRoleRepository.findRoleCodesByUserId(userId).stream()
			.map(AuthRole::valueOf)
			.collect(Collectors.toSet());
	}

	private record LoginFailState(int failCount, long windowStartEpochSecond, long lockedUntilEpochSecond) {
	}

	public record LoginResult(
		String accessToken,
		String refreshToken,
		String tokenType,
		Long userId,
		String username,
		Set<AuthRole> roles
	) {
	}

	public record SessionView(
		String tokenId,
		String revokeTokenId,
		String issuedIp,
		String issuedUa,
		Instant createdAt,
		Instant expiresAt
	) {
	}

	public record MeView(
		Long userId,
		String username,
		boolean enabled,
		Set<AuthRole> roles,
		int activeSessionCount
	) {
	}

	public record AuditLogView(
		String action,
		String result,
		String reason,
		String traceId,
		String requestIp,
		String requestPath,
		String requestMethod,
		Instant createdAt
	) {
	}
}
