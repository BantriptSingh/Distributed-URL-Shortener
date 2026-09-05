package com.shorty.support;

import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
        properties = {
            "it.rate-limit.guest-create-per-minute=10000",
            "it.rate-limit.jwt-create-per-minute=10000",
            "it.rate-limit.api-key-write-per-minute=10000",
            "it.rate-limit.redirect-per-minute=10000",
            "it.rate-limit.auth-per-minute=10000",
            "it.rate-limit.unlock-per-minute=10000"
        })
public abstract class HighLimitIT extends InfrastructureIT {}
