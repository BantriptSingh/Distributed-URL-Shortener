package com.shorty.click;

import com.shorty.config.ClickStreamProperties;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class ClickStreamSupport {

    private static final Logger log = LoggerFactory.getLogger(ClickStreamSupport.class);

    private final StringRedisTemplate redis;
    private final ClickStreamProperties props;

    public ClickStreamSupport(StringRedisTemplate redis, ClickStreamProperties props) {
        this.redis = redis;
        this.props = props;
    }

    /**
     * {@code XGROUP CREATE ... MKSTREAM}. {@code BUSYGROUP} means a previous boot already created
     * the group — that is success, not a startup failure.
     */
    public void ensureGroup() {
        String stream = props.stream();
        String group = props.group();
        try {
            redis.execute((RedisCallback<String>) connection -> {
                connection.streamCommands()
                        .xGroupCreate(
                                stream.getBytes(StandardCharsets.UTF_8),
                                group,
                                ReadOffset.latest(),
                                true);
                return "ok";
            });
            log.info("Redis stream group ready stream={} group={}", stream, group);
        } catch (RuntimeException e) {
            if (isBusyGroup(e)) {
                log.info("Redis stream group already exists stream={} group={}", stream, group);
                return;
            }
            throw e;
        }
    }

    static boolean isBusyGroup(Throwable error) {
        Throwable t = error;
        while (t != null) {
            String msg = t.getMessage();
            String name = t.getClass().getSimpleName();
            if ((msg != null && msg.toUpperCase().contains("BUSYGROUP")) || name.contains("Busy")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }
}
