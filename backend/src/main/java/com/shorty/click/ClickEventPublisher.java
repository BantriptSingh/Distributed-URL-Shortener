package com.shorty.click;

import com.shorty.config.ClickStreamProperties;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClickEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);

    private final StringRedisTemplate redis;
    private final ClickStreamProperties props;

    public ClickEventPublisher(StringRedisTemplate redis, ClickStreamProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /** Fail-open: redirect must not fail if Redis stream write fails. */
    public void publish(ClickEvent event) {
        try {
            Map<String, String> body = new HashMap<>();
            body.put("urlId", Long.toString(event.urlId()));
            body.put("shortCode", event.shortCode());
            body.put("ip", event.ip() == null ? "" : event.ip());
            body.put("userAgent", event.userAgent() == null ? "" : event.userAgent());
            body.put("referrer", event.referrer() == null ? "" : event.referrer());
            body.put("country", event.country());
            body.put("ts", event.timestamp().toString());
            body.put("deviceType", event.ua().deviceType());
            body.put("browser", event.ua().browser());
            body.put("os", event.ua().os());
            body.put("isBot", Boolean.toString(event.ua().bot()));
            MapRecord<String, String, String> record =
                    StreamRecords.mapBacked(body).withStreamKey(props.stream());
            XAddOptions options = XAddOptions.maxlen(props.maxlen()).approximateTrimming(true);
            redis.opsForStream().add(record, options);
        } catch (RuntimeException e) {
            log.error("Failed to enqueue click event urlId={}", event.urlId(), e);
        }
    }
}
