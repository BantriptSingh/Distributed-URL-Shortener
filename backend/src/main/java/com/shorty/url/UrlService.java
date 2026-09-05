package com.shorty.url;

import com.shorty.api.ApiException;
import com.shorty.click.ClickCountStore;
import com.shorty.click.ClickRepository;
import com.shorty.config.AppProperties;
import com.shorty.id.ShortCodeGenerator;
import com.shorty.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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

    public UrlService(
            UrlRepository urls,
            UrlCache cache,
            ShortCodeGenerator codes,
            SnowflakeIdGenerator ids,
            DestinationUrlValidator destinations,
            CustomAliasValidator aliases,
            AppProperties props,
            ClickCountStore clickCounts,
            ClickRepository clickRepository) {
        this.urls = urls;
        this.cache = cache;
        this.codes = codes;
        this.ids = ids;
        this.destinations = destinations;
        this.aliases = aliases;
        this.props = props;
        this.clickCounts = clickCounts;
        this.clickRepository = clickRepository;
    }

    @Transactional
    public CreateUrlResponse create(CreateUrlRequest request) {
        String destination = destinations.validate(request.destinationUrl());
        boolean custom = request.customCode() != null && !request.customCode().isBlank();
        String assigned = custom ? aliases.validateAndNormalize(request.customCode()) : null;
        if (custom && urls.existsByShortCodeIgnoreCase(assigned)) {
            throw new ApiException(HttpStatus.CONFLICT, "alias_taken", "That custom code is already in use");
        }

        UrlEntity saved = persistWithRetry(destination, assigned, custom, request);
        clickCounts.set(ShortCodes.normalize(saved.getShortCode()), 0);
        UrlCacheEntry entry = toCache(saved);
        cache.putPositive(entry);
        return new CreateUrlResponse(
                saved.getShortCode(),
                shortUrl(saved.getShortCode()),
                saved.getDestinationUrl(),
                saved.getCreatedAt(),
                saved.getExpiresAt(),
                saved.getMaxClicks(),
                saved.getTags(),
                saved.isPublicClickCount());
    }

    public PublicUrlResponse getPublic(String code) {
        UrlCacheEntry entry = resolveEntry(code);
        if (entry == null || !entry.active()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short link not found");
        }
        Long clicks = null;
        if (entry.publicClickCount()) {
            clicks = clickCounts
                    .get(ShortCodes.normalize(entry.shortCode()))
                    .orElse(entry.clickCount());
        }
        return new PublicUrlResponse(
                entry.shortCode(), entry.createdAt(), entry.active(), entry.expiresAt(), clicks);
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

    public String resolveRedirectTarget(String code) {
        UrlCacheEntry entry = requireActive(code);
        Instant now = Instant.now();
        if (entry.expired(now) || entry.maxClicksReached()) {
            throw new ApiException(HttpStatus.GONE, "gone", "This short link is no longer available");
        }
        if (entry.passwordProtected()) {
            return unlockUrl(entry.shortCode());
        }
        return entry.destinationUrl();
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

    private UrlEntity persistWithRetry(String destination, String assigned, boolean custom, CreateUrlRequest request) {
        int max = props.shortCode().maxRetries();
        DataIntegrityViolationException last = null;
        for (int attempt = 1; attempt <= max; attempt++) {
            String code = custom ? assigned : codes.next(props.shortCode().length());
            try {
                return urls.saveAndFlush(newEntity(destination, code, custom, request));
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

    private UrlEntity newEntity(String destination, String code, boolean custom, CreateUrlRequest request) {
        Instant now = Instant.now();
        UrlEntity entity = new UrlEntity();
        entity.setId(ids.nextId());
        entity.setShortCode(code);
        entity.setDestinationUrl(destination);
        entity.setCustomAlias(custom);
        entity.setActive(true);
        entity.setExpiresAt(request.expiresAt());
        entity.setMaxClicks(request.maxClicks());
        entity.setTags(request.tags() == null ? List.of() : request.tags());
        entity.setPublicClickCount(Boolean.TRUE.equals(request.publicClickCount()));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private UrlCacheEntry toCache(UrlEntity entity) {
        long count = clickCounts.getOrLoad(
                ShortCodes.normalize(entity.getShortCode()), () -> clickRepository.countByUrlId(entity.getId()));
        return new UrlCacheEntry(
                entity.getId(),
                entity.getShortCode(),
                entity.getDestinationUrl(),
                entity.isActive(),
                entity.getExpiresAt(),
                entity.getMaxClicks(),
                count,
                entity.getPasswordHash() != null && !entity.getPasswordHash().isBlank(),
                entity.isPublicClickCount(),
                entity.getCreatedAt());
    }

    private String shortUrl(String code) {
        return props.baseUrl().replaceAll("/$", "") + "/s/" + code;
    }
}
