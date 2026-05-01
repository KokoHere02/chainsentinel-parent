package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AuthUserRoleEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthUserRoleRepository extends JpaRepository<AuthUserRoleEntity, Long> {

	@Query(
		value = """
			SELECT r.role_code
			FROM auth_user_role ur
			JOIN auth_role r ON ur.role_id = r.id
			WHERE ur.user_id = :userId AND r.enabled = b'1'
		""",
		nativeQuery = true
	)
	List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
