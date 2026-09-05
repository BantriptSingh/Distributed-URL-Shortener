package com.shorty.geo;

import jakarta.servlet.http.HttpServletRequest;

public interface GeoResolver {

    /**
     * Best-effort ISO country code from the current request. Never throws.
     * Unknown → {@code ZZ}.
     */
    String resolve(HttpServletRequest request);
}
