package io.openaev.es8;

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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.EngineConfig;
import io.openaev.database.model.IndexingStatus;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.driver.EngineObjectMapperFactory;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.RetiredIndexes;
import io.openaev.engine.model.EsBase;
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

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;

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

  /**
   * Shared ObjectMapper used by the Elasticsearch client for JSON serialization. Exposed via {@link
   * #getObjectMapper()} so that other components (e.g. audit log service) can reuse the exact same
   * serialization settings.
   */
  private final ObjectMapper engineObjectMapper = EngineObjectMapperFactory.create();

  /** Returns the ObjectMapper used by the Elasticsearch client for document serialization. */
  public ObjectMapper getObjectMapper() {
    return engineObjectMapper;
  }

  @Autowired
  public void setSearchEngine(EngineContext searchEngine) {
    this.searchEngine = searchEngine;
  }

  private ElasticsearchClient getElasticClient() {
    RestClientBuilder restClientBuilder = RestClient.builder(HttpHost.create(config.getUrl()));
    // The library defaults (1s connect / 30s socket timeout, 10 connections per route) are too
    // tight for this platform: during bulk indexing (full reindex after a migration) the async
    // client saturates and cancels pending requests, surfacing as "Request execution cancelled"
    // in dashboard queries and engine jobs. Configure explicit limits instead.
    restClientBuilder.setRequestConfigCallback(
        requestConfig ->
            requestConfig
                .setConnectTimeout(config.getConnectTimeoutMs())
                .setSocketTimeout(config.getSocketTimeoutMs()));
    HttpAsyncClientBuilder clientBuilder = HttpAsyncClientBuilder.create();
    clientBuilder
        .setMaxConnTotal(config.getMaxConnections())
        .setMaxConnPerRoute(config.getMaxConnections());
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
    JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(engineObjectMapper);
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
      ElasticsearchClient client, String name, String version, Map<String, Property> mappings)
      throws IOException {
    // Create template
    String indexName = config.getIndexPrefix() + "_" + name;
    String coreSettings = config.getIndexPrefix() + ES_CORE_SETTINGS;
    String ilmPolicy = config.getIndexPrefix() + ES_ILM_POLICY;
    PutIndexTemplateRequest.Builder mapping = new PutIndexTemplateRequest.Builder();
    mapping.name(indexName);
    mapping.meta("version", JsonData.of(version));
    mapping.indexPatterns(indexName + "*");
    // Overlapping templates must not share a priority: "asset" is a name prefix of "asset-group",
    // so their patterns overlap and the engine refuses the second template at equal priority. The
    // name length makes the more specific template win, whatever the model registration order.
    mapping.priority((long) indexName.length());
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
                            .name(ilmPolicy)
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
        log.info("Creating index {}", indexName);
        client
            .indices()
            .create(
                new CreateIndexRequest.Builder()
                    .index(indexName + config.getIndexSuffix())
                    .aliases(indexName, new Alias.Builder().build())
                    .build());
        // A brand-new index must be reindexed from scratch: reset the cursor to epoch instead of
        // deleting the row. The row's presence is what tells the next startup that the index is
        // already initialized (a missing row means wipe & recreate at boot). Deleting the row
        // here kept models with no data yet in a wipe/recreate loop at every single boot,
        // because the indexing job only persists the row once a first document is indexed.
        resetIndexingCursor(name);
      } catch (ElasticsearchException e2) {
        log.error("cannot create index", e2);
      }
    }
  }

  /**
   * Upserts the {@link IndexingStatus} row for a model with an epoch cursor (full reindex). Epoch
   * is used instead of null because the column is NOT NULL, and every handler treats a null cursor
   * as epoch anyway ({@code from != null ? from : Instant.ofEpochMilli(0)}).
   */
  private void resetIndexingCursor(String modelName) {
    IndexingStatus status =
        indexingStatusRepository
            .findByType(modelName)
            .orElseGet(
                () -> {
                  IndexingStatus newStatus = new IndexingStatus();
                  newStatus.setType(modelName);
                  return newStatus;
                });
    status.setLastIndexing(Instant.EPOCH);
    indexingStatusRepository.save(status);
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
    // TODO Fetch the current model versions
    // | type     | last_updated_at      | version db | elastic version
    // | findings | 2024-12-04T12:00:00Z | 2.0        | 1.0
    // If version of the model stored in elastic is different from the db version
    // Index + template must be removed and recreated
    // last_updated_at for the type must be reset to reindex the full data.

    // Sequential iteration is intentional: parallel stream caused a startup deadlock.
    // Spring's MetricsRepositoryMethodInvocationListener acquires a ReentrantLock when recording
    // repository call metrics. When multiple ForkJoinPool threads all hit a repository method
    // (indexingStatusRepository.findByType) at the same time during startup, they contend on that
    // same lock and deadlock — none can proceed and the application never finishes booting.
    // Iterating sequentially eliminates the contention entirely at a negligible cost: the number
    // of ES models is small and the bottleneck is network I/O, not CPU parallelism.
    // Drop the indexes of retired models first: searches run against the index pattern, so a
    // leftover index would still be matched (see RetiredIndexes).
    for (String retiredIndex : RetiredIndexes.NAMES) {
      try {
        cleanUpIndex(retiredIndex, elasticClient);
      } catch (IOException e) {
        throw new RuntimeException(
            "Error while cleaning up retired index " + retiredIndex + " with Elastic", e);
      }
    }
    List<EsModel<T>> models = this.searchEngine.getModels();
    for (EsModel<T> esModel : models) {
      Map<String, Property> mappings = mappingGeneratorForClass(esModel);
      try {
        // Initialize indexes sequentially to avoid startup lock contention in repository metrics.
        // A missing IndexingStatus row means the index was never initialized (or a reindex was
        // explicitly requested by deleting the row): wipe any leftover and start from scratch.
        if (indexingStatusRepository.findByType(esModel.getName()).isEmpty()) {
          log.info("No indexing status for {}: resetting index", esModel.getName());
          cleanUpIndex(esModel.getName(), elasticClient);
        }
        log.debug("Ensuring index {}", esModel.getName());
        setupIndex(elasticClient, esModel.getName(), ES_MODEL_VERSION, mappings);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return elasticClient;
  }

  public void cleanUpIndex(String indexName, ElasticsearchClient client) throws IOException {
    try {
      String fullIndexName = config.getIndexPrefix() + "_" + indexName;
      String fullIndexWithSuffix = fullIndexName + config.getIndexSuffix();

      // 1. Delete index and alias if they exist. Probe existence first (this runs at every
      // startup for retired models) so a healthy platform boots without deletion warnings, and
      // delete the concrete index before the alias name: removing the index also removes its
      // alias, so the alias-name delete (which would fail with "matches an alias") is skipped.
      for (String name : List.of(fullIndexWithSuffix, fullIndexName)) {
        if (!client.indices().exists(b -> b.index(name)).value()) {
          continue;
        }
        try {
          client.indices().delete(d -> d.index(name));
          log.info("Deleted index: {}", name);
        } catch (ElasticsearchException e) {
          log.warn("Index {} could not be deleted: {}", name, e.getMessage());
        }
      }

      // 2. Delete index template
      if (client.indices().existsIndexTemplate(b -> b.name(fullIndexName)).value()) {
        client.indices().deleteIndexTemplate(d -> d.name(fullIndexName));
        log.info("Deleted index template: {}", fullIndexName);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to delete index " + indexName, e);
    }
  }
}
