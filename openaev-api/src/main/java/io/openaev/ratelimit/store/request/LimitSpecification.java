package io.openaev.ratelimit.store.request;

public record LimitSpecification(Long defaultRps, Long authenticatedRps) {}
