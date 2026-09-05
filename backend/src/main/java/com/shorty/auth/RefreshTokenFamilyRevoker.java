package com.shorty.auth;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenFamilyRevoker {

    private final RefreshTokenRepository refreshTokens;

    public RefreshTokenFamilyRevoker(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllSessions(long userId) {
        refreshTokens.revokeAllActiveForUser(userId, Instant.now());
    }
}
