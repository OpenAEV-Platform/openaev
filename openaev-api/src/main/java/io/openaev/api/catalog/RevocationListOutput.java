package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record RevocationListOutput(
    @JsonProperty("revocation_list_repository") String repository,
    @JsonProperty("revocation_list_serial") long serial,
    @JsonProperty("revocation_list_issued_at") Instant issuedAt,
    @JsonProperty("revocation_list_expires_at") Instant expiresAt,
    @JsonProperty("revocation_list_downloaded_at") Instant downloadedAt,
    @JsonProperty("revocation_list_applied") boolean applied,
    @JsonProperty("revocation_list_added") int added,
    @JsonProperty("revocation_list_lifted") int lifted) {}
