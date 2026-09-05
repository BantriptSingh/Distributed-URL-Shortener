package com.shorty.click;

import java.util.Locale;
import java.util.Set;

public final class UserAgentClassifier {

    public record Classification(String deviceType, String browser, String os, boolean bot) {}

    private static final Set<String> BOT_TOKENS = Set.of(
            "bot",
            "crawler",
            "spider",
            "slurp",
            "curl/",
            "wget",
            "facebookexternalhit",
            "pingdom",
            "monitoring",
            "python-requests",
            "go-http-client",
            "httpunit",
            "scrapy");

    public Classification classify(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return new Classification("desktop", "other", "other", false);
        }
        String ua = userAgent;
        String lower = ua.toLowerCase(Locale.ROOT);
        boolean bot = BOT_TOKENS.stream().anyMatch(lower::contains);
        String device = device(lower);
        String browser = browser(lower);
        String os = os(lower);
        return new Classification(device, browser, os, bot);
    }

    private static String device(String lower) {
        if (lower.contains("ipad") || lower.contains("tablet")) {
            return "tablet";
        }
        if (lower.contains("mobi") || lower.contains("android") || lower.contains("iphone")) {
            return "mobile";
        }
        return "desktop";
    }

    private static String browser(String lower) {
        if (lower.contains("edg/")) {
            return "edge";
        }
        if (lower.contains("chrome/") && !lower.contains("chromium")) {
            return "chrome";
        }
        if (lower.contains("firefox/")) {
            return "firefox";
        }
        if (lower.contains("safari/") && !lower.contains("chrome")) {
            return "safari";
        }
        return "other";
    }

    private static String os(String lower) {
        if (lower.contains("windows")) {
            return "windows";
        }
        if (lower.contains("android")) {
            return "android";
        }
        if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ios")) {
            return "ios";
        }
        if (lower.contains("mac os") || lower.contains("macintosh")) {
            return "macos";
        }
        if (lower.contains("linux")) {
            return "linux";
        }
        return "other";
    }
}
