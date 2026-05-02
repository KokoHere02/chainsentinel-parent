package com.chainsentinel.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainsentinel.infra.entity.AuthRoleEntity;
import com.chainsentinel.infra.entity.AuthUserEntity;
import com.chainsentinel.infra.entity.AuthUserRoleEntity;
import com.chainsentinel.infra.repository.AuthRoleRepository;
import com.chainsentinel.infra.repository.AuthUserRepository;
import com.chainsentinel.infra.repository.AuthUserRoleRepository;
import com.chainsentinel.web.auth.audit.AuditEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

	@Mock
	private AuthUserRepository authUserRepository;
	@Mock
	private AuthRoleRepository authRoleRepository;
	@Mock
	private AuthUserRoleRepository authUserRoleRepository;
	@Mock
	private AuditEventPublisher auditEventPublisher;
	@Mock
	private HttpServletRequest request;

	private AdminUserService adminUserService;

	@BeforeEach
	void setUp() {
		adminUserService = new AdminUserService(
			authUserRepository,
			authRoleRepository,
			authUserRoleRepository,
			auditEventPublisher
		);
		when(request.getRemoteAddr()).thenReturn("127.0.0.1");
		when(request.getRequestURI()).thenReturn("/api/admin/users");
		when(request.getMethod()).thenReturn("POST");
	}

	@Test
	void shouldCreateUser() {
		AuthRoleEntity operatorRole = buildRole(2L, "OPERATOR");
		when(authUserRepository.existsByUsername("alice")).thenReturn(false);
		when(authRoleRepository.findByRoleCodeInAndEnabledTrue(Set.of("OPERATOR"))).thenReturn(List.of(operatorRole));
		when(authUserRepository.save(any(AuthUserEntity.class))).thenAnswer(invocation -> {
			AuthUserEntity entity = invocation.getArgument(0);
			setUserId(entity, 2L);
			return entity;
		});

		AdminUserService.UserView view = adminUserService.createUser(
			"Alice",
			"password",
			Set.of(AuthRole.OPERATOR),
			true,
			request,
			"t1"
		);

		assertEquals("alice", view.username());
		assertTrue(view.enabled());
		assertTrue(view.roles().contains(AuthRole.OPERATOR));
		ArgumentCaptor<List<AuthUserRoleEntity>> captor = ArgumentCaptor.forClass(List.class);
		verify(authUserRoleRepository).saveAll(captor.capture());
		assertEquals(1, captor.getValue().size());
	}

	@Test
	void shouldListUsers() {
		AuthUserEntity admin = buildUser(1L, "admin", true);
		AuthUserEntity alice = buildUser(2L, "alice", false);
		when(authUserRepository.findAll()).thenReturn(List.of(alice, admin));
		when(authUserRoleRepository.findRoleCodesByUserId(1L)).thenReturn(List.of("ADMIN"));
		when(authUserRoleRepository.findRoleCodesByUserId(2L)).thenReturn(List.of("TRADER"));

		List<AdminUserService.UserView> users = adminUserService.listUsers();

		assertEquals(2, users.size());
		assertEquals("admin", users.get(0).username());
		assertFalse(users.get(1).enabled());
	}

	@Test
	void shouldUpdatePassword() {
		AuthUserEntity user = buildUser(2L, "alice", true);
		user.setPasswordHash(BCrypt.hashpw("old", BCrypt.gensalt()));
		when(authUserRepository.findById(2L)).thenReturn(Optional.of(user));

		adminUserService.updatePassword(2L, "new-password", request, "t2");

		verify(authUserRepository).save(user);
		assertTrue(BCrypt.checkpw("new-password", user.getPasswordHash()));
	}

	@Test
	void shouldUpdateRoles() {
		AuthUserEntity user = buildUser(2L, "alice", true);
		AuthRoleEntity traderRole = buildRole(3L, "TRADER");
		when(authUserRepository.findById(2L)).thenReturn(Optional.of(user));
		when(authRoleRepository.findByRoleCodeInAndEnabledTrue(Set.of("TRADER"))).thenReturn(List.of(traderRole));

		AdminUserService.UserView view = adminUserService.updateRoles(2L, Set.of(AuthRole.TRADER), request, "t3");

		verify(authUserRoleRepository).deleteByUserId(2L);
		assertEquals(Set.of(AuthRole.TRADER), view.roles());
	}

	@Test
	void shouldUpdateStatus() {
		AuthUserEntity user = buildUser(2L, "alice", true);
		when(authUserRepository.findById(2L)).thenReturn(Optional.of(user));
		when(authUserRepository.save(user)).thenReturn(user);
		when(authUserRoleRepository.findRoleCodesByUserId(2L)).thenReturn(List.of("OPERATOR"));

		AdminUserService.UserView view = adminUserService.updateStatus(2L, false, request, "t4");

		assertFalse(view.enabled());
	}

	@Test
	void shouldRejectDuplicateUsername() {
		when(authUserRepository.existsByUsername("alice")).thenReturn(true);

		AuthException ex = assertThrows(
			AuthException.class,
			() -> adminUserService.createUser("alice", "password", Set.of(AuthRole.OPERATOR), true, request, "t5")
		);

		assertEquals(AuthErrorCode.AUTH_USERNAME_CONFLICT, ex.getErrorCode());
		assertEquals(HttpStatus.CONFLICT, ex.getStatus());
	}

	private AuthRoleEntity buildRole(Long id, String roleCode) {
		AuthRoleEntity role = new AuthRoleEntity();
		try {
			java.lang.reflect.Field idField = AuthRoleEntity.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(role, id);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		role.setRoleCode(roleCode);
		role.setRoleName(roleCode);
		role.setEnabled(true);
		return role;
	}

	private AuthUserEntity buildUser(Long id, String username, boolean enabled) {
		AuthUserEntity user = new AuthUserEntity();
		setUserId(user, id);
		user.setUsername(username);
		user.setEnabled(enabled);
		return user;
	}

	private void setUserId(AuthUserEntity user, Long id) {
		try {
			java.lang.reflect.Field idField = AuthUserEntity.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(user, id);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
