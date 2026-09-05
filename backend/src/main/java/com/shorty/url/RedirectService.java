package com.shorty.url;

import com.shorty.api.ApiException;
import com.shorty.auth.UnlockTokenService;
import com.shorty.click.ClickCountStore;
import com.shorty.click.ClickEvent;
import com.shorty.click.ClickEventPublisher;
import com.shorty.click.UserAgentClassifier;
import com.shorty.geo.GeoResolver;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class RedirectService {

    private final UrlService urls;
    private final ClickCountStore clickCounts;
    private final ClickEventPublisher publisher;
    private final GeoResolver geo;
    private final UnlockTokenService unlocks;
    private final UserAgentClassifier userAgents = new UserAgentClassifier();

    public RedirectService(
            UrlService urls,
            ClickCountStore clickCounts,
            ClickEventPublisher publisher,
            GeoResolver geo,
            UnlockTokenService unlocks) {
        this.urls = urls;
        this.clickCounts = clickCounts;
        this.publisher = publisher;
        this.geo = geo;
        this.unlocks = unlocks;
    }

    public String locationFor(String code, HttpServletRequest request) {
        UrlCacheEntry entry = urls.requireActive(code);
        Instant now = Instant.now();
        if (entry.expired(now)) {
            throw new ApiException(HttpStatus.GONE, "gone", "This short link is no longer available");
        }
        if (entry.passwordProtected() && !unlocks.valid(entry.shortCode(), unlockToken(request))) {
            return urls.unlockUrl(entry.shortCode());
        }
        if (entry.urlId() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "not_found", "Short link not found");
        }
        String normalized = ShortCodes.normalize(entry.shortCode());
        long n = clickCounts.increment(normalized);
        if (entry.maxClicks() != null && n > entry.maxClicks()) {
            throw new ApiException(HttpStatus.GONE, "gone", "This short link is no longer available");
        }
        String ua = request.getHeader("User-Agent");
        boolean enqueued = publisher.publish(new ClickEvent(
                entry.urlId(),
                entry.shortCode(),
                clientIp(request),
                ua,
                request.getHeader("Referer"),
                geo.resolve(request),
                now,
                userAgents.classify(ua)));
        if (!enqueued) {
            clickCounts.decrement(normalized);
        }
        return entry.destinationUrl();
    }

    static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static String unlockToken(HttpServletRequest request) {
        String q = request.getParameter("u");
        if (q != null && !q.isBlank()) {
            return q;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if ("unlock".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
