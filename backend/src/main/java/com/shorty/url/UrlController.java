package com.shorty.url;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
public class UrlController {

    private final UrlService urls;

    public UrlController(UrlService urls) {
        this.urls = urls;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUrlResponse create(@Valid @RequestBody CreateUrlRequest request) {
        return urls.create(request);
    }

    @GetMapping("/{code}")
    public PublicUrlResponse get(@PathVariable String code) {
        return urls.getPublic(code);
    }
}
