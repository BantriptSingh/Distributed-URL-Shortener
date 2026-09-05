package com.shorty.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shorty.api.ApiException;
import com.shorty.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshExpiryTest {

    @Mock
    UserRepository users;

    @Mock
    RefreshTokenRepository refreshTokens;

    @Mock
    PasswordResetTokenRepository resets;

    @Mock
    PasswordEncoder passwords;

    @Mock
    JwtService jwt;

    @Mock
    RefreshTokenFamilyRevoker familyRevoker;

    private AuthService auth;

    @BeforeEach
    void setUp() {
        auth = new AuthService(
                users,
                refreshTokens,
                resets,
                passwords,
                jwt,
                new SnowflakeIdGenerator(1),
                familyRevoker,
                Duration.ofDays(14),
                false);
    }

    @Test
    void expiredUnrevokedRefreshIsInvalidAndDoesNotRevokeFamily() {
        RefreshTokenEntity row = new RefreshTokenEntity();
        row.setId(1L);
        row.setUserId(42L);
        row.setTokenHash(TokenHasher.sha256Hex("expired-refresh"));
        row.setCreatedAt(Instant.now().minus(Duration.ofDays(20)));
        row.setExpiresAt(Instant.now().minus(Duration.ofHours(1)));
        row.setRevokedAt(null);
        when(refreshTokens.findByTokenHash(TokenHasher.sha256Hex("expired-refresh"))).thenReturn(Optional.of(row));

        assertThatThrownBy(() -> auth.refresh("expired-refresh"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(api.getCode()).isEqualTo("invalid_refresh");
                });
        verify(familyRevoker, never()).revokeAllSessions(anyLong());
        verify(familyRevoker, never()).revokeAllSessions(42L);
    }
}
