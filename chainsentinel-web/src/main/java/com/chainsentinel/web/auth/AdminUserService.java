package com.chainsentinel.web.auth;

import com.chainsentinel.infra.entity.AuthRoleEntity;
import com.chainsentinel.infra.entity.AuthUserEntity;
import com.chainsentinel.infra.entity.AuthUserRoleEntity;
import com.chainsentinel.infra.repository.AuthRoleRepository;
import com.chainsentinel.infra.repository.AuthUserRepository;
import com.chainsentinel.infra.repository.AuthUserRoleRepository;
import com.chainsentinel.web.auth.audit.AuditEvent;
import com.chainsentinel.web.auth.audit.AuditEventPublisher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserService {

	private final AuthUserRepository authUserRepository;
	private final AuthRoleRepository authRoleRepository;
	private final AuthUserRoleRepository authUserRoleRepository;
	private final AuditEventPublisher auditEventPublisher;
	private final PasswordPolicyValidator passwordPolicyValidator;
	private final UsernamePolicyValidator usernamePolicyValidator;

	public AdminUserService(
		AuthUserRepository authUserRepository,
		AuthRoleRepository authRoleRepository,
		AuthUserRoleRepository authUserRoleRepository,
		AuditEventPublisher auditEventPublisher,
		PasswordPolicyValidator passwordPolicyValidator,
		UsernamePolicyValidator usernamePolicyValidator
	) {
		this.authUserRepository = authUserRepository;
		this.authRoleRepository = authRoleRepository;
		this.authUserRoleRepository = authUserRoleRepository;
		this.auditEventPublisher = auditEventPublisher;
		this.passwordPolicyValidator = passwordPolicyValidator;
		this.usernamePolicyValidator = usernamePolicyValidator;
	}

	@Transactional
	public UserView createUser(
		String username,
		String password,
		Set<AuthRole> roles,
		boolean enabled,
		HttpServletRequest request,
		String traceId
	) {
		String normalizedUsername = usernamePolicyValidator.normalizeAndValidate(username);
		if (authUserRepository.existsByUsername(normalizedUsername)) {
			throw new AuthException(AuthErrorCode.AUTH_USERNAME_CONFLICT, HttpStatus.CONFLICT, "Username already exists");
		}
		List<AuthRoleEntity> roleEntities = resolveRoles(roles);
		passwordPolicyValidator.validate(password);

		AuthUserEntity user = new AuthUserEntity();
		user.setUsername(normalizedUsername);
		user.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
		user.setEnabled(enabled);
		AuthUserEntity savedUser = authUserRepository.save(user);
		rebindRoles(savedUser.getId(), roleEntities);
		audit("ADMIN_USER_CREATE_SUCCESS", savedUser.getId(), savedUser.getUsername(), "SUCCESS", "", request, traceId);
		return toUserView(savedUser, toRoleSet(roleEntities));
	}

	@Transactional(readOnly = true)
	public List<UserView> listUsers() {
		return authUserRepository.findAll().stream()
			.sorted(Comparator.comparing(AuthUserEntity::getId))
			.map(user -> toUserView(user, getUserRoles(user.getId())))
			.toList();
	}

	@Transactional
	public void updatePassword(
		Long userId,
		String newPassword,
		HttpServletRequest request,
		String traceId
	) {
		AuthUserEntity user = getUserOrThrow(userId);
		passwordPolicyValidator.validate(newPassword);
		user.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
		authUserRepository.save(user);
		audit("ADMIN_USER_PASSWORD_RESET_SUCCESS", user.getId(), user.getUsername(), "SUCCESS", "", request, traceId);
	}

	@Transactional
	public UserView updateRoles(
		Long userId,
		Set<AuthRole> roles,
		HttpServletRequest request,
		String traceId
	) {
		AuthUserEntity user = getUserOrThrow(userId);
		List<AuthRoleEntity> roleEntities = resolveRoles(roles);
		rebindRoles(user.getId(), roleEntities);
		Set<AuthRole> updatedRoles = toRoleSet(roleEntities);
		audit(
			"ADMIN_USER_ROLE_UPDATE_SUCCESS",
			user.getId(),
			user.getUsername(),
			"SUCCESS",
			updatedRoles.stream().map(Enum::name).sorted().collect(Collectors.joining(",")),
			request,
			traceId
		);
		return toUserView(user, updatedRoles);
	}

	@Transactional
	public UserView updateStatus(
		Long userId,
		boolean enabled,
		HttpServletRequest request,
		String traceId
	) {
		AuthUserEntity user = getUserOrThrow(userId);
		user.setEnabled(enabled);
		AuthUserEntity savedUser = authUserRepository.save(user);
		audit(
			"ADMIN_USER_STATUS_UPDATE_SUCCESS",
			savedUser.getId(),
			savedUser.getUsername(),
			"SUCCESS",
			enabled ? "enabled" : "disabled",
			request,
			traceId
		);
		return toUserView(savedUser, getUserRoles(savedUser.getId()));
	}

	private AuthUserEntity getUserOrThrow(Long userId) {
		return authUserRepository.findById(userId)
			.orElseThrow(() -> new AuthException(AuthErrorCode.AUTH_USER_NOT_FOUND, HttpStatus.NOT_FOUND, "User not found"));
	}

	private List<AuthRoleEntity> resolveRoles(Set<AuthRole> roles) {
		if (roles == null || roles.isEmpty()) {
			throw new AuthException(AuthErrorCode.AUTH_ROLE_INVALID, HttpStatus.BAD_REQUEST, "At least one role is required");
		}
		Set<String> roleCodes = roles.stream().map(Enum::name).collect(Collectors.toCollection(LinkedHashSet::new));
		List<AuthRoleEntity> roleEntities = authRoleRepository.findByRoleCodeInAndEnabledTrue(roleCodes);
		if (roleEntities.size() != roleCodes.size()) {
			throw new AuthException(AuthErrorCode.AUTH_ROLE_INVALID, HttpStatus.BAD_REQUEST, "One or more roles are invalid");
		}
		return roleEntities;
	}

	private void rebindRoles(Long userId, List<AuthRoleEntity> roleEntities) {
		authUserRoleRepository.deleteByUserId(userId);
		List<AuthUserRoleEntity> bindings = roleEntities.stream().map(role -> {
			AuthUserRoleEntity binding = new AuthUserRoleEntity();
			binding.setUserId(userId);
			binding.setRoleId(role.getId());
			return binding;
		}).toList();
		authUserRoleRepository.saveAll(bindings);
	}

	private Set<AuthRole> getUserRoles(Long userId) {
		return authUserRoleRepository.findRoleCodesByUserId(userId).stream()
			.map(AuthRole::valueOf)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private Set<AuthRole> toRoleSet(List<AuthRoleEntity> roleEntities) {
		return roleEntities.stream()
			.map(AuthRoleEntity::getRoleCode)
			.map(AuthRole::valueOf)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private UserView toUserView(AuthUserEntity user, Set<AuthRole> roles) {
		return new UserView(user.getId(), user.getUsername(), Boolean.TRUE.equals(user.getEnabled()), roles);
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

	public record UserView(Long id, String username, boolean enabled, Set<AuthRole> roles) {
	}
}
