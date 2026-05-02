package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AuthUserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthUserRepository extends JpaRepository<AuthUserEntity, Long> {

	Optional<AuthUserEntity> findByUsernameAndEnabledTrue(String username);

	Optional<AuthUserEntity> findByIdAndEnabledTrue(Long id);

	Optional<AuthUserEntity> findByUsername(String username);

	boolean existsByUsername(String username);
}
