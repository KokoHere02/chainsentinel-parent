package com.chainsentinel.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
	private PasswordPolicyValidator passwordPolicyValidator;
	private UsernamePolicyValidator usernamePolicyValidator;

	@BeforeEach
	void setUp() {
		AuthProperties authProperties = new AuthProperties();
		authProperties.setPasswordMinLength(10);
		passwordPolicyValidator = new PasswordPolicyValidator(authProperties);
		usernamePolicyValidator = new UsernamePolicyValidator();
		adminUserService = new AdminUserService(
			authUserRepository,
			authRoleRepository,
			authUserRoleRepository,
			auditEventPublisher,
			passwordPolicyValidator,
			usernamePolicyValidator
		);
		lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
		lenient().when(request.getRequestURI()).thenReturn("/api/admin/users");
		lenient().when(request.getMethod()).thenReturn("POST");
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
			"Password1A",
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
		when(authUserRepository.findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "id"))))
			.thenReturn(new PageImpl<>(List.of(
				buildUser(2L, "alice", false),
				buildUser(1L, "admin", true)
			)));
		when(authUserRepository.findUserRoleRowsByUserIds(List.of(2L, 1L))).thenReturn(List.of(
			buildUserRow(2L, "alice", false, "TRADER"),
			buildUserRow(1L, "admin", true, "ADMIN")
		));

		List<AdminUserService.UserView> users = adminUserService.listUsers();

		assertEquals(2, users.size());
		assertEquals("alice", users.get(0).username());
		assertFalse(users.get(0).enabled());
	}

	@Test
	void shouldClampUserListPageBounds() {
		when(authUserRepository.findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "id"))))
			.thenReturn(new PageImpl<>(List.of()));

		List<AdminUserService.UserView> users = adminUserService.listUsers(-1, 999);

		assertTrue(users.isEmpty());
		verify(authUserRepository).findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "id")));
	}

	@Test
	void shouldUpdatePassword() {
		AuthUserEntity user = buildUser(2L, "alice", true);
		user.setPasswordHash(BCrypt.hashpw("old", BCrypt.gensalt()));
		when(authUserRepository.findById(2L)).thenReturn(Optional.of(user));

		adminUserService.updatePassword(2L, "NewPassword1", request, "t2");

		verify(authUserRepository).save(user);
		assertTrue(BCrypt.checkpw("NewPassword1", user.getPasswordHash()));
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
			() -> adminUserService.createUser("alice", "Password1A", Set.of(AuthRole.OPERATOR), true, request, "t5")
		);

		assertEquals(AuthErrorCode.AUTH_USERNAME_CONFLICT, ex.getErrorCode());
		assertEquals(HttpStatus.CONFLICT, ex.getStatus());
	}

	@Test
	void shouldRejectWeakPasswordWhenCreateUser() {
		when(authUserRepository.existsByUsername("alice")).thenReturn(false);
		when(authRoleRepository.findByRoleCodeInAndEnabledTrue(Set.of("OPERATOR"))).thenReturn(List.of(buildRole(2L, "OPERATOR")));

		AuthException ex = assertThrows(
			AuthException.class,
			() -> adminUserService.createUser("alice", "weak", Set.of(AuthRole.OPERATOR), true, request, "t6")
		);

		assertEquals(AuthErrorCode.AUTH_PASSWORD_WEAK, ex.getErrorCode());
	}

	@Test
	void shouldRejectInvalidUsernameWhenCreateUser() {
		AuthException ex = assertThrows(
			AuthException.class,
			() -> adminUserService.createUser("A*", "Password1A", Set.of(AuthRole.OPERATOR), true, request, "t7")
		);

		assertEquals(AuthErrorCode.AUTH_USERNAME_INVALID, ex.getErrorCode());
		assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
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

	private AuthUserRepository.UserWithRoleRow buildUserRow(Long id, String username, boolean enabled, String roleCode) {
		return new AuthUserRepository.UserWithRoleRow() {
			@Override
			public Long getUserId() {
				return id;
			}

			@Override
			public String getUsername() {
				return username;
			}

			@Override
			public Boolean getEnabled() {
				return enabled;
			}

			@Override
			public String getRoleCode() {
				return roleCode;
			}
		};
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
