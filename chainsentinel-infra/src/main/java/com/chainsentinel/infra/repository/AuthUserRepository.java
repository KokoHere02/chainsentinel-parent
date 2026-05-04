package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AuthUserEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthUserRepository extends JpaRepository<AuthUserEntity, Long> {

	Optional<AuthUserEntity> findByUsernameAndEnabledTrue(String username);

	Optional<AuthUserEntity> findByIdAndEnabledTrue(Long id);

	Optional<AuthUserEntity> findByUsername(String username);

	boolean existsByUsername(String username);

	@Query(value = """
		select u.id as userId,
		       u.username as username,
		       u.enabled as enabled,
		       r.role_code as roleCode
		from auth_user u
		left join auth_user_role ur on ur.user_id = u.id
		left join auth_role r on r.id = ur.role_id and r.enabled = b'1'
		where u.id in (:userIds)
		order by u.id desc, r.role_code asc
		""", nativeQuery = true)
	List<UserWithRoleRow> findUserRoleRowsByUserIds(@Param("userIds") List<Long> userIds);

	interface UserWithRoleRow {
		Long getUserId();

		String getUsername();

		Boolean getEnabled();

		String getRoleCode();
	}
}
