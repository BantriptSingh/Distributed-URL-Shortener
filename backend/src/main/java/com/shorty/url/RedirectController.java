package com.shorty.url;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final UrlService urls;

    public RedirectController(UrlService urls) {
        this.urls = urls;
    }

    @GetMapping("/s/{code}")
    public void redirect(@PathVariable String code, HttpServletResponse response) {
        String target = urls.resolveRedirectTarget(code);
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader("Location", target);
        response.setHeader("Cache-Control", "no-store");
    }
}
