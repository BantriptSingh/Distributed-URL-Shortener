package com.shorty.seed;

import com.shorty.url.CreateUrlRequest;
import com.shorty.url.UrlRepository;
import com.shorty.url.UrlService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DemoSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

    private final boolean enabled;
    private final UrlService urls;
    private final UrlRepository repo;

    public DemoSeedRunner(
            @Value("${SEED_DEMO_DATA:false}") boolean enabled, UrlService urls, UrlRepository repo) {
        this.enabled = enabled;
        this.urls = urls;
        this.repo = repo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        seed("demo01a", "https://example.com/demo-one", List.of("demo"));
        seed("demo02b", "https://example.com/demo-two", List.of("demo"));
        seed("demo03c", "https://example.com/demo-three", List.of("demo"));
        log.info("demo seed complete");
    }

    private void seed(String code, String destination, List<String> tags) {
        if (repo.existsByShortCodeIgnoreCase(code)) {
            log.info("seed skip existing {}", code);
            return;
        }
        urls.create(new CreateUrlRequest(destination, code, null, null, tags, true, false, null), null);
        log.info("seed created {}", code);
    }
}
