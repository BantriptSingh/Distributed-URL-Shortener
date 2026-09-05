package com.shorty.analytics;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsQueryService {

    public enum Granularity {
        HOUR,
        DAY,
        WEEK
    }

    public record CountRow(String key, long count) {}

    public record SeriesPoint(Instant bucket, long count) {}

    public record LinkAnalytics(
            long totalClicks,
            long realClicks,
            long botClicks,
            List<SeriesPoint> series,
            List<CountRow> countries,
            List<CountRow> devices,
            List<CountRow> browsers,
            List<CountRow> operatingSystems,
            List<CountRow> referrers
    ) {}

    @PersistenceContext
    private EntityManager em;

    public LinkAnalytics forLink(long urlId, Instant from, Instant to, Granularity groupBy) {
        long total = scalar("SELECT COUNT(*) FROM clicks WHERE url_id = :id AND timestamp >= :from AND timestamp < :to", urlId, from, to);
        long bots = scalar(
                "SELECT COUNT(*) FROM clicks WHERE url_id = :id AND timestamp >= :from AND timestamp < :to AND is_bot = TRUE",
                urlId,
                from,
                to);
        String trunc = switch (groupBy) {
            case HOUR -> "hour";
            case WEEK -> "week";
            case DAY -> "day";
        };
        @SuppressWarnings("unchecked")
        List<Object[]> seriesRows = em.createNativeQuery(
                        "SELECT date_trunc('" + trunc + "', timestamp) AS bucket, COUNT(*) "
                                + "FROM clicks WHERE url_id = :id AND timestamp >= :from AND timestamp < :to "
                                + "AND is_bot = FALSE GROUP BY 1 ORDER BY 1")
                .setParameter("id", urlId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        List<SeriesPoint> series = seriesRows.stream()
                .map(r -> new SeriesPoint(toInstant(r[0]), ((Number) r[1]).longValue()))
                .toList();
        return new LinkAnalytics(
                total,
                total - bots,
                bots,
                series,
                breakdown(urlId, from, to, "country"),
                breakdown(urlId, from, to, "device_type"),
                breakdown(urlId, from, to, "browser"),
                breakdown(urlId, from, to, "os"),
                breakdown(urlId, from, to, "COALESCE(referrer, '(direct)')"));
    }

    private List<CountRow> breakdown(long urlId, Instant from, Instant to, String expr) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT " + expr + " AS k, COUNT(*) FROM clicks "
                                + "WHERE url_id = :id AND timestamp >= :from AND timestamp < :to AND is_bot = FALSE "
                                + "GROUP BY 1 ORDER BY 2 DESC LIMIT 20")
                .setParameter("id", urlId)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        return rows.stream()
                .map(r -> new CountRow(r[0] == null ? "(none)" : String.valueOf(r[0]), ((Number) r[1]).longValue()))
                .toList();
    }

    private long scalar(String sql, long urlId, Instant from, Instant to) {
        Query q = em.createNativeQuery(sql)
                .setParameter("id", urlId)
                .setParameter("from", from)
                .setParameter("to", to);
        Object v = q.getSingleResult();
        return v == null ? 0L : ((Number) v).longValue();
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) {
            return i;
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (value instanceof java.util.Date d) {
            return d.toInstant();
        }
        return Instant.parse(value.toString());
    }

    public DashboardSummary summary(long ownerId) {
        Number links = (Number) em.createNativeQuery("SELECT COUNT(*) FROM urls WHERE owner_id = :oid")
                .setParameter("oid", ownerId)
                .getSingleResult();
        Number clicks = (Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM clicks c JOIN urls u ON u.id = c.url_id WHERE u.owner_id = :oid")
                .setParameter("oid", ownerId)
                .getSingleResult();
        @SuppressWarnings("unchecked")
        List<Object[]> top = em.createNativeQuery(
                        "SELECT u.short_code, COUNT(c.id) FROM urls u LEFT JOIN clicks c ON c.url_id = u.id "
                                + "WHERE u.owner_id = :oid GROUP BY u.short_code ORDER BY COUNT(c.id) DESC LIMIT 5")
                .setParameter("oid", ownerId)
                .getResultList();
        @SuppressWarnings("unchecked")
        List<Object[]> recent = em.createNativeQuery(
                        "SELECT u.short_code, c.timestamp FROM clicks c JOIN urls u ON u.id = c.url_id "
                                + "WHERE u.owner_id = :oid ORDER BY c.timestamp DESC LIMIT 10")
                .setParameter("oid", ownerId)
                .getResultList();
        return new DashboardSummary(
                links == null ? 0 : links.longValue(),
                clicks == null ? 0 : clicks.longValue(),
                top.stream()
                        .map(r -> new CountRow(String.valueOf(r[0]), ((Number) r[1]).longValue()))
                        .toList(),
                recent.stream()
                        .map(r -> new SeriesPoint(toInstant(r[1]), 1))
                        .toList());
    }

    public record DashboardSummary(long links, long totalClicks, List<CountRow> topLinks, List<SeriesPoint> recent) {}
}
