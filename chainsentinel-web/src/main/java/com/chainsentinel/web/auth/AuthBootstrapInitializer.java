package com.chainsentinel.web.auth;

import com.chainsentinel.infra.entity.AuthRoleEntity;
import com.chainsentinel.infra.entity.AuthUserEntity;
import com.chainsentinel.infra.entity.AuthUserRoleEntity;
import com.chainsentinel.infra.repository.AuthRoleRepository;
import com.chainsentinel.infra.repository.AuthUserRepository;
import com.chainsentinel.infra.repository.AuthUserRoleRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuthBootstrapInitializer {

	private static final Logger log = LoggerFactory.getLogger(AuthBootstrapInitializer.class);

	private final AuthProperties authProperties;
	private final AuthUserRepository authUserRepository;
	private final AuthRoleRepository authRoleRepository;
	private final AuthUserRoleRepository authUserRoleRepository;
	private final PasswordPolicyValidator passwordPolicyValidator;
	private final UsernamePolicyValidator usernamePolicyValidator;

	public AuthBootstrapInitializer(
		AuthProperties authProperties,
		AuthUserRepository authUserRepository,
		AuthRoleRepository authRoleRepository,
		AuthUserRoleRepository authUserRoleRepository,
		PasswordPolicyValidator passwordPolicyValidator,
		UsernamePolicyValidator usernamePolicyValidator
	) {
		this.authProperties = authProperties;
		this.authUserRepository = authUserRepository;
		this.authRoleRepository = authRoleRepository;
		this.authUserRoleRepository = authUserRoleRepository;
		this.passwordPolicyValidator = passwordPolicyValidator;
		this.usernamePolicyValidator = usernamePolicyValidator;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void bootstrapAdmin() {
		if (!authProperties.isBootstrapAdminEnabled()) {
			return;
		}
		String normalizedUsername = usernamePolicyValidator.normalizeAndValidate(authProperties.getBootstrapAdminUsername());
		String rawCredential = authProperties.getBootstrapAdminPassword();
		passwordPolicyValidator.validate(rawCredential);
		if (authUserRepository.existsByUsername(normalizedUsername)) {
			log.info("auth.bootstrap-admin.skip username={} reason=already_exists", normalizedUsername);
			return;
		}
		AuthRoleEntity adminRole = authRoleRepository.findByRoleCodeAndEnabledTrue(AuthRole.ADMIN.name())
			.orElseThrow(() -> new IllegalStateException("ADMIN role is not available for bootstrap initialization"));

		AuthUserEntity user = new AuthUserEntity();
		user.setUsername(normalizedUsername);
		user.setPasswordHash(BCrypt.hashpw(rawCredential, BCrypt.gensalt()));
		user.setEnabled(true);
		AuthUserEntity savedUser = authUserRepository.save(user);

		AuthUserRoleEntity userRole = new AuthUserRoleEntity();
		userRole.setUserId(savedUser.getId());
		userRole.setRoleId(adminRole.getId());
		authUserRoleRepository.saveAll(List.of(userRole));
		log.warn("auth.bootstrap-admin.created username={} userId={}", savedUser.getUsername(), savedUser.getId());
	}
}
