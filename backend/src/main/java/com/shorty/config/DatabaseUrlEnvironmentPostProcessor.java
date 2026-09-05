package com.shorty.config;

import java.net.URI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;

/**
 * Accepts Railway-style {@code DATABASE_URL=postgres://user:pass@host:5432/db}
 * and maps it onto Spring datasource properties.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty("DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            return;
        }
        if (raw.startsWith("jdbc:")) {
            var jdbc = new HashMap<String, Object>();
            jdbc.put("spring.datasource.url", raw);
            environment.getPropertySources().addFirst(new MapPropertySource("databaseUrlJdbc", jdbc));
            return;
        }
        URI uri = URI.create(raw);
        String userInfo = uri.getUserInfo();
        String username = environment.getProperty("SPRING_DATASOURCE_USERNAME");
        String password = environment.getProperty("SPRING_DATASOURCE_PASSWORD");
        if (userInfo != null && userInfo.contains(":")) {
            int split = userInfo.indexOf(':');
            username = userInfo.substring(0, split);
            password = userInfo.substring(split + 1);
        }
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String path = uri.getPath() == null ? "/shortener" : uri.getPath();
        String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + path;
        var map = new HashMap<String, Object>();
        map.put("spring.datasource.url", jdbc);
        if (username != null) {
            map.put("spring.datasource.username", username);
        }
        if (password != null) {
            map.put("spring.datasource.password", password);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("databaseUrl", map));
    }
}
