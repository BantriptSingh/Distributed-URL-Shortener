package com.shorty.url;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class UrlReputation {

    private final List<String> blockedHosts;

    public UrlReputation() {
        this.blockedHosts = loadClasspath();
    }

    UrlReputation(List<String> blockedHosts) {
        this.blockedHosts = List.copyOf(blockedHosts);
    }

    public boolean isBlocked(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = normalizeHost(host);
        for (String rule : blockedHosts) {
            if (h.equals(rule) || h.endsWith("." + rule)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Placeholder for Google Safe Browsing (or similar) — not called in Core v1.
     * Wire an HTTP client here later; keep {@link #isBlocked(String)} as the local denylist.
     */
    public boolean lookupSafeBrowsing(String url) {
        return false;
    }

    static String normalizeHost(String host) {
        String h = host.toLowerCase(Locale.ROOT).strip();
        if (h.endsWith(".")) {
            h = h.substring(0, h.length() - 1);
        }
        return h;
    }

    private static List<String> loadClasspath() {
        List<String> rules = new ArrayList<>();
        var resource = new ClassPathResource("denylist.txt");
        try (var in = resource.getInputStream();
                var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                rules.add(normalizeHost(trimmed));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load denylist.txt", e);
        }
        return List.copyOf(rules);
    }
}
