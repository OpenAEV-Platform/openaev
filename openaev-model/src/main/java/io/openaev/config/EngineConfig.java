package io.openaev.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "engine")
@Data
public class EngineConfig {

  public static class Defaults {
    public static final String ENGINE_SELECTOR = "elk";
    public static final String ENGINE_AWS_MODE = "no";
    public static final String ENGINE_AWS_HOST = "search-...us-west-2.es.amazonaws.com";
    public static final String ENGINE_AWS_REGION = "us-west-2";
    public static final String INDEX_PREFIX = "openaev";
    public static final String INDEX_SUFFIX = "-000001";
    public static final String NUMBER_OF_SHARDS = "1";
    public static final String NUMBER_OF_REPLICAS = "1";
    public static final int MAX_RESULT_WINDOW = 100000;
    public static final int ENTITIES_CAP = 100;
    public static final int SEARCH_CAP = 500;
    public static final int MAX_PRIMARY_SHARD_DOCS = 75000000;
    public static final String MAX_PRIMARY_SHARDS_SIZE = "50Gb";
    public static final String MAX_FIELD_SIZE = "4096";
    public static final boolean REJECT_UNAUTHORIZED = true;
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
}
