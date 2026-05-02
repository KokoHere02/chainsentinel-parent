package com.chainsentinel.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.entity.AuthAuditLogEntity;
import com.chainsentinel.infra.entity.AuthRefreshTokenEntity;
import com.chainsentinel.infra.entity.AuthUserEntity;
import com.chainsentinel.infra.repository.AuthAuditLogRepository;
import com.chainsentinel.infra.repository.AuthRefreshTokenRepository;
import com.chainsentinel.infra.repository.AuthUserRepository;
import com.chainsentinel.infra.repository.AuthUserRoleRepository;
import com.chainsentinel.web.auth.audit.AuditEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private AuthUserRepository authUserRepository;
	@Mock
	private AuthUserRoleRepository authUserRoleRepository;
	@Mock
	private AuthRefreshTokenRepository authRefreshTokenRepository;
	@Mock
	private AuthAuditLogRepository authAuditLogRepository;
	@Mock
	private HttpServletRequest request;
	@Mock
	private AuditEventPublisher auditEventPublisher;

	private AuthService authService;
	private PasswordPolicyValidator passwordPolicyValidator;
	private UsernamePolicyValidator usernamePolicyValidator;

	@BeforeEach
	void setUp() {
		AuthProperties properties = new AuthProperties();
		properties.setJwtSecret("test-secret");
		properties.setAccessTokenTtlSeconds(3600);
		properties.setRefreshTokenTtlSeconds(86400);
		properties.setLoginFailMaxAttempts(2);
		properties.setLoginFailWindowSeconds(300);
		properties.setLoginLockSeconds(60);
		properties.setPasswordMinLength(10);
		passwordPolicyValidator = new PasswordPolicyValidator(properties);
		usernamePolicyValidator = new UsernamePolicyValidator();
		JwtTokenService jwtTokenService = new JwtTokenService(new ObjectMapper(), properties);
		authService = new AuthService(
			authUserRepository,
			authUserRoleRepository,
			authRefreshTokenRepository,
			authAuditLogRepository,
			auditEventPublisher,
			passwordPolicyValidator,
			usernamePolicyValidator,
			jwtTokenService,
			properties
		);
		when(request.getMethod()).thenReturn("POST");
		when(request.getRequestURI()).thenReturn("/api/auth/login");
		when(request.getRemoteAddr()).thenReturn("127.0.0.1");
	}

	@Test
	void shouldLoginSuccess() {
		AuthUserEntity user = buildUser(1L, "admin", "Admin12345");
		when(authUserRepository.findByUsernameAndEnabledTrue("admin")).thenReturn(Optional.of(user));
		when(authUserRoleRepository.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));

		AuthService.LoginResult result = authService.login("admin", "Admin12345", request, "t1");

		assertNotNull(result.accessToken());
		assertNotNull(result.refreshToken());
		assertEquals("admin", result.username());
		assertTrue(result.roles().contains(AuthRole.ADMIN));
		verify(authRefreshTokenRepository).save(any(AuthRefreshTokenEntity.class));
	}

	@Test
	void shouldLockAfterTooManyFailures() {
		when(authUserRepository.findByUsernameAndEnabledTrue("admin")).thenReturn(Optional.empty());

		assertThrows(AuthException.class, () -> authService.login("admin", "bad", request, "t1"));
		assertThrows(AuthException.class, () -> authService.login("admin", "bad", request, "t2"));
		AuthException ex = assertThrows(AuthException.class, () -> authService.login("admin", "bad", request, "t3"));

		assertEquals(AuthErrorCode.AUTH_LOGIN_LOCKED, ex.getErrorCode());
		assertEquals(HttpStatus.TOO_MANY_REQUESTS, ex.getStatus());
	}

	@Test
	void shouldRefreshRotateToken() {
		AuthRefreshTokenEntity oldToken = new AuthRefreshTokenEntity();
		oldToken.setUserId(1L);
		oldToken.setTokenId("old-r");
		oldToken.setRevoked(false);
		oldToken.setExpiresAt(Instant.now().plusSeconds(120));
		AuthUserEntity user = buildUser(1L, "admin", "Admin12345");

		when(authRefreshTokenRepository.findByTokenIdAndRevokedFalse("old-r")).thenReturn(Optional.of(oldToken));
		when(authUserRepository.findByIdAndEnabledTrue(1L)).thenReturn(Optional.of(user));
		when(authUserRoleRepository.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));

		AuthService.LoginResult result = authService.refresh("old-r", request, "t3");

		assertNotNull(result.accessToken());
		assertNotNull(result.refreshToken());
		assertFalse(result.refreshToken().isBlank());
		ArgumentCaptor<AuthRefreshTokenEntity> captor = ArgumentCaptor.forClass(AuthRefreshTokenEntity.class);
		verify(authRefreshTokenRepository, org.mockito.Mockito.atLeast(2)).save(captor.capture());
		assertTrue(captor.getAllValues().stream().anyMatch(v -> Boolean.TRUE.equals(v.getRevoked())));
	}

	@Test
	void shouldLogoutRevokeRefreshToken() {
		AuthRefreshTokenEntity token = new AuthRefreshTokenEntity();
		token.setUserId(1L);
		token.setTokenId("r1");
		token.setRevoked(false);
		token.setExpiresAt(Instant.now().plusSeconds(600));
		when(authRefreshTokenRepository.findByTokenIdAndRevokedFalse("r1")).thenReturn(Optional.of(token));

		authService.logout("r1", request, "t4");

		assertTrue(Boolean.TRUE.equals(token.getRevoked()));
		assertNotNull(token.getRevokedAt());
		verify(authRefreshTokenRepository).save(token);
	}

	@Test
	void shouldRejectInvalidRefreshToken() {
		when(authRefreshTokenRepository.findByTokenIdAndRevokedFalse("bad")).thenReturn(Optional.empty());

		AuthException ex = assertThrows(AuthException.class, () -> authService.refresh("bad", request, "t5"));

		assertEquals(AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID, ex.getErrorCode());
		verify(authUserRepository, never()).findByIdAndEnabledTrue(any());
	}

	@Test
	void shouldListActiveSessions() {
		AuthRefreshTokenEntity t1 = new AuthRefreshTokenEntity();
		t1.setTokenId("r1");
		t1.setIssuedIp("127.0.0.1");
		t1.setIssuedUa("ua");
		t1.setExpiresAt(Instant.now().plusSeconds(300));
		when(authRefreshTokenRepository.findByUserIdAndRevokedFalseAndExpiresAtAfter(eq(1L), any()))
			.thenReturn(List.of(t1));

		List<AuthService.SessionView> sessions = authService.listActiveSessions(1L);

		assertEquals(1, sessions.size());
		assertEquals("****", sessions.get(0).tokenId());
		assertEquals("r1", sessions.get(0).revokeTokenId());
	}

	@Test
	void shouldRevokeSessionByTokenId() {
		AuthRefreshTokenEntity token = new AuthRefreshTokenEntity();
		token.setUserId(1L);
		token.setTokenId("r1");
		token.setRevoked(false);
		token.setExpiresAt(Instant.now().plusSeconds(600));
		when(authRefreshTokenRepository.findByUserIdAndTokenIdAndRevokedFalse(1L, "r1"))
			.thenReturn(Optional.of(token));

		authService.revokeSession(1L, "r1", request, "t6");

		assertTrue(Boolean.TRUE.equals(token.getRevoked()));
		verify(authRefreshTokenRepository).save(token);
	}

	@Test
	void shouldRevokeAllSessions() {
		AuthRefreshTokenEntity t1 = new AuthRefreshTokenEntity();
		t1.setUserId(1L);
		t1.setTokenId("r1");
		t1.setRevoked(false);
		t1.setExpiresAt(Instant.now().plusSeconds(600));
		AuthRefreshTokenEntity t2 = new AuthRefreshTokenEntity();
		t2.setUserId(1L);
		t2.setTokenId("r2");
		t2.setRevoked(false);
		t2.setExpiresAt(Instant.now().plusSeconds(600));
		when(authRefreshTokenRepository.findByUserIdAndRevokedFalseAndExpiresAtAfter(eq(1L), any()))
			.thenReturn(List.of(t1, t2));

		int count = authService.revokeAllSessions(1L, request, "t7");

		assertEquals(2, count);
		assertTrue(Boolean.TRUE.equals(t1.getRevoked()));
		assertTrue(Boolean.TRUE.equals(t2.getRevoked()));
		verify(authRefreshTokenRepository).saveAll(List.of(t1, t2));
	}

	@Test
	void shouldReturnMeView() {
		AuthUserEntity user = buildUser(1L, "admin", "password");
		when(authUserRepository.findById(1L)).thenReturn(Optional.of(user));
		when(authUserRoleRepository.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN", "TRADER"));
		when(authRefreshTokenRepository.findByUserIdAndRevokedFalseAndExpiresAtAfter(eq(1L), any())).thenReturn(List.of(
			new AuthRefreshTokenEntity(),
			new AuthRefreshTokenEntity()
		));

		AuthService.MeView view = authService.me(1L);

		assertEquals(1L, view.userId());
		assertEquals("admin", view.username());
		assertEquals(2, view.activeSessionCount());
	}

	@Test
	void shouldChangePasswordAndRevokeSessions() {
		AuthUserEntity user = buildUser(1L, "admin", "OldPassword1");
		AuthRefreshTokenEntity token = new AuthRefreshTokenEntity();
		token.setUserId(1L);
		token.setTokenId("r1");
		token.setRevoked(false);
		token.setExpiresAt(Instant.now().plusSeconds(600));
		when(authUserRepository.findById(1L)).thenReturn(Optional.of(user));
		when(authRefreshTokenRepository.findByUserIdAndRevokedFalseAndExpiresAtAfter(eq(1L), any())).thenReturn(List.of(token));

		authService.changePassword(1L, "OldPassword1", "NewPassword1", request, "t8");

		assertTrue(BCrypt.checkpw("NewPassword1", user.getPasswordHash()));
		assertTrue(Boolean.TRUE.equals(token.getRevoked()));
		verify(authUserRepository).save(user);
		verify(authRefreshTokenRepository).saveAll(List.of(token));
	}

	@Test
	void shouldRejectInvalidCurrentPassword() {
		AuthUserEntity user = buildUser(1L, "admin", "OldPassword1");
		when(authUserRepository.findById(1L)).thenReturn(Optional.of(user));

		AuthException ex = assertThrows(
			AuthException.class,
			() -> authService.changePassword(1L, "BadPassword1", "NewPassword1", request, "t9")
		);

		assertEquals(AuthErrorCode.AUTH_PASSWORD_INVALID, ex.getErrorCode());
	}

	@Test
	void shouldRejectWeakNewPassword() {
		AuthUserEntity user = buildUser(1L, "admin", "OldPassword1");
		when(authUserRepository.findById(1L)).thenReturn(Optional.of(user));

		AuthException ex = assertThrows(
			AuthException.class,
			() -> authService.changePassword(1L, "OldPassword1", "weak", request, "t10")
		);

		assertEquals(AuthErrorCode.AUTH_PASSWORD_WEAK, ex.getErrorCode());
	}

	@Test
	void shouldListMyAuditLogs() {
		AuthAuditLogEntity log = new AuthAuditLogEntity();
		log.setAction("LOGIN_SUCCESS");
		log.setResult("SUCCESS");
		log.setReason("");
		log.setTraceId("t10");
		log.setRequestIp("127.0.0.1");
		log.setRequestPath("/api/auth/login");
		log.setRequestMethod("POST");
		when(authAuditLogRepository.findTop50ByUserIdOrderByIdDesc(1L)).thenReturn(List.of(log));

		List<AuthService.AuditLogView> logs = authService.listMyAuditLogs(1L, 50);

		assertEquals(1, logs.size());
		assertEquals("LOGIN_SUCCESS", logs.get(0).action());
	}

	@Test
	void shouldNormalizeUsernameOnLogin() {
		AuthUserEntity user = buildUser(1L, "alice", "Admin12345");
		when(authUserRepository.findByUsernameAndEnabledTrue("alice")).thenReturn(Optional.of(user));
		when(authUserRoleRepository.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));

		AuthService.LoginResult result = authService.login(" Alice ", "Admin12345", request, "t11");

		assertEquals("alice", result.username());
		verify(authUserRepository).findByUsernameAndEnabledTrue("alice");
	}

	private AuthUserEntity buildUser(Long id, String username, String rawPassword) {
		AuthUserEntity user = new AuthUserEntity();
		try {
			java.lang.reflect.Field idField = AuthUserEntity.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(user, id);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		user.setUsername(username);
		user.setPasswordHash(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
		user.setEnabled(true);
		return user;
	}
}
