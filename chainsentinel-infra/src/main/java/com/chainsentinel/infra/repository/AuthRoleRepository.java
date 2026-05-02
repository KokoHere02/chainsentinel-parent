package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AuthRoleEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRoleRepository extends JpaRepository<AuthRoleEntity, Long> {

	Optional<AuthRoleEntity> findByRoleCodeAndEnabledTrue(String roleCode);

	List<AuthRoleEntity> findByRoleCodeInAndEnabledTrue(Collection<String> roleCodes);
}
