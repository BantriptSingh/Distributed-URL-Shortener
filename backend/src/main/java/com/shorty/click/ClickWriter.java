package com.shorty.click;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClickWriter {

    public record PersistResult(boolean inserted, String shortCode) {}

    private final ClickRepository clicks;
    private final com.shorty.id.SnowflakeIdGenerator ids;
    private final IpHasher ipHasher;

    public ClickWriter(
            ClickRepository clicks, com.shorty.id.SnowflakeIdGenerator ids, IpHasher ipHasher) {
        this.clicks = clicks;
        this.ids = ids;
        this.ipHasher = ipHasher;
    }

    /**
     * @return whether a new row was inserted; duplicate {@code stream_id} returns {@code inserted=false}
     *     (safe to ACK). Any other DB error is thrown so the message stays pending.
     */
    @Transactional
    public PersistResult persistIfNew(String streamId, Map<Object, Object> body) {
        if (clicks.existsByStreamId(streamId)) {
            return new PersistResult(false, str(body, "shortCode"));
        }
        ClickEntity row = new ClickEntity();
        row.setId(ids.nextId());
        row.setStreamId(streamId);
        row.setUrlId(Long.parseLong(str(body, "urlId")));
        row.setTimestamp(parseTs(str(body, "ts")));
        row.setIpHash(ipHasher.hash(str(body, "ip")));
        String country = str(body, "country");
        row.setCountry(country.isBlank() ? "ZZ" : country);
        row.setDeviceType(str(body, "deviceType"));
        row.setBrowser(str(body, "browser"));
        row.setOs(str(body, "os"));
        String referrer = str(body, "referrer");
        row.setReferrer(referrer.isBlank() ? null : referrer);
        row.setBot(Boolean.parseBoolean(str(body, "isBot")));
        try {
            clicks.saveAndFlush(row);
            return new PersistResult(true, str(body, "shortCode"));
        } catch (DataIntegrityViolationException e) {
            if (isStreamIdDuplicate(e)) {
                return new PersistResult(false, str(body, "shortCode"));
            }
            throw e;
        }
    }

    static boolean isStreamIdDuplicate(DataIntegrityViolationException error) {
        Throwable t = error;
        while (t != null) {
            if (t instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null && name.toLowerCase(Locale.ROOT).contains("stream_id")) {
                    return true;
                }
            }
            String msg = t.getMessage();
            if (msg != null && msg.toLowerCase(Locale.ROOT).contains("stream_id")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static Instant parseTs(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    private static String str(Map<Object, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? "" : String.valueOf(v);
    }
}
