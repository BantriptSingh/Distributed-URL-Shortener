package com.shorty.url;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final RedirectService redirects;

    public RedirectController(RedirectService redirects) {
        this.redirects = redirects;
    }

    @GetMapping("/s/{code}")
    public void redirect(
            @PathVariable String code, HttpServletRequest request, HttpServletResponse response) {
        String target = redirects.locationFor(code, request);
        response.setStatus(HttpStatus.FOUND.value());
        response.setHeader("Location", target);
        response.setHeader("Cache-Control", "no-store");
    }
}
