package com.chainsentinel.infra.repository;

import com.chainsentinel.infra.entity.AuthRefreshTokenEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshTokenEntity, Long> {

	Optional<AuthRefreshTokenEntity> findByTokenId(String tokenId);

	Optional<AuthRefreshTokenEntity> findByTokenIdAndRevokedFalse(String tokenId);

	Optional<AuthRefreshTokenEntity> findByUserIdAndTokenIdAndRevokedFalse(Long userId, String tokenId);

	List<AuthRefreshTokenEntity> findByUserIdAndRevokedFalseAndExpiresAtAfter(Long userId, Instant now);
}
