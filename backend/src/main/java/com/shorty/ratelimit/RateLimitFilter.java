package com.shorty.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shorty.auth.AuthPrincipal;
import com.shorty.config.AppProperties;
import com.shorty.web.ClientIps;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration MINUTE = Duration.ofMinutes(1);
    private static final Pattern UNLOCK = Pattern.compile("^/api/v1/urls/([^/]+)/unlock$");

    private final SlidingWindowRateLimiter limiter;
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final Environment environment;

    public RateLimitFilter(
            SlidingWindowRateLimiter limiter, AppProperties props, ObjectMapper mapper, Environment environment) {
        this.limiter = limiter;
        this.props = props;
        this.mapper = mapper;
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        String path = request.getRequestURI();
        String ip = ClientIps.from(request);
        var limits = props.rateLimit();

        if ("POST".equals(method) && isAuthPath(path)) {
            if (deny(response, limiter.check("auth:" + ip, limit("auth-per-minute", limits.authPerMinute()), MINUTE))) {
                return;
            }
        }
        if ("POST".equals(method)) {
            Matcher unlock = UNLOCK.matcher(path);
            if (unlock.matches()) {
                String code = unlock.group(1).toLowerCase(Locale.ROOT);
                if (deny(
                        response,
                        limiter.check(
                                "unlock:" + ip + ":" + code,
                                limit("unlock-per-minute", limits.unlockPerMinute()),
                                MINUTE))) {
                    return;
                }
            }
        }
        if ("GET".equals(method) && path.startsWith("/s/")) {
            if (deny(
                    response,
                    limiter.check("redir:" + ip, limit("redirect-per-minute", limits.redirectPerMinute()), MINUTE))) {
                return;
            }
        }
        if ("POST".equals(method) && "/api/v1/urls".equals(path)) {
            AuthPrincipal principal = principal();
            SlidingWindowRateLimiter.Decision decision;
            if (principal != null && principal.apiKey()) {
                String keyId = principal.apiKeyId() == null ? "unknown" : Long.toString(principal.apiKeyId());
                decision = limiter.check(
                        "keywrite:" + keyId, limit("api-key-write-per-minute", limits.apiKeyWritePerMinute()), MINUTE);
            } else if (principal != null) {
                decision = limiter.check(
                        "jwtcreate:" + principal.userId(),
                        limit("jwt-create-per-minute", limits.jwtCreatePerMinute()),
                        MINUTE);
            } else {
                decision = limiter.check(
                        "guest:" + ip, limit("guest-create-per-minute", limits.guestCreatePerMinute()), MINUTE);
            }
            if (deny(response, decision)) {
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private int limit(String name, int fallback) {
        Integer override = environment.getProperty("it.rate-limit." + name, Integer.class);
        return override != null ? override : fallback;
    }

    private boolean deny(HttpServletResponse response, SlidingWindowRateLimiter.Decision decision) throws IOException {
        if (decision.allowed()) {
            return false;
        }
        response.setStatus(429);
        response.setHeader("Retry-After", Integer.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(
                response.getWriter(),
                Map.of(
                        "code",
                        "rate_limited",
                        "message",
                        "Too many requests",
                        "timestamp",
                        Instant.now().toString()));
        return true;
    }

    private static boolean isAuthPath(String path) {
        return "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/login".equals(path)
                || "/api/v1/auth/refresh".equals(path)
                || "/api/v1/auth/logout".equals(path)
                || "/api/v1/auth/forgot-password".equals(path)
                || "/api/v1/auth/reset-password".equals(path);
    }

    private static AuthPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthPrincipal p) {
            return p;
        }
        return null;
    }
}
