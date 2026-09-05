package com.shorty.url;

import com.shorty.api.ApiException;
import com.shorty.auth.AuthPrincipal;
import com.shorty.auth.GuestClaimTokenEntity;
import com.shorty.auth.GuestClaimTokenRepository;
import com.shorty.auth.TokenHasher;
import com.shorty.click.ClickCountStore;
import com.shorty.click.ClickRepository;
import com.shorty.config.AppProperties;
import com.shorty.id.ShortCodeGenerator;
import com.shorty.id.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlService {

    private final UrlRepository urls;
    private final UrlCache cache;
    private final ShortCodeGenerator codes;
    private final SnowflakeIdGenerator ids;
    private final DestinationUrlValidator destinations;
    private final CustomAliasValidator aliases;
    private final AppProperties props;
    private final ClickCountStore clickCounts;
    private final ClickRepository clickRepository;
    private final GuestClaimTokenRepository claims;
    private final PasswordEncoder passwords;
    private final Duration claimTtl;

    public UrlService(
            UrlRepository urls,
            UrlCache cache,
            ShortCodeGenerator codes,
            SnowflakeIdGenerator ids,
            DestinationUrlValidator destinations,
            CustomAliasValidator aliases,
            AppProperties props,
            ClickCountStore clickCounts,
            ClickRepository clickRepository,
            GuestClaimTokenRepository claims,
            PasswordEncoder passwords,
            @Value("${app.claim-ttl:7d}") Duration claimTtl) {
        this.urls = urls;
        this.cache = cache;
        this.codes = codes;
        this.ids = ids;
        this.destinations = destinations;
        this.aliases = aliases;
        this.props = props;
        this.clickCounts = clickCounts;
        this.clickRepository = clickRepository;
        this.claims = claims;
        this.passwords = passwords;
        this.claimTtl = claimTtl;
    }

    @Transactional
    public CreateUrlResponse create(CreateUrlRequest request) {
        return create(request, null);
    }

    @Transactional
    public CreateUrlResponse create(CreateUrlRequest request, Long ownerId) {
        String destination = destinations.validate(request.destinationUrl());
        boolean custom = request.customCode() != null && !request.customCode().isBlank();
        String assigned = custom ? aliases.validateAndNormalize(request.customCode()) : null;
        if (custom && urls.existsByShortCodeIgnoreCase(assigned)) {
            throw new ApiException(HttpStatus.CONFLICT, "alias_taken", "That custom code is already in use");
        }

        UrlEntity saved = persistWithRetry(destination, assigned, custom, request, ownerId);
        clickCounts.set(ShortCodes.normalize(saved.getShortCode()), 0);
        cache.putPositive(toCache(saved));
        String claimToken = null;
        if (ownerId == null) {
            claimToken = issueClaimToken(saved.getId());
        }
        return new CreateUrlResponse(
                saved.getShortCode(),
                shortUrl(saved.getShortCode()),
                saved.getDestinationUrl(),
                saved.getCreatedAt(),
                saved.getExpiresAt(),
                saved.getMaxClicks(),
                saved.getTags(),
                saved.isPublicClickCount(),
                saved.isHidePreview(),
                claimToken);
    }

    public UrlView getView(String code, AuthPrincipal principal) {
        UrlEntity entity = urls.findByShortCodeIgnoreCase(ShortCodes.normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short link not found"));
        boolean owner = isOwner(entity, principal);
        if (!entity.isActive() && !owner) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short link not found");
        }
        return toView(entity, owner);
    }

    public Page<UrlView> listOwned(
            AuthPrincipal principal,
            Boolean active,
            Instant from,
            Instant to,
            String tag,
            int page,
            int size,
            String sort) {
        requireRead(principal);
        Sort s = "createdAt,asc".equals(sort)
                ? Sort.by("createdAt").ascending()
                : Sort.by("createdAt").descending();
        PageRequest pr = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), s);
        Page<UrlEntity> result = urls.findByOwnerId(principal.userId(), pr);
        var filtered = result.getContent().stream()
                .filter(e -> active == null || e.isActive() == active)
                .filter(e -> from == null || !e.getCreatedAt().isBefore(from))
                .filter(e -> to == null || e.getCreatedAt().isBefore(to))
                .filter(e -> blankToNull(tag) == null || (e.getTags() != null && e.getTags().contains(tag)))
                .map(entity -> toView(entity, true))
                .toList();
        if (active == null && from == null && to == null && blankToNull(tag) == null) {
            return result.map(entity -> toView(entity, true));
        }
        return new org.springframework.data.domain.PageImpl<>(filtered, pr, filtered.size());
    }

    @Transactional
    public UrlView patch(String code, PatchUrlRequest request, AuthPrincipal principal) {
        requireWrite(principal);
        UrlEntity entity = requireOwned(code, principal);
        if (request.destinationUrl() != null) {
            entity.setDestinationUrl(destinations.validate(request.destinationUrl()));
        }
        if (request.expiresAt() != null) {
            entity.setExpiresAt(request.expiresAt());
        }
        if (request.maxClicks() != null) {
            entity.setMaxClicks(request.maxClicks());
        }
        if (request.tags() != null) {
            entity.setTags(request.tags());
        }
        if (request.isActive() != null) {
            entity.setActive(request.isActive());
        }
        if (request.publicClickCount() != null) {
            entity.setPublicClickCount(request.publicClickCount());
        }
        if (request.hidePreview() != null) {
            entity.setHidePreview(request.hidePreview());
        }
        if (request.password() != null) {
            if (request.password().isBlank()) {
                entity.setPasswordHash(null);
            } else {
                entity.setPasswordHash(passwords.encode(request.password()));
            }
        }
        entity.setUpdatedAt(Instant.now());
        urls.save(entity);
        cache.invalidate(ShortCodes.normalize(entity.getShortCode()));
        cache.putPositive(toCache(entity));
        return toView(entity, true);
    }

    @Transactional
    public void delete(String code, boolean hard, AuthPrincipal principal) {
        requireWrite(principal);
        UrlEntity entity = requireOwned(code, principal);
        String normalized = ShortCodes.normalize(entity.getShortCode());
        if (hard) {
            urls.delete(entity);
            cache.invalidate(normalized);
            cache.putNegative(normalized);
            return;
        }
        entity.setActive(false);
        entity.setUpdatedAt(Instant.now());
        urls.save(entity);
        cache.invalidate(normalized);
        cache.putPositive(toCache(entity));
    }

    @Transactional
    public void claim(String code, String claimToken, AuthPrincipal principal) {
        requireWrite(principal);
        UrlEntity entity = urls.findByShortCodeIgnoreCase(ShortCodes.normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short link not found"));
        if (entity.getOwnerId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "already_owned", "This link already has an owner");
        }
        GuestClaimTokenEntity row = claims
                .findByTokenHash(TokenHasher.sha256Hex(claimToken))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "invalid_claim", "Claim token is invalid"));
        if (row.isUsed() || row.getExpiresAt().isBefore(Instant.now()) || !row.getUrlId().equals(entity.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_claim", "Claim token is invalid");
        }
        entity.setOwnerId(principal.userId());
        entity.setUpdatedAt(Instant.now());
        urls.save(entity);
        row.setUsed(true);
        claims.save(row);
        cache.invalidate(ShortCodes.normalize(entity.getShortCode()));
        cache.putPositive(toCache(entity));
    }

    public UrlEntity requireOwnedEntity(String code, AuthPrincipal principal) {
        return requireOwned(code, principal);
    }

    public UrlCacheEntry requireActive(String code) {
        UrlCacheEntry entry = resolveEntry(code);
        if (entry == null || !entry.active()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short link not found");
        }
        return entry;
    }

    public String unlockUrl(String shortCode) {
        return props.frontendBaseUrl().replaceAll("/$", "") + "/unlock/" + shortCode;
    }

    public boolean passwordMatches(UrlCacheEntry entry, String password) {
        UrlEntity entity = urls.findByShortCodeIgnoreCase(ShortCodes.normalize(entry.shortCode()))
                .orElse(null);
        if (entity == null || entity.getPasswordHash() == null) {
            return false;
        }
        return password != null && passwords.matches(password, entity.getPasswordHash());
    }

    UrlCacheEntry resolveEntry(String code) {
        String normalized = ShortCodes.normalize(code);
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short link not found");
        }
        if (cache.isNegative(normalized)) {
            return null;
        }
        var cached = cache.getPositive(normalized);
        if (cached.isPresent()) {
            return cached.get();
        }
        return urls.findByShortCodeIgnoreCase(normalized)
                .map(entity -> {
                    UrlCacheEntry entry = toCache(entity);
                    cache.putPositive(entry);
                    return entry;
                })
                .orElseGet(() -> {
                    cache.putNegative(normalized);
                    return null;
                });
    }

    private UrlEntity requireOwned(String code, AuthPrincipal principal) {
        requireRead(principal);
        UrlEntity entity = urls.findByShortCodeIgnoreCase(ShortCodes.normalize(code))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short link not found"));
        if (!isOwner(entity, principal)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "You do not own this link");
        }
        return entity;
    }

    private static boolean isOwner(UrlEntity entity, AuthPrincipal principal) {
        return principal != null
                && entity.getOwnerId() != null
                && entity.getOwnerId().equals(principal.userId());
    }

    private static void requireRead(AuthPrincipal principal) {
        if (principal == null || !principal.canRead()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required");
        }
    }

    private static void requireWrite(AuthPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required");
        }
        if (!principal.canWrite()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "forbidden", "write scope required");
        }
    }

    private UrlEntity persistWithRetry(
            String destination, String assigned, boolean custom, CreateUrlRequest request, Long ownerId) {
        int max = props.shortCode().maxRetries();
        DataIntegrityViolationException last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            String code = custom ? assigned : codes.next(props.shortCode().length());
            try {
                return urls.saveAndFlush(newEntity(destination, code, custom, request, ownerId));
            } catch (DataIntegrityViolationException e) {
                last = e;
                if (custom) {
                    throw new ApiException(HttpStatus.CONFLICT, "alias_taken", "That custom code is already in use");
                }
            }
        }
        throw new ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "short_code_exhausted",
                "Could not allocate a unique short code",
                last);
    }

    private UrlEntity newEntity(
            String destination, String code, boolean custom, CreateUrlRequest request, Long ownerId) {
        Instant now = Instant.now();
        UrlEntity entity = new UrlEntity();
        entity.setId(ids.nextId());
        entity.setShortCode(code);
        entity.setOwnerId(ownerId);
        entity.setDestinationUrl(destination);
        entity.setCustomAlias(custom);
        entity.setActive(true);
        entity.setExpiresAt(request.expiresAt());
        entity.setMaxClicks(request.maxClicks());
        entity.setTags(request.tags() == null ? List.of() : request.tags());
        entity.setPublicClickCount(Boolean.TRUE.equals(request.publicClickCount()));
        entity.setHidePreview(Boolean.TRUE.equals(request.hidePreview()));
        if (request.password() != null && !request.password().isBlank()) {
            entity.setPasswordHash(passwords.encode(request.password()));
        }
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private String issueClaimToken(long urlId) {
        String raw = TokenHasher.randomUrlToken(24);
        GuestClaimTokenEntity row = new GuestClaimTokenEntity();
        row.setTokenHash(TokenHasher.sha256Hex(raw));
        row.setUrlId(urlId);
        row.setExpiresAt(Instant.now().plus(claimTtl));
        row.setUsed(false);
        claims.save(row);
        return raw;
    }

    private UrlCacheEntry toCache(UrlEntity entity) {
        long count = clickCounts.getOrLoad(
                ShortCodes.normalize(entity.getShortCode()), () -> clickRepository.countByUrlId(entity.getId()));
        return new UrlCacheEntry(
                entity.getId(),
                entity.getOwnerId(),
                entity.getShortCode(),
                entity.getDestinationUrl(),
                entity.isActive(),
                entity.getExpiresAt(),
                entity.getMaxClicks(),
                count,
                entity.getPasswordHash() != null && !entity.getPasswordHash().isBlank(),
                entity.isPublicClickCount(),
                entity.isHidePreview(),
                entity.getCreatedAt());
    }

    private UrlView toView(UrlEntity entity, boolean owner) {
        Long clicks = null;
        if (owner || entity.isPublicClickCount()) {
            clicks = clickCounts
                    .get(ShortCodes.normalize(entity.getShortCode()))
                    .orElseGet(() -> clickRepository.countByUrlId(entity.getId()));
        }
        String destination = (owner || !entity.isHidePreview()) ? entity.getDestinationUrl() : null;
        return new UrlView(
                entity.getShortCode(),
                shortUrl(entity.getShortCode()),
                destination,
                entity.getCreatedAt(),
                owner ? entity.getUpdatedAt() : null,
                entity.isActive(),
                entity.getExpiresAt(),
                owner ? entity.getMaxClicks() : null,
                clicks,
                owner ? entity.getTags() : null,
                entity.isPublicClickCount(),
                entity.isHidePreview(),
                owner ? entity.getPasswordHash() != null : entity.getPasswordHash() != null,
                owner ? entity.isCustomAlias() : null,
                owner);
    }

    public String shortUrl(String code) {
        return props.baseUrl().replaceAll("/$", "") + "/s/" + code;
    }

    private static String blankToNull(String tag) {
        return tag == null || tag.isBlank() ? null : tag;
    }
}
