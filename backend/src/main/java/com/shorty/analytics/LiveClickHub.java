package com.shorty.analytics;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class LiveClickHub {

    private static final Logger log = LoggerFactory.getLogger(LiveClickHub.class);

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> byCode = new ConcurrentHashMap<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("hello").data("{\"ok\":true}"));
        } catch (IOException e) {
            emitters.remove(emitter);
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

    int subscriberCount() {
        return emitters.size();
    }
}
