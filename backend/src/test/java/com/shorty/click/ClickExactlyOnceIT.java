package com.shorty.click;

import static org.assertj.core.api.Assertions.assertThat;

import com.shorty.analytics.AnalyticsQueryService;
import com.shorty.analytics.AnalyticsQueryService.Granularity;
import com.shorty.analytics.LiveClickHub;
import com.shorty.config.ClickStreamProperties;
import com.shorty.support.HighLimitIT;
import com.shorty.url.UrlEntity;
import com.shorty.url.UrlRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

@SpringBootTest(
        properties = {
            "app.clicks.consumer-enabled=false",
            "app.clicks.stream=clicks-once",
            "app.clicks.group=click-workers-once"
        })
class ClickExactlyOnceIT extends HighLimitIT {

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    ClickWriter writer;

    @Autowired
    LiveClickHub liveHub;

    @Autowired
    ClickStreamProperties streamProps;

    @Autowired
    ClickStreamSupport streamSupport;

    @Autowired
    ClickEventPublisher publisher;

    @Autowired
    UrlRepository urls;

    @Autowired
    ClickRepository clicks;

    @Autowired
    AnalyticsQueryService analytics;

    @Autowired
    com.shorty.id.SnowflakeIdGenerator ids;

    @Test
    void ensureGroupIsIdempotentOnBusyGroup() {
        streamSupport.ensureGroup();
        streamSupport.ensureGroup();
    }

    @Test
    void twoConsumersProcessEachMessageOnce() throws Exception {
        streamSupport.ensureGroup();
        UrlEntity url = persistUrl("once01a");
        int n = 40;
        Instant now = Instant.now();
        var ua = new UserAgentClassifier().classify("Mozilla/5.0");
        for (int i = 0; i < n; i++) {
            publisher.publish(new ClickEvent(
                    url.getId(),
                    url.getShortCode(),
                    "203.0.113." + (i % 10),
                    "Mozilla/5.0",
                    "https://ref.example",
                    "US",
                    now,
                    ua));
        }

        ClickConsumer a = new ClickConsumer(
                redis, writer, liveHub, streamProps, "click-test-a", Duration.ofMillis(200));
        ClickConsumer b = new ClickConsumer(
                redis, writer, liveHub, streamProps, "click-test-b", Duration.ofMillis(200));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Instant deadline = Instant.now().plusSeconds(20);
        pool.submit(() -> drainUntil(a, deadline));
        pool.submit(() -> drainUntil(b, deadline));
        pool.shutdown();
        assertThat(pool.awaitTermination(25, TimeUnit.SECONDS)).isTrue();

        var rows = clicks.findAll().stream().filter(c -> c.getUrlId().equals(url.getId())).toList();
        assertThat(rows).hasSize(n);
        assertThat(rows.stream().map(ClickEntity::getStreamId).distinct()).hasSize(n);
        rows.forEach(row -> {
            assertThat(row.getIpHash()).hasSize(64);
            assertThat(row.getIpHash()).doesNotStartWith("203.0.113");
            assertThat(row.getCountry()).isEqualTo("US");
        });

        Instant from = now.minus(1, ChronoUnit.HOURS);
        Instant to = now.plus(1, ChronoUnit.HOURS);
        var snap = analytics.forLink(url.getId(), from, to, Granularity.DAY);
        assertThat(snap.totalClicks()).isEqualTo(n);
        assertThat(snap.realClicks()).isEqualTo(n);
        assertThat(snap.botClicks()).isZero();
        assertThat(snap.countries()).anyMatch(c -> c.key().equals("US") && c.count() == n);
    }

    @Test
    void redeliveryDoesNotDuplicateRows() {
        streamSupport.ensureGroup();
        UrlEntity url = persistUrl("once02b");
        var ua = new UserAgentClassifier().classify("curl/8.0");
        publisher.publish(new ClickEvent(
                url.getId(),
                url.getShortCode(),
                "198.51.100.9",
                "curl/8.0",
                null,
                "FR",
                Instant.now(),
                ua));

        ClickConsumer first = new ClickConsumer(
                redis, writer, liveHub, streamProps, "click-redeliver-a", Duration.ofMillis(500));
        assertThat(first.drainBatch()).isEqualTo(1);
        // Simulate crash-before-ack by not being able to insert twice: persist same stream ids via claim
        first.claimIdle(Duration.ZERO);
        assertThat(clicks.countByUrlId(url.getId())).isEqualTo(1);
        assertThat(clicks.countByUrlIdAndBotTrue(url.getId())).isEqualTo(1);
    }

    private static void drainUntil(ClickConsumer consumer, Instant deadline) {
        while (Instant.now().isBefore(deadline)) {
            consumer.drainBatch();
        }
    }

    private UrlEntity persistUrl(String code) {
        UrlEntity entity = new UrlEntity();
        entity.setId(ids.nextId());
        entity.setShortCode(code);
        entity.setDestinationUrl("https://example.com/" + code);
        entity.setCustomAlias(true);
        entity.setActive(true);
        entity.setTags(java.util.List.of());
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return urls.saveAndFlush(entity);
    }
}
