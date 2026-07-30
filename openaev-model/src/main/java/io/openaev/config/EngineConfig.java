package io.openaev.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the search engine (Elasticsearch or OpenSearch).
 *
 * <p>This configuration class manages connection settings, index configuration, and performance
 * tuning parameters for the underlying search engine. Properties are loaded from the application
 * configuration with the {@code engine.*} prefix.
 *
 * <p>Supported engines:
 *
 * <ul>
 *   <li>{@code elk} - Elasticsearch (default)
 *   <li>{@code opensearch} - OpenSearch
 * </ul>
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * engine:
 *   url: http://localhost:9200
 *   engine-selector: elk
 *   index-prefix: openaev
 *   username: elastic
 *   password: changeme
 * }</pre>
 */
@Component
@ConfigurationProperties(prefix = "engine")
@Data
public class EngineConfig {

  /** Default configuration values for the search engine. */
  public static class Defaults {
    /** Default engine selector (Elasticsearch). */
    public static final String ENGINE_SELECTOR = "elk";

    /** Default AWS mode (disabled). */
    public static final String ENGINE_AWS_MODE = "no";

    /** Default AWS Elasticsearch host. */
    public static final String ENGINE_AWS_HOST = "search-...us-west-2.es.amazonaws.com";

    /** Default AWS region. */
    public static final String ENGINE_AWS_REGION = "us-west-2";

    /** Default index name prefix. */
    public static final String INDEX_PREFIX = "openaev";

    /** Default index name suffix for rollover. */
    public static final String INDEX_SUFFIX = "-000001";

    /** Default number of primary shards. */
    public static final String NUMBER_OF_SHARDS = "1";

    /** Default number of replica shards. */
    public static final String NUMBER_OF_REPLICAS = "1";

    /** Default maximum result window for pagination. */
    public static final int MAX_RESULT_WINDOW = 100000;

    /** Default maximum number of entities returned. */
    public static final int ENTITIES_CAP = 100;

    /** Default maximum number of search results. */
    public static final int SEARCH_CAP = 500;

    /** Default maximum number of documents per primary shard. */
    public static final int MAX_PRIMARY_SHARD_DOCS = 75000000;

    /** Default maximum size of primary shards. */
    public static final String MAX_PRIMARY_SHARDS_SIZE = "50Gb";

    /** Default maximum field size in bytes. */
    public static final String MAX_FIELD_SIZE = "4096";

    /** Default SSL certificate verification setting. */
    public static final boolean REJECT_UNAUTHORIZED = true;

    /** Default maximum number of records fetched per indexing batch. */
    public static final int INDEXING_BATCH_SIZE = 500;

    /**
     * Default maximum number of models whose indexing sync may run concurrently. Each sync pass
     * holds a database connection for the duration of its (potentially heavy) fetch query; during a
     * full reindex these queries can run for a long time, so letting every model sync at once
     * exhausts the connection pool and starves the rest of the platform.
     */
    public static final int INDEXING_MAX_CONCURRENT_MODELS = 3;

    /** Default connect timeout for the search engine client, in milliseconds. */
    public static final int CONNECT_TIMEOUT_MS = 5_000;

    /** Default socket (response) timeout for the search engine client, in milliseconds. */
    public static final int SOCKET_TIMEOUT_MS = 60_000;

    /**
     * Default maximum number of concurrent connections of the search engine client. Applied to both
     * the per-route and the total limit (the platform talks to a single engine endpoint, so the
     * per-route limit is the effective cap).
     */
    public static final int MAX_CONNECTIONS = 40;
  }

  private String engineSelector = Defaults.ENGINE_SELECTOR;

  private String engineAwsMode = Defaults.ENGINE_AWS_MODE;

  private String engineAwsHost = Defaults.ENGINE_AWS_HOST;

  private String engineAwsRegion = Defaults.ENGINE_AWS_REGION;

  private String indexPrefix = Defaults.INDEX_PREFIX;

  private String indexSuffix = Defaults.INDEX_SUFFIX;

  private String numberOfShards = Defaults.NUMBER_OF_SHARDS;

  private String numberOfReplicas = Defaults.NUMBER_OF_REPLICAS;

  private int maxResultWindow = Defaults.MAX_RESULT_WINDOW;

  private int searchCap = Defaults.SEARCH_CAP;

  private long maxPrimaryShardDocs = Defaults.MAX_PRIMARY_SHARD_DOCS;

  private String maxPrimaryShardsSize = Defaults.MAX_PRIMARY_SHARDS_SIZE;

  private String maxFieldsSize = Defaults.MAX_FIELD_SIZE;

  @NotNull private String url;

  private String username;

  private String password;

  private boolean rejectUnauthorized = Defaults.REJECT_UNAUTHORIZED;

  private int indexingBatchSize = Defaults.INDEXING_BATCH_SIZE;

  private int indexingMaxConcurrentModels = Defaults.INDEXING_MAX_CONCURRENT_MODELS;

  private int connectTimeoutMs = Defaults.CONNECT_TIMEOUT_MS;

  private int socketTimeoutMs = Defaults.SOCKET_TIMEOUT_MS;

  private int maxConnections = Defaults.MAX_CONNECTIONS;

  /**
   * Wildcard pattern matching all indices of this platform and only them.
   *
   * <p>Index names are always built as {@code {prefix}_{name}}, so the pattern includes the
   * underscore separator. Using {@code {prefix}*} instead would also match indices of another
   * platform whose prefix merely starts with this one (e.g. {@code openaev} matching {@code
   * openaevci_*} indices), leaking foreign documents into every engine query.
   *
   * @return the index wildcard pattern, e.g. {@code openaev_*}
   */
  public String getIndexPattern() {
    return indexPrefix + "_*";
  }
}
