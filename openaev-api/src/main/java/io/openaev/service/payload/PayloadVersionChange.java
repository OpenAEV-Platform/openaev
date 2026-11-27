package io.openaev.service.payload;

/** What a version of a payload changed since the one just below it. */
public record PayloadVersionChange(String fromVersion, PayloadChange change) {}
