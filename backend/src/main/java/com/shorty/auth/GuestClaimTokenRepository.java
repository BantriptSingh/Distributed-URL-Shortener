package com.shorty.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestClaimTokenRepository extends JpaRepository<GuestClaimTokenEntity, String> {

    Optional<GuestClaimTokenEntity> findByTokenHash(String tokenHash);
}
