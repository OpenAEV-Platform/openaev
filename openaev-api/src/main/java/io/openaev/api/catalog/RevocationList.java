package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** The revocation list of a content repository: the newest serial wins. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RevocationList(
    @JsonProperty("repository") String repository,
    @JsonProperty("serial") Long serial,
    @JsonProperty("issued_at") Instant issuedAt,
    @JsonProperty("expires_at") Instant expiresAt,
    @JsonProperty("revocations") List<Entry> revocations) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Entry(
      @JsonProperty("type") String type,
      @JsonProperty("external_id") String externalId,
      @JsonProperty("version") String version,
      @JsonProperty("reason") String reason) {}
}
