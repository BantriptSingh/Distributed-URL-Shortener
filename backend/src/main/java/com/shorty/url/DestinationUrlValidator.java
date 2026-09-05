package com.shorty.url;

import com.shorty.api.ApiException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DestinationUrlValidator {

    private static final Set<String> ALLOWED = Set.of("http", "https");

    @FunctionalInterface
    interface HostLookup {
        InetAddress[] lookup(String host) throws UnknownHostException;
    }

    private final UrlReputation reputation;
    private final HostLookup hosts;

    @org.springframework.beans.factory.annotation.Autowired
    public DestinationUrlValidator(UrlReputation reputation) {
        this(reputation, InetAddress::getAllByName);
    }

    DestinationUrlValidator(UrlReputation reputation, HostLookup hosts) {
        this.reputation = reputation;
        this.hosts = hosts;
    }

    public String validate(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "destinationUrl is required");
        }
        String trimmed = raw.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "destinationUrl is not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(scheme)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "invalid_url", "Only http and https URLs are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "destinationUrl must include a host");
        }
        if (reputation.isBlocked(host)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "blocked_url", "This destination is not allowed");
        }
        rejectPrivateOrMetadata(host);
        return trimmed;
    }

    private void rejectPrivateOrMetadata(String host) {
        InetAddress[] resolved;
        try {
            resolved = hosts.lookup(host);
        } catch (UnknownHostException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "destinationUrl host could not be resolved");
        }
        if (resolved == null || resolved.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_url", "destinationUrl host could not be resolved");
        }
        for (InetAddress address : resolved) {
            if (SsrfAddresses.blocked(address)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "invalid_url", "destinationUrl must not target a private or metadata address");
            }
        }
    }
}
