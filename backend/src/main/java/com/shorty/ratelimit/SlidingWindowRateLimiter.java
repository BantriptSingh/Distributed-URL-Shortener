package com.shorty.ratelimit;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

@Component
public class SlidingWindowRateLimiter {

    public record Decision(boolean allowed, int retryAfterSeconds) {
        static Decision allow() {
            return new Decision(true, 0);
        }

        static Decision deny(int retryAfterSeconds) {
            return new Decision(false, Math.max(1, retryAfterSeconds));
        }
    }

    private static final Logger log = LoggerFactory.getLogger(SlidingWindowRateLimiter.class);

    private final StringRedisTemplate redis;

    public SlidingWindowRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Decision check(String bucket, int limit, Duration window) {
        if (limit <= 0) {
            return Decision.allow();
        }
        String key = "rl:" + bucket;
        long now = System.currentTimeMillis();
        long windowMs = window.toMillis();
        long cutoff = now - windowMs;
        try {
            ZSetOperations<String, String> zset = redis.opsForZSet();
            zset.removeRangeByScore(key, 0, cutoff);
            Long count = zset.zCard(key);
            if (count != null && count >= limit) {
                int retry = retryAfterSeconds(zset, key, windowMs, now);
                return Decision.deny(retry);
            }
            zset.add(key, UUID.randomUUID().toString(), now);
            redis.expire(key, window.plusSeconds(2));
            return Decision.allow();
        } catch (RuntimeException e) {
            log.error("rate_limit_degraded=true bucket={} reason={}", bucket, e.toString());
            return Decision.allow();
        }
    }

    private static int retryAfterSeconds(
            ZSetOperations<String, String> zset, String key, long windowMs, long now) {
        Set<ZSetOperations.TypedTuple<String>> oldest = zset.rangeWithScores(key, 0, 0);
        if (oldest == null || oldest.isEmpty()) {
            return 1;
        }
        Double score = oldest.iterator().next().getScore();
        if (score == null) {
            return 1;
        }
        long retryMs = (long) (score + windowMs - now);
        return (int) Math.max(1, (retryMs + 999) / 1000);
    }
}
