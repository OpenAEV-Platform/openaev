package io.openaev.api.catalog.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.catalog.ContentType;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** The manifest of a content repository release (RFC 0004 §4.2), as the collector sends it. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CatalogManifest(
    @JsonProperty("schema_version") Integer schemaVersion,
    @JsonProperty("repository") String repository,
    @JsonProperty("release") String release,
    @JsonProperty("issued_at") Instant issuedAt,
    @JsonProperty("release_approver") String releaseApprover,
    @JsonProperty("items") List<Item> items,
    @JsonProperty("payloads") List<PayloadVersion> payloadVersions) {

  @Override
  public List<Item> items() {
    return items == null ? List.of() : items;
  }

  @Override
  public List<PayloadVersion> payloadVersions() {
    return payloadVersions == null ? List.of() : payloadVersions;
  }

  // What a notification can show for an item: a scenario's name, a payload version's slug.
  public Map<String, String> names() {
    Map<String, String> names = new HashMap<>();
    items().stream()
        .filter(item -> item.externalId() != null && item.name() != null)
        .forEach(item -> names.put(item.externalId(), item.name()));
    payloadVersions().stream()
        .filter(version -> version.openaevId() != null && version.payload() != null)
        .forEach(version -> names.putIfAbsent(version.openaevId(), version.payload()));
    return names;
  }

  /** The key of every version the release lists. */
  public Set<String> keys() {
    Set<String> keys = new HashSet<>();
    items().forEach(item -> keys.add(key(item.externalId(), item.version())));
    payloadVersions().forEach(version -> keys.add(key(version.openaevId(), version.version())));
    return keys;
  }

  public static String key(String externalId, String version) {
    return externalId + "@" + version;
  }

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
      @JsonProperty("payloads") List<PayloadRef> payloadRefs,
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
  public record Archive(
      @JsonProperty("name") String name,
      @JsonProperty("sha256") String sha256,
      @JsonProperty("size") Long size) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PayloadRef(
      @JsonProperty("external_id") @JsonAlias("id") String externalId,
      @JsonProperty("version") String version,
      @JsonProperty("sha256") String sha256) {}

  /** A payload version of the release, sent as a JSON file next to the archives. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PayloadVersion(
      @JsonProperty("payload") String payload,
      @JsonProperty("version") String version,
      @JsonProperty("sha256") String sha256,
      @JsonProperty("openaev_id") String openaevId,
      @JsonProperty("status") String status,
      @JsonProperty("file") VersionFile file) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record VersionFile(
      @JsonProperty("name") String name,
      @JsonProperty("sha256") String sha256,
      @JsonProperty("size") Long size) {}
}
