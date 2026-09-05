package com.shorty.click;

import com.shorty.analytics.LiveClickHub;
import com.shorty.config.ClickStreamProperties;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class ClickConsumerRuntime implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClickConsumerRuntime.class);

    private final ClickStreamProperties props;
    private final ClickStreamSupport streams;
    private final ClickConsumer consumer;
    private volatile boolean running;
    private Thread thread;

    public ClickConsumerRuntime(
            StringRedisTemplate redis,
            ClickWriter writer,
            LiveClickHub liveHub,
            ClickStreamProperties props,
            ClickStreamSupport streams,
            @Value("${INSTANCE_ID:}") String instanceId) {
        this.props = props;
        this.streams = streams;
        String name = "click-"
                + (instanceId == null || instanceId.isBlank() ? UUID.randomUUID().toString() : instanceId);
        this.consumer = new ClickConsumer(redis, writer, liveHub, props, name, props.block());
    }

    @Override
    public void run(ApplicationArguments args) {
        streams.ensureGroup();
        if (!props.consumerEnabled()) {
            log.info("Click stream consumer disabled");
            return;
        }
        running = true;
        thread = new Thread(this::loop, "click-consumer");
        thread.setDaemon(true);
        thread.start();
        log.info("Click stream consumer started");
    }

    private void loop() {
        while (running) {
            try {
                consumer.drainBatch();
            } catch (RuntimeException e) {
                if (!running) {
                    return;
                }
                log.warn("Click consumer loop error", e);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "15000")
    public void reclaim() {
        if (!running) {
            return;
        }
        try {
            consumer.claimIdle(props.claimIdle());
        } catch (RuntimeException e) {
            log.warn("XAUTOCLAIM sweep failed", e);
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
