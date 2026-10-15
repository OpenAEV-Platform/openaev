package io.openaev.api.catalog;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.ContentType;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

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
      @JsonProperty("payloads") List<PayloadRef> payloads,
      @JsonProperty("requires") Requires requires) {

    // The scenarios repository writes the scenario type there: its manifest holds scenarios only.
    public ContentType contentType() {
      String value = type == null ? "" : type.toLowerCase(Locale.ROOT);
      return switch (value) {
        case "payload" -> ContentType.PAYLOAD;
        case "scenario", "time-based", "chained" -> ContentType.SCENARIO;
        default -> throw new IllegalArgumentException("Unknown content type " + type);
      };
    }

    public String scenarioTypeOrType() {
      if (scenarioType != null) {
        return scenarioType;
      }
      return contentType() == ContentType.SCENARIO && !"scenario".equalsIgnoreCase(type)
          ? type
          : null;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Requires(
      @JsonProperty("openaev") String openaev,
      @JsonProperty("edition") String edition,
      @JsonProperty("injectors") List<String> injectors,
      @JsonProperty("executors") List<String> executors) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Archive(@JsonProperty("name") String name, @JsonProperty("sha256") String sha256) {}

  public record PayloadRef(
      @JsonProperty("external_id") @JsonAlias("id") String externalId,
      @JsonProperty("version") String version,
      @JsonProperty("sha256") String sha256) {}
}
