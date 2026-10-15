package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/** The manifest of a content repository release (RFC 0004 §4.2), as the collector sends it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogManifest(
    @JsonProperty("repository") String repository,
    @JsonProperty("channel") String channel,
    @JsonProperty("release") String release,
    @JsonProperty("issued_at") Instant issuedAt,
    @JsonProperty("items") List<Item> items) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Item(
      @JsonProperty("type") String type,
      @JsonProperty("external_id") String externalId,
      @JsonProperty("version") String version,
      @JsonProperty("status") String status,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("author") String author,
      @JsonProperty("severity") String severity,
      @JsonProperty("category") String category,
      @JsonProperty("main_focus") String mainFocus,
      @JsonProperty("scenario_type") String scenarioType,
      @JsonProperty("platforms") List<String> platforms,
      @JsonProperty("injects_count") Integer injectsCount,
      @JsonProperty("changelog") String changelog,
      @JsonProperty("published_at") Instant publishedAt,
      @JsonProperty("archive") Archive archive,
      @JsonProperty("payloads") List<PayloadRef> payloads) {}

  public record Archive(@JsonProperty("name") String name, @JsonProperty("sha256") String sha256) {}

  public record PayloadRef(
      @JsonProperty("external_id") String externalId,
      @JsonProperty("version") String version,
      @JsonProperty("sha256") String sha256) {}
}
