package com.shorty.url;

import java.util.Optional;

public interface UrlCache {

    Optional<UrlCacheEntry> getPositive(String normalizedCode);

    boolean isNegative(String normalizedCode);

    void putPositive(UrlCacheEntry entry);

    void putNegative(String normalizedCode);

    void invalidate(String normalizedCode);
}
