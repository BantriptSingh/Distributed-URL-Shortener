package com.shorty.auth;

import com.shorty.api.ApiException;
import com.shorty.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordResetTokenRepository resets;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final SnowflakeIdGenerator ids;
    private final Duration refreshTtl;
    private final boolean exposeReset;
    private final RefreshTokenFamilyRevoker familyRevoker;

    public AuthService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            PasswordResetTokenRepository resets,
            PasswordEncoder passwords,
            JwtService jwt,
            SnowflakeIdGenerator ids,
            RefreshTokenFamilyRevoker familyRevoker,
            @Value("${app.jwt.refresh-ttl:14d}") Duration refreshTtl,
            @Value("${DEV_EXPOSE_RESET_TOKEN:false}") boolean exposeReset) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.resets = resets;
        this.passwords = passwords;
        this.jwt = jwt;
        this.ids = ids;
        this.familyRevoker = familyRevoker;
        this.refreshTtl = refreshTtl;
        this.exposeReset = exposeReset;
    }

    @Transactional
    public TokenResponse register(String email, String password) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(normalized)) {
            throw new ApiException(HttpStatus.CONFLICT, "email_taken", "An account with that email already exists");
        }
        UserEntity user = new UserEntity();
        user.setId(ids.nextId());
        user.setEmail(normalized);
        user.setPasswordHash(passwords.encode(password));
        user.setEmailVerified(false);
        user.setCreatedAt(Instant.now());
        users.save(user);
        return issueSession(user);
    }

    @Transactional
    public TokenResponse login(String email, String password) {
        UserEntity user = users.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "bad_credentials", "Invalid email or password"));
        if (!passwords.matches(password, user.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "bad_credentials", "Invalid email or password");
        }
        return issueSession(user);
    }

    @Transactional
    public TokenResponse refresh(String refreshRaw) {
        String hash = TokenHasher.sha256Hex(refreshRaw);
        RefreshTokenEntity existing = refreshTokens
                .findByTokenHash(hash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh", "Refresh token is invalid"));
        if (existing.getRevokedAt() != null) {
            familyRevoker.revokeAllSessions(existing.getUserId());
            log.warn("refresh_token_reuse userId={} — revoked all sessions", existing.getUserId());
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "refresh_reuse",
                    "Refresh token reuse detected; all sessions have been revoked");
        }
        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh", "Refresh token is invalid");
        }
        existing.setRevokedAt(Instant.now());
        refreshTokens.save(existing);
        UserEntity user = users.findById(existing.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "invalid_refresh", "Refresh token is invalid"));
        return issueSession(user);
    }

    @Transactional
    public void logout(String refreshRaw) {
        refreshTokens.findByTokenHash(TokenHasher.sha256Hex(refreshRaw)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokens.save(token);
        });
    }

    public MeResponse me(long userId) {
        UserEntity user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required"));
        return new MeResponse(user.getId(), user.getEmail(), user.isEmailVerified(), user.getCreatedAt());
    }

    @Transactional
    public ForgotResponse forgot(String email) {
        var user = users.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT));
        if (user.isEmpty()) {
            return exposeReset ? new ForgotResponse(true, null) : new ForgotResponse(true, null);
        }
        String raw = TokenHasher.randomUrlToken(24);
        PasswordResetTokenEntity row = new PasswordResetTokenEntity();
        row.setId(ids.nextId());
        row.setUserId(user.get().getId());
        row.setTokenHash(TokenHasher.sha256Hex(raw));
        row.setExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        row.setUsed(false);
        row.setCreatedAt(Instant.now());
        resets.save(row);
        log.info("DEV password reset token for {} is {} (remove DEV_EXPOSE_RESET_TOKEN in production)", email, raw);
        return new ForgotResponse(true, exposeReset ? raw : null);
    }

    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetTokenEntity row = resets
                .findByTokenHash(TokenHasher.sha256Hex(token))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "invalid_reset", "Reset token is invalid"));
        if (row.isUsed() || row.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_reset", "Reset token is invalid");
        }
        UserEntity user = users.findById(row.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "invalid_reset", "Reset token is invalid"));
        user.setPasswordHash(passwords.encode(newPassword));
        users.save(user);
        row.setUsed(true);
        resets.save(row);
    }

    private TokenResponse issueSession(UserEntity user) {
        String refreshRaw = TokenHasher.randomUrlToken(32);
        RefreshTokenEntity row = new RefreshTokenEntity();
        row.setId(ids.nextId());
        row.setUserId(user.getId());
        row.setTokenHash(TokenHasher.sha256Hex(refreshRaw));
        Instant now = Instant.now();
        row.setCreatedAt(now);
        row.setExpiresAt(now.plus(refreshTtl));
        refreshTokens.save(row);
        return new TokenResponse(jwt.issueAccess(user), refreshRaw, "Bearer", user.getEmail());
    }

    public record TokenResponse(String accessToken, String refreshToken, String tokenType, String email) {}

    public record MeResponse(long id, String email, boolean emailVerified, Instant createdAt) {}

    public record ForgotResponse(boolean ok, String devResetToken) {}

    public record Credentials(String email, String password) {}

    public record RefreshRequest(String refreshToken) {}

    public record ResetRequest(String token, String password) {}

    public record ForgotRequest(String email) {}
}
