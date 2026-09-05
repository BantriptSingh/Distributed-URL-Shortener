package com.shorty.url;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shorty.config.AppProperties;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisUrlCache implements UrlCache {

    static final String POSITIVE_PREFIX = "url:";
    static final String NEGATIVE_PREFIX = "url:miss:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public RedisUrlCache(StringRedisTemplate redis, ObjectMapper objectMapper, AppProperties props) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.props = props;
    }

    @Override
    public Optional<UrlCacheEntry> getPositive(String normalizedCode) {
        String json = redis.opsForValue().get(POSITIVE_PREFIX + normalizedCode);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, UrlCacheEntry.class));
        } catch (JsonProcessingException e) {
            redis.delete(POSITIVE_PREFIX + normalizedCode);
            return Optional.empty();
        }
    }

    @Override
    public boolean isNegative(String normalizedCode) {
        return Boolean.TRUE.equals(redis.hasKey(NEGATIVE_PREFIX + normalizedCode));
    }

    @Override
    public void putPositive(UrlCacheEntry entry) {
        String key = POSITIVE_PREFIX + ShortCodes.normalize(entry.shortCode());
        try {
            redis.opsForValue()
                    .set(key, objectMapper.writeValueAsString(entry), props.cache().positiveTtl());
            redis.delete(NEGATIVE_PREFIX + ShortCodes.normalize(entry.shortCode()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize cache entry", e);
        }
    }

    @Override
    public void putNegative(String normalizedCode) {
        redis.opsForValue().set(NEGATIVE_PREFIX + normalizedCode, "1", props.cache().negativeTtl());
    }

    @Override
    public void invalidate(String normalizedCode) {
        redis.delete(POSITIVE_PREFIX + normalizedCode);
        redis.delete(NEGATIVE_PREFIX + normalizedCode);
    }
}
