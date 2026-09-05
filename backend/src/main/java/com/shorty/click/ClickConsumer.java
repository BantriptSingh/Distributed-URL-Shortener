package com.shorty.click;

import com.shorty.analytics.LiveClickHub;
import com.shorty.config.ClickStreamProperties;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * One Redis Streams consumer. Safe to construct twice against the same group.
 * Idempotency is {@code clicks.stream_id}.
 */
public class ClickConsumer {

    private static final Logger log = LoggerFactory.getLogger(ClickConsumer.class);

    private final StringRedisTemplate redis;
    private final ClickWriter writer;
    private final LiveClickHub liveHub;
    private final ClickStreamProperties props;
    private final String consumerName;
    private final Duration block;

    public ClickConsumer(
            StringRedisTemplate redis,
            ClickWriter writer,
            LiveClickHub liveHub,
            ClickStreamProperties props,
            String consumerName,
            Duration block) {
        this.redis = redis;
        this.writer = writer;
        this.liveHub = liveHub;
        this.props = props;
        this.consumerName = consumerName;
        this.block = block;
    }

    public int drainBatch() {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream()
                .read(
                        Consumer.from(props.group(), consumerName),
                        StreamReadOptions.empty().count(32).block(block),
                        StreamOffset.create(props.stream(), ReadOffset.lastConsumed()));
        if (records == null || records.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (MapRecord<String, Object, Object> record : records) {
            if (handle(record)) {
                n++;
            }
        }
        return n;
    }

    public void claimIdle(Duration minIdle) {
        PendingMessages pending = redis.opsForStream()
                .pending(props.stream(), props.group(), Range.unbounded(), 50L);
        if (pending == null || pending.isEmpty()) {
            return;
        }
        RecordId[] steal = pending.stream()
                .filter(msg -> idleMillis(msg) >= minIdle.toMillis())
                .map(PendingMessage::getId)
                .toArray(RecordId[]::new);
        if (steal.length == 0) {
            return;
        }
        List<MapRecord<String, Object, Object>> claimed =
                redis.opsForStream().claim(props.stream(), props.group(), consumerName, minIdle, steal);
        if (claimed == null) {
            return;
        }
        for (MapRecord<String, Object, Object> record : claimed) {
            handle(record);
        }
    }

    boolean handle(MapRecord<String, Object, Object> record) {
        String streamId = record.getId().getValue();
        try {
            var result = writer.persistIfNew(streamId, record.getValue());
            ack(streamId);
            if (result.inserted()) {
                liveHub.pulse();
                liveHub.pulseCode(result.shortCode());
            }
            return result.inserted();
        } catch (RuntimeException e) {
            log.warn("Click persist failed streamId={} consumer={}", streamId, consumerName, e);
            return false;
        }
    }

    private void ack(String streamId) {
        redis.opsForStream().acknowledge(props.stream(), props.group(), RecordId.of(streamId));
    }

    private static long idleMillis(PendingMessage msg) {
        Duration idle = msg.getElapsedTimeSinceLastDelivery();
        return idle == null ? 0L : idle.toMillis();
    }
}
