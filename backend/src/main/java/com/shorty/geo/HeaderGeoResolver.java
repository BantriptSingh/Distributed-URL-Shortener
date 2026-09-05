package com.shorty.geo;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Reads Cloudflare / proxy geo headers. Must run on the inbound HTTP thread
 * (redirect producer), not in the stream consumer.
 */
@Component
public class HeaderGeoResolver implements GeoResolver {

    static final String UNKNOWN = "ZZ";

    @Override
    public String resolve(HttpServletRequest request) {
        String raw = firstNonBlank(
                request.getHeader("CF-IPCountry"),
                request.getHeader("X-Geo-Country"),
                request.getHeader("CloudFront-Viewer-Country"));
        if (raw == null) {
            return UNKNOWN;
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (code.length() == 2 && Character.isLetter(code.charAt(0)) && Character.isLetter(code.charAt(1))) {
            return code;
        }
        return UNKNOWN;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
