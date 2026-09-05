package com.shorty.analytics;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class LiveClickHub {

    private static final Logger log = LoggerFactory.getLogger(LiveClickHub.class);

    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

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
