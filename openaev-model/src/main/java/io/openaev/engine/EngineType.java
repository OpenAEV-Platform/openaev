package io.openaev.engine;

import io.openaev.exception.StartupException;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Search engine implementations the platform can drive, detected at startup from the server itself
 * (see {@link EngineVersionProbe}).
 *
 * <p>The {@link #getKey() key} is what the platform settings expose to the frontend, which uses it
 * as a translation key. The {@link #getClient() client} is the {@code engine.client} value that
 * forces this implementation, and the {@link #getDisplayName() display name} labels it in logs.
 */
@Getter
@Slf4j
public enum EngineType {
  ELASTICSEARCH_8("elk", "elasticsearch8", "Elasticsearch 8"),
  ELASTICSEARCH_9("elk9", "elasticsearch9", "Elasticsearch 9"),
  OPENSEARCH("opensearch", "opensearch", "OpenSearch");

  /** {@code engine.client} value that leaves the choice to the detection. */
  public static final String AUTO_CLIENT = "auto";

  /**
   * Distribution reported by OpenSearch in {@code version.distribution}; absent on Elasticsearch.
   */
  public static final String OPENSEARCH_DISTRIBUTION = "opensearch";

  /** Start of the root {@code tagline} of OpenSearch, compatibility mode included. */
  public static final String OPENSEARCH_TAGLINE_PREFIX = "The OpenSearch Project";

  /**
   * Build flavor reported by Elasticsearch Serverless in {@code version.build_flavor}, the way
   * Elastic documents to recognise it.
   */
  public static final String SERVERLESS_BUILD_FLAVOR = "serverless";

  /** Oldest Elasticsearch 8 minor the platform supports. */
  private static final int MIN_ELASTICSEARCH_8_MINOR = 19;

  /** Most recent Elasticsearch client the platform ships. */
  private static final EngineType LATEST_ELASTICSEARCH = ELASTICSEARCH_9;

  private final String key;

  private final String client;

  private final String displayName;

  EngineType(String key, String client, String displayName) {
    this.key = key;
    this.client = client;
    this.displayName = displayName;
  }

  /**
   * Reads the {@code engine.client} setting.
   *
   * @param client the configured value
   * @return the forced implementation, or empty for {@code auto} (or no value)
   * @throws StartupException if the value names no known client
   */
  public static Optional<EngineType> fromClient(String client) {
    if (client == null || client.isBlank() || AUTO_CLIENT.equalsIgnoreCase(client.trim())) {
      return Optional.empty();
    }
    Optional<EngineType> forced =
        Arrays.stream(values())
            .filter(type -> type.client.equalsIgnoreCase(client.trim()))
            .findFirst();
    if (forced.isEmpty()) {
      throw new StartupException(
          "Unknown engine.client '"
              + client
              + "': use "
              + AUTO_CLIENT
              + ", "
              + Arrays.stream(values())
                  .map(EngineType::getClient)
                  .collect(Collectors.joining(", ")));
    }
    return forced;
  }

  /**
   * Picks the implementation matching the {@code GET /} answer of the engine.
   *
   * <p>OpenSearch is recognised by its distribution, whatever version number it reports. In
   * compatibility mode ({@code compatibility.override_main_response_version}, OpenSearch 1.x and
   * 2.x) it drops the distribution and announces itself as 7.10.2, so its tagline identifies it
   * instead. Elasticsearch reports no distribution, and its major version picks the client: the 9.x
   * client is rejected by an 8.x server, so each major needs its own.
   *
   * <p>Elasticsearch Serverless is the exception: its version number is only kept for legacy
   * clients and does not follow the service, which tracks the latest Elasticsearch. It is
   * recognised by its build flavor and gets the most recent client.
   *
   * @param info what the engine reported about itself
   * @return the matching engine type
   * @throws StartupException if the engine is not a supported Elasticsearch or OpenSearch
   */
  public static EngineType fromServerInfo(EngineVersionProbe.ServerInfo info) {
    String distribution = info.distribution();
    String versionNumber = info.number();
    String tagline = info.tagline();
    if (OPENSEARCH_DISTRIBUTION.equalsIgnoreCase(distribution)) {
      return OPENSEARCH;
    }
    if ((distribution == null || distribution.isBlank())
        && tagline != null
        && tagline.startsWith(OPENSEARCH_TAGLINE_PREFIX)) {
      return OPENSEARCH;
    }
    if (distribution != null && !distribution.isBlank()) {
      throw new StartupException(
          "Unsupported search engine distribution '"
              + distribution
              + "': only Elasticsearch and OpenSearch are supported");
    }
    if (SERVERLESS_BUILD_FLAVOR.equalsIgnoreCase(info.buildFlavor())) {
      log.info(
          "Elasticsearch Serverless detected (reported version {}): using the {} client",
          versionNumber,
          LATEST_ELASTICSEARCH.getDisplayName());
      return LATEST_ELASTICSEARCH;
    }
    int[] version = parseVersion(versionNumber);
    int major = version[0];
    if (major < 8) {
      throw new StartupException(
          "Unsupported Elasticsearch version "
              + versionNumber
              + ": Elasticsearch 8.19 or later is required");
    }
    if (major == 8) {
      if (version[1] < MIN_ELASTICSEARCH_8_MINOR) {
        log.warn(
            "Elasticsearch {} is older than 8.{}, the oldest supported version",
            versionNumber,
            MIN_ELASTICSEARCH_8_MINOR);
      }
      return ELASTICSEARCH_8;
    }
    if (major > 9) {
      log.warn(
          "Elasticsearch {} has not been tested with the platform, using the {} client",
          versionNumber,
          LATEST_ELASTICSEARCH.getDisplayName());
    }
    return LATEST_ELASTICSEARCH;
  }

  /** Major and minor of a version such as {@code 9.5.1} or {@code 8.19.0-SNAPSHOT}. */
  private static int[] parseVersion(String versionNumber) {
    if (versionNumber == null || versionNumber.isBlank()) {
      throw new StartupException("The search engine did not report its version number");
    }
    String[] parts = versionNumber.trim().split("[.\\-]");
    try {
      int major = Integer.parseInt(parts[0]);
      int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
      return new int[] {major, minor};
    } catch (NumberFormatException e) {
      throw new StartupException(
          "Unable to read the search engine version number '" + versionNumber + "'", e);
    }
  }
}
