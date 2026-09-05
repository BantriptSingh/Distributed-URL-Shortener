package com.shorty.analytics;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class LiveAnalyticsController {

    private final LiveClickHub hub;

    public LiveAnalyticsController(LiveClickHub hub) {
        this.hub = hub;
    }

    @GetMapping(path = "/api/v1/analytics/live", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter live() {
        return hub.subscribe();
    }
}
