package com.shorty.url;

import com.shorty.analytics.AnalyticsQueryService;
import com.shorty.analytics.AnalyticsQueryService.Granularity;
import com.shorty.analytics.AnalyticsQueryService.LinkAnalytics;
import com.shorty.analytics.LiveClickHub;
import com.shorty.auth.AuthPrincipal;
import com.shorty.auth.SecuritySupport;
import com.shorty.auth.UnlockTokenService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    public record UnlockRequest(String password) {}

    public record UnlockResponse(String unlockToken, Instant expiresAt) {}

    public record ClaimRequest(String claimToken) {}

    private final UrlService urls;
    private final QrCodeService qr;
    private final UnlockTokenService unlocks;
    private final AnalyticsQueryService analytics;
    private final LiveClickHub live;

    public UrlController(
            UrlService urls,
            QrCodeService qr,
            UnlockTokenService unlocks,
            AnalyticsQueryService analytics,
            LiveClickHub live) {
        this.urls = urls;
        this.qr = qr;
        this.unlocks = unlocks;
        this.analytics = analytics;
        this.live = live;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUrlResponse create(@Valid @RequestBody CreateUrlRequest request) {
        AuthPrincipal principal = SecuritySupport.current().orElse(null);
        if (principal != null && !principal.canWrite()) {
            throw new com.shorty.api.ApiException(HttpStatus.FORBIDDEN, "forbidden", "write scope required");
        }
        Long ownerId = principal == null ? null : principal.userId();
        return urls.create(request, ownerId);
    }

    @GetMapping
    public Page<UrlView> list(
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return urls.listOwned(SecuritySupport.requireUser(), active, from, to, tag, page, size, sort);
    }

    @GetMapping("/{code}")
    public UrlView get(@PathVariable String code) {
        return urls.getView(code, SecuritySupport.current().orElse(null));
    }

    @PatchMapping("/{code}")
    public UrlView patch(@PathVariable String code, @RequestBody PatchUrlRequest request) {
        return urls.patch(code, request, SecuritySupport.requireUser());
    }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code, @RequestParam(defaultValue = "false") boolean hard) {
        urls.delete(code, hard, SecuritySupport.requireUser());
    }

    @GetMapping(value = "/{code}/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] qr(@PathVariable String code) {
        urls.requireActive(code);
        return qr.pngFor(code);
    }

    @GetMapping("/{code}/analytics")
    public LinkAnalytics analytics(
            @PathVariable String code,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "day") String groupBy) {
        var entity = urls.requireOwnedEntity(code, SecuritySupport.requireUser());
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(30, ChronoUnit.DAYS) : from;
        Granularity g = switch (groupBy.toLowerCase()) {
            case "hour" -> Granularity.HOUR;
            case "week" -> Granularity.WEEK;
            default -> Granularity.DAY;
        };
        return analytics.forLink(entity.getId(), start, end, g);
    }

    @GetMapping(value = "/{code}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String code) {
        var entity = urls.requireOwnedEntity(code, SecuritySupport.requireUser());
        return live.subscribeCode(entity.getShortCode());
    }

    @PostMapping("/{code}/claim")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void claim(@PathVariable String code, @RequestBody ClaimRequest body) {
        urls.claim(code, body.claimToken(), SecuritySupport.requireUser());
    }

    @PostMapping("/{code}/unlock")
    public ResponseEntity<UnlockResponse> unlock(@PathVariable String code, @RequestBody UnlockRequest body) {
        var entry = urls.requireActive(code);
        if (!entry.passwordProtected() || !urls.passwordMatches(entry, body.password())) {
            throw new com.shorty.api.ApiException(HttpStatus.UNAUTHORIZED, "bad_password", "Incorrect password");
        }
        String token = unlocks.issue(entry.shortCode());
        return ResponseEntity.ok()
                .header("Set-Cookie", "unlock=" + token + "; Path=/; Max-Age=300; HttpOnly; SameSite=Lax")
                .body(new UnlockResponse(token, Instant.now().plusSeconds(300)));
    }
}
