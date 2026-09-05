package com.shorty.analytics;

import com.shorty.api.ApiException;
import com.shorty.config.AppProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class LiveClickHub {

    private static final Logger log = LoggerFactory.getLogger(LiveClickHub.class);

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> byCode = new ConcurrentHashMap<>();
    private final AtomicInteger liveCount = new AtomicInteger();
    private final int maxLive;

    public LiveClickHub(AppProperties props) {
        this.maxLive = props.liveSseMax();
    }

    public SseEmitter subscribe() {
        if (liveCount.incrementAndGet() > maxLive) {
            liveCount.decrementAndGet();
            throw new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "rate_limited",
                    "Too many live analytics connections",
                    1);
        }
        SseEmitter emitter = new SseEmitter(300_000L);
        emitters.add(emitter);
        Runnable drop = () -> {
            if (emitters.remove(emitter)) {
                liveCount.decrementAndGet();
            }
        };
        emitter.onCompletion(drop);
        emitter.onTimeout(drop);
        emitter.onError(e -> drop.run());
        try {
            emitter.send(SseEmitter.event().name("hello").data("{\"ok\":true}"));
        } catch (IOException e) {
            drop.run();
        }
        return emitter;
    }

    public SseEmitter subscribeCode(String shortCode) {
        String key = shortCode == null ? "" : shortCode.toLowerCase();
        SseEmitter emitter = new SseEmitter(300_000L);
        byCode.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> removeCode(key, emitter));
        emitter.onTimeout(() -> removeCode(key, emitter));
        emitter.onError(e -> removeCode(key, emitter));
        try {
            emitter.send(SseEmitter.event().name("hello").data("{\"ok\":true}"));
        } catch (IOException e) {
            removeCode(key, emitter);
        }
        return emitter;
    }

    public void pulseCode(String shortCode) {
        if (shortCode == null || shortCode.isBlank()) {
            return;
        }
        String json = "{\"ts\":\"" + Instant.now() + "\",\"clicks\":1,\"shortCode\":\"" + shortCode + "\"}";
        var list = byCode.get(shortCode.toLowerCase());
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("click").data(json));
            } catch (Exception e) {
                emitter.complete();
                list.remove(emitter);
            }
        }
    }

    private void removeCode(String key, SseEmitter emitter) {
        var list = byCode.get(key);
        if (list != null) {
            list.remove(emitter);
        }
    }

    public void pulse() {
        String json = "{\"ts\":\"" + Instant.now() + "\",\"clicks\":1}";
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("click").data(json));
            } catch (Exception e) {
                emitter.complete();
                emitters.remove(emitter);
                log.debug("Dropped live SSE client", e);
            }
        }
    }

    public int subscriberCount() {
        return emitters.size();
    }
}
