package io.openaev.driver;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.analysis.CustomNormalizer;
import co.elastic.clients.elasticsearch._types.analysis.Normalizer;
import co.elastic.clients.elasticsearch._types.mapping.*;
import co.elastic.clients.elasticsearch.cluster.PutComponentTemplateRequest;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.ilm.*;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.elasticsearch.indices.put_index_template.IndexTemplateMapping;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openaev.config.EngineConfig;
import io.openaev.database.model.IndexingStatus;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.model.EsBase;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import javax.net.ssl.SSLContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ElasticDriver {
  public static final String ES_MODEL_VERSION = "1.0";
  public static final String ES_ILM_POLICY = "-ilm-policy";
  public static final String ES_CORE_SETTINGS = "-core-settings";

  private EngineContext searchEngine;
  private final EngineConfig config;
  private final IndexingStatusRepository indexingStatusRepository;

  @Autowired
  public void setSearchEngine(EngineContext searchEngine) {
    this.searchEngine = searchEngine;
  }

  private ElasticsearchClient getElasticClient() {
    RestClientBuilder restClientBuilder = RestClient.builder(HttpHost.create(config.getUrl()));
    HttpAsyncClientBuilder clientBuilder = HttpAsyncClientBuilder.create();
    if (config.getUsername() != null) {
      BasicCredentialsProvider credsProv = new BasicCredentialsProvider();
      credsProv.setCredentials(
          AuthScope.ANY,
          new UsernamePasswordCredentials(config.getUsername(), config.getPassword()));
      clientBuilder.setDefaultCredentialsProvider(credsProv);
    }
    if (!config.isRejectUnauthorized()) {
      // Create an SSLContext that trusts all certificates
      try {
        SSLContext sslContext =
            SSLContextBuilder.create()
                .loadTrustMaterial(null, (X509Certificate[] chain, String authType) -> true)
                .build();
        clientBuilder
            .setSSLContext(sslContext)
            .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
    restClientBuilder.setHttpClientConfigCallback(hc -> clientBuilder);
    RestClient restClient = restClientBuilder.build();
    JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
    jsonpMapper.objectMapper().registerModule(new JavaTimeModule());
    jsonpMapper.objectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    jsonpMapper.objectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    ElasticsearchTransport transport = new RestClientTransport(restClient, jsonpMapper);
    return new ElasticsearchClient(transport);
  }

  private void createRolloverPolicy(ElasticsearchClient client) throws IOException {
    PutLifecycleRequest lifecycleRequest =
        new PutLifecycleRequest.Builder()
            .name(config.getIndexPrefix() + ES_ILM_POLICY)
            .policy(
                new IlmPolicy.Builder()
                    .phases(
                        new Phases.Builder()
                            .hot(
                                new Phase.Builder()
                                    .actions(
                                        new Actions.Builder()
                                            .rollover(
                                                new RolloverAction.Builder()
                                                    .maxPrimaryShardDocs(
                                                        config.getMaxPrimaryShardDocs())
                                                    .maxPrimaryShardSize(
                                                        config.getMaxPrimaryShardsSize())
                                                    .build())
                                            .setPriority(
                                                new SetPriorityAction.Builder()
                                                    .priority(100)
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build();
    client.ilm().putLifecycle(lifecycleRequest);
  }

  /**
   * Creates a dedicated ILM retention policy for the audit-log index. This policy includes a
   * <b>hot</b> phase (rollover by size/age) and a <b>delete</b> phase (after the retention period).
   *
   * <p>Failures are logged as warnings — a missing policy does not prevent the application from
   * starting; it only means audit-log indexes will not be automatically deleted.
   *
   * @param client the Elasticsearch client
   */
  private void createAuditLogRetentionPolicy(ElasticsearchClient client) {
    try {
      String policyName = config.getIndexPrefix() + "-audit-log-ilm-policy";
      PutLifecycleRequest lifecycleRequest =
          new PutLifecycleRequest.Builder()
              .name(policyName)
              .policy(
                  new IlmPolicy.Builder()
                      .phases(
                          new Phases.Builder()
                              .hot(
                                  new Phase.Builder()
                                      .actions(
                                          new Actions.Builder()
                                              .rollover(
                                                  new RolloverAction.Builder()
                                                      .maxPrimaryShardSize(
                                                          config.getAuditLogRolloverMaxSize())
                                                      .maxAge(
                                                          co.elastic.clients.elasticsearch._types
                                                              .Time.of(
                                                              t ->
                                                                  t.time(
                                                                      config
                                                                          .getAuditLogRolloverMaxAge())))
                                                      .build())
                                              .setPriority(
                                                  new SetPriorityAction.Builder()
                                                      .priority(100)
                                                      .build())
                                              .build())
                                      .build())
                              .delete(
                                  new Phase.Builder()
                                      .minAge(
                                          co.elastic.clients.elasticsearch._types.Time.of(
                                              t -> t.time(config.getAuditLogRetentionDays() + "d")))
                                      .actions(
                                          new Actions.Builder()
                                              .delete(new DeleteAction.Builder().build())
                                              .build())
                                      .build())
                              .build())
                      .build())
              .build();
      client.ilm().putLifecycle(lifecycleRequest);
    } catch (Exception e) {
      log.warn("Failed to create audit-log ILM retention policy: {}", e.getMessage(), e);
    }
  }

  private void createCoreSettings(ElasticsearchClient client) throws IOException {
    PutComponentTemplateRequest.Builder coreSettings = new PutComponentTemplateRequest.Builder();
    coreSettings.name(config.getIndexPrefix() + ES_CORE_SETTINGS);
    coreSettings.create(false);
    coreSettings.template(
        new IndexState.Builder()
            .settings(
                new IndexSettings.Builder()
                    .maxResultWindow(config.getMaxResultWindow())
                    .numberOfReplicas(config.getNumberOfReplicas())
                    .numberOfShards(config.getNumberOfShards())
                    .analysis(
                        new IndexSettingsAnalysis.Builder()
                            .normalizer(
                                "string_normalizer",
                                new Normalizer.Builder()
                                    .custom(
                                        new CustomNormalizer.Builder()
                                            .filter("lowercase", "asciifolding")
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build());
    client.cluster().putComponentTemplate(coreSettings.build());
  }

  @SuppressWarnings("SameParameterValue")
  private void setupIndex(
      ElasticsearchClient client,
      String name,
      String version,
      Map<String, Property> mappings,
      String policyName)
      throws IOException {
    // Create template
    String indexName = config.getIndexPrefix() + "_" + name;
    String coreSettings = config.getIndexPrefix() + ES_CORE_SETTINGS;
    PutIndexTemplateRequest.Builder mapping = new PutIndexTemplateRequest.Builder();
    mapping.name(indexName);
    mapping.meta("version", JsonData.of(version));
    mapping.indexPatterns(indexName + "*");
    mapping.composedOf(coreSettings);
    TypeMapping indexMapping =
        new TypeMapping.Builder()
            .dynamic(DynamicMapping.Strict)
            .dateDetection(false)
            .numericDetection(false)
            .properties(mappings)
            .build();
    mapping.template(
        new IndexTemplateMapping.Builder()
            .settings(
                new IndexSettings.Builder()
                    .lifecycle(
                        new IndexSettingsLifecycle.Builder()
                            .name(policyName)
                            .rolloverAlias(indexName)
                            .build())
                    .mapping(
                        new MappingLimitSettings.Builder()
                            .totalFields(
                                new MappingLimitSettingsTotalFields.Builder()
                                    .limit(config.getMaxFieldsSize())
                                    .build())
                            .build())
                    .build())
            .mappings(indexMapping)
            .build());
    try {
      client.indices().putIndexTemplate(mapping.build());
    } catch (Exception e) {
      throw new IOException(e);
    }
    // Create index
    try {
      client.indices().get(new GetIndexRequest.Builder().index(indexName).build());
    } catch (ElasticsearchException e) {
      try {
        client
            .indices()
            .create(
                new CreateIndexRequest.Builder()
                    .index(indexName + config.getIndexSuffix())
                    .aliases(indexName, new Alias.Builder().build())
                    .build());
        Optional<IndexingStatus> status = indexingStatusRepository.findByType(name);
        status.ifPresent(indexingStatusRepository::delete);
      } catch (ElasticsearchException e2) {
        log.error("cannot create index", e2);
      }
    }
  }

  private Map<String, Property> mappingGeneratorForClass(EsModel<?> esModel) {
    Property subKeyword =
        new Property.Builder()
            .keyword(
                new KeywordProperty.Builder()
                    .ignoreAbove(512)
                    .normalizer("string_normalizer")
                    .build())
            .build();

    Map<String, Property> mappings = new HashMap<>();
    Class<?> model = esModel.getModel();
    // Collect fields from the entire class hierarchy (not just the direct parent)
    List<Field> allFields = new ArrayList<>();
    for (Class<?> clazz = model;
        clazz != null && clazz != Object.class;
        clazz = clazz.getSuperclass()) {
      allFields.addAll(List.of(clazz.getDeclaredFields()));
    }
    for (Field field : allFields) {
      Class<?> fieldType = field.getType();
      if (List.class.isAssignableFrom(field.getType()) || Set.class.isAssignableFrom(fieldType)) {
        ParameterizedType fieldGenericType = (ParameterizedType) field.getGenericType();
        fieldType = (Class<?>) fieldGenericType.getActualTypeArguments()[0];
      }
      if (fieldType == String.class) {
        mappings.put(
            field.getName(),
            new Property.Builder()
                .text(new TextProperty.Builder().fields("keyword", subKeyword).build())
                .build());
      } else if (fieldType == Instant.class) {
        mappings.put(
            field.getName(),
            new Property.Builder()
                // .dateNanos(new DateNanosProperty.Builder().build())
                .date(new DateProperty.Builder().build())
                .build());
      } else if (fieldType == Boolean.class) {
        mappings.put(
            field.getName(),
            new Property.Builder().boolean_(new BooleanProperty.Builder().build()).build());
      } else if (fieldType == Double.class) {
        mappings.put(
            field.getName(),
            new Property.Builder().double_(new DoubleNumberProperty.Builder().build()).build());
      } else if (fieldType == Long.class) {
        mappings.put(
            field.getName(),
            new Property.Builder().long_(new LongNumberProperty.Builder().build()).build());
      } else {
        throw new RuntimeException("Unsupported field type: " + fieldType);
      }
    }
    return mappings;
  }

  public <T extends EsBase> ElasticsearchClient elasticClient() throws Exception {
    log.info("Creating ElasticClient");
    ElasticsearchClient elasticClient = getElasticClient();
    // Try to client configuration
    try {
      InfoResponse info = elasticClient.info();
      log.info("ElasticClient ready for {} - {}", info.name(), info.version());
    } catch (Exception e) {
      log.error(String.format("Error activating Elasticsearch engine: %s", e.getMessage()), e);
      throw new IllegalStateException("Failed to connect to Elasticsearch", e);
    }
    // TODO enable telemetry ?
    // https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/opentelemetry.html
    // Initialize elastic if needed.
    createRolloverPolicy(elasticClient);
    createCoreSettings(elasticClient);
    createAuditLogRetentionPolicy(elasticClient);
    // TODO Fetch the current model versions
    // | type     | last_updated_at      | version db | elastic version
    // | findings | 2024-12-04T12:00:00Z | 2.0        | 1.0
    // If version of the model stored in elastic is different from the db version
    // Index + template must be removed and recreated
    // last_updated_at for the type must be reset to reindex the full data.
    String sharedPolicy = config.getIndexPrefix() + ES_ILM_POLICY;
    String auditLogPolicy = config.getIndexPrefix() + "-audit-log-ilm-policy";
    List<EsModel<T>> models = this.searchEngine.getModels();
    models.forEach(
        esModel -> {
          Map<String, Property> mappings = mappingGeneratorForClass(esModel);
          String policy = "audit-log".equals(esModel.getName()) ? auditLogPolicy : sharedPolicy;
          try {
            // Cleanup old index
            if (indexingStatusRepository.findByType(esModel.getName()).isEmpty()) {
              log.info("Cleanup old Index {}", esModel.getName());
              cleanUpIndex(esModel.getName(), elasticClient);
            }
            log.info("Creating Index " + esModel.getName());
            setupIndex(elasticClient, esModel.getName(), ES_MODEL_VERSION, mappings, policy);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        });
    // Create audit-log index with custom mapping (nested objects + dynamic context_data)
    setupAuditLogIndex(elasticClient);
    return elasticClient;
  }

  /**
   * Creates the audit-log index with a custom mapping. Unlike other indices whose mappings are
   * generated via reflection, the audit-log index uses nested objects ({@code user_metadata}) and a
   * dynamic sub-object ({@code context_data}) that cannot be expressed through the generic mapping
   * generator.
   */
  private void setupAuditLogIndex(ElasticsearchClient client) {
    try {
      String indexName = config.getIndexPrefix() + "_audit-log";
      String coreSettings = config.getIndexPrefix() + ES_CORE_SETTINGS;
      String auditLogPolicy = config.getIndexPrefix() + "-audit-log-ilm-policy";

      // -- User metadata nested properties --
      Map<String, Property> userMetaProps = new HashMap<>();
      userMetaProps.put(
          "user_email",
          new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());
      userMetaProps.put(
          "user_agent", new Property.Builder().text(new TextProperty.Builder().build()).build());
      userMetaProps.put(
          "x_forwarded_for",
          new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());
      userMetaProps.put(
          "ip", new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());

      // -- Top-level properties --
      Map<String, Property> props = new HashMap<>();
      props.put(
          "id", new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());
      props.put(
          "entity_type",
          new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());
      props.put(
          "created_at", new Property.Builder().date(new DateProperty.Builder().build()).build());
      props.put(
          "event_type",
          new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());
      props.put(
          "event_status",
          new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());
      props.put(
          "event_access",
          new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());
      props.put(
          "event_scope",
          new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());
      props.put(
          "user_id", new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());
      props.put(
          "tenant_id",
          new Property.Builder().keyword(new KeywordProperty.Builder().build()).build());
      props.put(
          "user_metadata",
          new Property.Builder()
              .object(new ObjectProperty.Builder().properties(userMetaProps).build())
              .build());
      props.put(
          "timestamp", new Property.Builder().date(new DateProperty.Builder().build()).build());
      props.put(
          "context_data",
          new Property.Builder()
              .object(
                  new ObjectProperty.Builder().enabled(true).dynamic(DynamicMapping.True).build())
              .build());

      TypeMapping indexMapping =
          new TypeMapping.Builder()
              .dynamic(DynamicMapping.Strict)
              .dateDetection(false)
              .numericDetection(false)
              .properties(props)
              .build();

      // -- Index template --
      PutIndexTemplateRequest.Builder template = new PutIndexTemplateRequest.Builder();
      template.name(indexName);
      template.meta("version", JsonData.of(ES_MODEL_VERSION));
      template.indexPatterns(indexName + "*");
      template.composedOf(coreSettings);
      template.template(
          new IndexTemplateMapping.Builder()
              .settings(
                  new IndexSettings.Builder()
                      .lifecycle(
                          new IndexSettingsLifecycle.Builder()
                              .name(auditLogPolicy)
                              .rolloverAlias(indexName)
                              .build())
                      .mapping(
                          new MappingLimitSettings.Builder()
                              .totalFields(
                                  new MappingLimitSettingsTotalFields.Builder()
                                      .limit(config.getMaxFieldsSize())
                                      .build())
                              .build())
                      .build())
              .mappings(indexMapping)
              .build());
      client.indices().putIndexTemplate(template.build());

      // -- Create index if it does not exist --
      try {
        client.indices().get(new GetIndexRequest.Builder().index(indexName).build());
      } catch (ElasticsearchException e) {
        client
            .indices()
            .create(
                new CreateIndexRequest.Builder()
                    .index(indexName + config.getIndexSuffix())
                    .aliases(indexName, new Alias.Builder().build())
                    .build());
        log.info("Created audit-log index: {}{}", indexName, config.getIndexSuffix());
      }
    } catch (Exception e) {
      log.warn("Failed to setup audit-log index: {}", e.getMessage(), e);
    }
  }

  public void cleanUpIndex(String indexName, ElasticsearchClient client) throws IOException {
    try {
      String fullIndexName = config.getIndexPrefix() + "_" + indexName;
      String fullIndexWithSuffix = fullIndexName + config.getIndexSuffix();

      // 1. Delete index and alias if they exist
      for (String name : List.of(fullIndexName, fullIndexWithSuffix)) {
        try {
          client.indices().delete(d -> d.index(name));
          log.info("Deleted index: {}", name);
        } catch (ElasticsearchException e) {
          log.warn("Index " + name + " does not exist or already deleted");
        }
      }

      // 2. Delete index template
      try {
        client.indices().deleteIndexTemplate(d -> d.name(fullIndexName));
        log.info("Deleted index template: " + fullIndexName);
      } catch (ElasticsearchException e) {
        log.warn("Index template {} does not exist or already deleted", fullIndexName);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to delete index " + indexName, e);
    }
  }
}
