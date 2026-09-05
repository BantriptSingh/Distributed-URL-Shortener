package com.shorty.click;

import java.util.OptionalLong;
import java.util.function.LongSupplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClickCountStore {

    static final String PREFIX = "url:clicks:";

    private final StringRedisTemplate redis;

    public ClickCountStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public long increment(String normalizedCode) {
        Long v = redis.opsForValue().increment(PREFIX + normalizedCode);
        return v == null ? 0L : v;
    }

    public void decrement(String normalizedCode) {
        Long v = redis.opsForValue().increment(PREFIX + normalizedCode, -1);
        if (v != null && v < 0) {
            set(normalizedCode, 0);
        }
    }

    public void set(String normalizedCode, long n) {
        redis.opsForValue().set(PREFIX + normalizedCode, Long.toString(n));
    }

    public OptionalLong get(String normalizedCode) {
        String v = redis.opsForValue().get(PREFIX + normalizedCode);
        if (v == null) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Long.parseLong(v));
    }

    public long getOrLoad(String normalizedCode, LongSupplier dbCount) {
        OptionalLong cached = get(normalizedCode);
        if (cached.isPresent()) {
            return cached.getAsLong();
        }
        long n = dbCount.getAsLong();
        set(normalizedCode, n);
        return n;
    }
}
