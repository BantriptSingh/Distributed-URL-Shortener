package com.shorty.click;

import java.time.Instant;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClickWriter {

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
     * @return true if a new row was inserted
     */
    @Transactional
    public boolean persistIfNew(String streamId, Map<Object, Object> body) {
        if (clicks.existsByStreamId(streamId)) {
            return false;
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
            return true;
        } catch (DataIntegrityViolationException dup) {
            return false;
        }
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
