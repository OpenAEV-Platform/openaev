package io.openaev.driver;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openaev.config.EngineConfig;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.EngineContext;
import io.openaev.engine.EsModel;
import io.openaev.engine.model.EsBase;
import io.openaev.exception.AnalyticsEngineException;
import io.openaev.exception.StartupException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import javax.net.ssl.SSLContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.reactor.ssl.TlsDetails;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.analysis.CustomNormalizer;
import org.opensearch.client.opensearch._types.analysis.Normalizer;
import org.opensearch.client.opensearch._types.mapping.*;
import org.opensearch.client.opensearch.cluster.PutComponentTemplateRequest;
import org.opensearch.client.opensearch.core.InfoResponse;
import org.opensearch.client.opensearch.generic.Body;
import org.opensearch.client.opensearch.generic.Requests;
import org.opensearch.client.opensearch.generic.Response;
import org.opensearch.client.opensearch.indices.*;
import org.opensearch.client.opensearch.indices.put_index_template.IndexTemplateMapping;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenSearchDriver {
  public static final String ES_MODEL_VERSION = "1.0";
  public static final String ES_ILM_POLICY = "-ilm-policy";
  public static final String ES_CORE_SETTINGS = "-core-settings";

  private final EngineContext searchEngine;
  private final EngineConfig config;
  private final IndexingStatusRepository indexingStatusRepository;

  /**
   * Initializing the standard client
   *
   * @return the OpenSearchClient
   * @throws URISyntaxException throw an exception in case of a malformed URI
   */
  private OpenSearchClient standardClient() throws URISyntaxException {
    final HttpHost host = HttpHost.create(config.getUrl());
    final ApacheHttpClient5TransportBuilder builder =
        ApacheHttpClient5TransportBuilder.builder(host);
    final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    if (config.getUsername() != null) {
      credentialsProvider.setCredentials(
          new AuthScope(host),
          new UsernamePasswordCredentials(
              config.getUsername(), config.getPassword().toCharArray()));
    }
    builder.setHttpClientConfigCallback(
        httpClientBuilder -> {
          PoolingAsyncClientConnectionManagerBuilder managerBuilder =
              PoolingAsyncClientConnectionManagerBuilder.create();
          if (!config.isRejectUnauthorized()) {
            // Create an SSLContext that trusts all certificates
            try {
              SSLContext sslContext =
                  SSLContextBuilder.create()
                      .loadTrustMaterial(null, (X509Certificate[] chain, String authType) -> true)
                      .build();
              @SuppressWarnings("deprecation")
              final TlsStrategy tlsStrategy =
                  ClientTlsStrategyBuilder.create()
                      .setSslContext(sslContext)
                      .setHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                      // See https://issues.apache.org/jira/browse/HTTPCLIENT-2219
                      .setTlsDetailsFactory(
                          sslEngine ->
                              new TlsDetails(
                                  sslEngine.getSession(), sslEngine.getApplicationProtocol()))
                      .build();
              managerBuilder.setTlsStrategy(tlsStrategy);
            } catch (Exception e) {
              throw new StartupException(
                  "Error during startup. Cannot initialize Opensearch - ", e);
            }
          }

          final PoolingAsyncClientConnectionManager connectionManager = managerBuilder.build();
          return httpClientBuilder
              .setDefaultCredentialsProvider(credentialsProvider)
              .setConnectionManager(connectionManager);
        });
    JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper();
    jsonpMapper.objectMapper().registerModule(new JavaTimeModule());
    jsonpMapper.objectMapper().configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    jsonpMapper.objectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    builder.setMapper(jsonpMapper);
    final OpenSearchTransport transport = builder.build();
    return new OpenSearchClient(transport);
  }

  /**
   * Returns the opensearch client depending on the situation
   *
   * @return the opensearch client
   * @throws URISyntaxException throw an exception in case of a malformed URI
   */
  private OpenSearchClient getOpensearchClient() throws URISyntaxException {
    // If client is not AWS specific
    if (config.getEngineAwsMode().equalsIgnoreCase("no")) {
      return standardClient();
    }
    // If client is directly to AWS opensearch service
    SdkHttpClient httpClient = ApacheHttpClient.builder().build();
    return new OpenSearchClient(
        new AwsSdk2Transport(
            httpClient,
            config.getEngineAwsHost(),
            config.getEngineAwsMode(),
            Region.of(config.getEngineAwsRegion()),
            AwsSdk2TransportOptions.builder().build()));
  }

  /**
   * Create the rollover policy
   *
   * @param client the client to use
   * @throws IOException in case of error during the call to opensearch
   */
  private void createRolloverPolicy(OpenSearchClient client) throws IOException {
    String endpoint = "/_plugins/_ism/policies/" + config.getIndexPrefix() + ES_ILM_POLICY;
    try (Response response =
        client.generic().execute(Requests.builder().endpoint(endpoint).method("GET").build())) {
      final int status = response.getStatus();
      if (status != 404) {
        return;
      }
    }
    String jsonRequest =
        String.format(
            """
                    {
                      "policy": {
                          "description": "OpenAEV ISM Policy",
                          "default_state": "hot",
                          "states": [
                            {
                              "name": "hot",
                              "actions": [
                                {
                                  "rollover": {
                                    "min_primary_shard_size": "%s",
                                    "min_doc_count": %s
                                  }
                                }],
                              "transitions": []
                            }],
                          "ism_template": {
                            "index_patterns": ["%s*"],
                            "priority": 100
                          }
                      }
                   }
                   """,
            config.getMaxPrimaryShardsSize(),
            config.getMaxPrimaryShardDocs(),
            config.getIndexPrefix());

    try (Response response =
        client
            .generic()
            .execute(
                Requests.builder().endpoint(endpoint).method("PUT").json(jsonRequest).build())) {
      final int status = response.getStatus();
      log.info("Create rollover policy: {}", status);
      if (status != 201) {
        Optional<Body> body = response.getBody();
        String message = body.isPresent() ? body.get().bodyAsString() : "no response";
        throw new IOException(message);
      }
    }
  }

  /**
   * Creating the core settings
   *
   * @param client the client to use
   * @throws IOException in case of error during the call to opensearch
   */
  private void createCoreSettings(OpenSearchClient client) throws IOException {
    PutComponentTemplateRequest.Builder coreSettings = new PutComponentTemplateRequest.Builder();
    coreSettings.name(config.getIndexPrefix() + ES_CORE_SETTINGS);
    coreSettings.create(false);
    coreSettings.template(
        new IndexState.Builder()
            .settings(
                new IndexSettings.Builder()
                    .maxResultWindow(config.getMaxResultWindow())
                    .numberOfReplicas(Integer.parseInt(config.getNumberOfReplicas()))
                    .numberOfShards(Integer.parseInt(config.getNumberOfShards()))
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

  /**
   * Create the index
   *
   * @param client the client to use
   * @param name the name of the index
   * @param version the version
   * @param mappings the mappings of the data
   * @throws IOException in case of error during the call to opensearch
   */
  @SuppressWarnings("SameParameterValue")
  private void createIndex(
      OpenSearchClient client, String name, String version, Map<String, Property> mappings)
      throws IOException {
    // Create template
    String indexName = config.getIndexPrefix() + "_" + name;
    String coreSettings = config.getIndexPrefix() + ES_CORE_SETTINGS;
    PutIndexTemplateRequest.Builder template = new PutIndexTemplateRequest.Builder();
    template.name(indexName);
    template.meta("version", JsonData.of(version));
    template.indexPatterns(indexName + "*");
    template.composedOf(coreSettings);
    TypeMapping indexMapping =
        new TypeMapping.Builder()
            .dynamic(DynamicMapping.Strict)
            .dateDetection(false)
            .numericDetection(false)
            .properties(mappings)
            .build();
    template.template(
        new IndexTemplateMapping.Builder()
            .settings(
                new IndexSettings.Builder()
                    .customSettings(
                        Map.of(
                            "plugins",
                            JsonData.of(
                                String.format(
                                    """
                                  "index_state_management": {
                                    "rollover_alias": "%s",
                                  }
                            """,
                                    indexName))))
                    .mapping(
                        new IndexSettingsMapping.Builder()
                            .totalFields(
                                new IndexSettingsMappingLimitTotalFields.Builder()
                                    .limit(Long.parseLong(config.getMaxFieldsSize()))
                                    .build())
                            .build())
                    .build()
                    .index())
            .mappings(indexMapping)
            .build());
    try {
      client.indices().putIndexTemplate(template.build());
    } catch (Exception e) {
      throw new IOException(e);
    }
    // Create index
    try {
      client.indices().get(new GetIndexRequest.Builder().index(indexName).build());
    } catch (OpenSearchException e) {
      client
          .indices()
          .create(
              new CreateIndexRequest.Builder()
                  .index(indexName + config.getIndexSuffix())
                  .aliases(indexName, new Alias.Builder().build())
                  .build());
    }
  }

  /**
   * Mapping generator for the class representing the ES Model
   *
   * @param esModel the esmodel to use
   * @return a map of properties
   */
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
    Field[] parentFields = model.getSuperclass().getDeclaredFields();
    Field[] directFields = model.getDeclaredFields();
    Field[] fields = ArrayUtils.addAll(directFields, parentFields);
    for (Field field : fields) {
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
            new Property.Builder().date(new DateProperty.Builder().build()).build());
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
        throw new StartupException("Error with Opensearch - Unsupported field type: " + fieldType);
      }
    }
    return mappings;
  }

  /**
   * Creating the opensearchClient
   *
   * @return the client
   * @param <T> a type extending EsBase
   * @throws Exception in case of an exception during the calls to opensearch
   */
  public <T extends EsBase> OpenSearchClient opensearchClient() throws Exception {
    log.info("Creating OpensearchClient");
    OpenSearchClient openClient = getOpensearchClient();
    // Try to client configuration
    try {
      InfoResponse info = openClient.info();
      log.info("OpensearchClient ready for {} - {}", info.name(), info.version());
    } catch (Exception e) {
      log.error(String.format("Error activating Opensearch engine: %s", e.getMessage()), e);
      throw new IllegalStateException("Failed to connect to Opensearch", e);
    }
    // TODO enable telemetry ?
    // Initialize opensearch if needed.
    createRolloverPolicy(openClient);
    createCoreSettings(openClient);
    // TODO Fetch the current model versions
    // | type     | last_updated_at      | version db | search version
    // | findings | 2024-12-04T12:00:00Z | 2.0        | 1.0
    // If version of the model stored in opensearch is different from the db version
    // Index + template must be removed and recreated
    // last_updated_at for the type must be reset to reindex the full data.
    List<EsModel<T>> models = this.searchEngine.getModels();
    models.stream()
        .parallel()
        .forEach(
            esModel -> {
              Map<String, Property> mappings = mappingGeneratorForClass(esModel);
              try {
                // Cleanup old index
                if (indexingStatusRepository.findByType(esModel.getName()).isEmpty()) {
                  log.info("Cleanup old Index {}", esModel.getName());
                  cleanUpIndex(esModel.getName(), openClient);
                }
                log.info("Creating Index {}", esModel.getName());
                createIndex(openClient, esModel.getName(), ES_MODEL_VERSION, mappings);
              } catch (IOException e) {
                throw new AnalyticsEngineException(
                    "Error while cleanup of indexes with Opensearch - " + e);
              }
            });
    return openClient;
  }

  /**
   * Clean up of the index
   *
   * @param indexName the name of the index
   * @param client the client to use
   * @throws IOException in case of an exception during the call to opensearch
   */
  public void cleanUpIndex(String indexName, OpenSearchClient client) throws IOException {
    try {
      String fullIndexName = config.getIndexPrefix() + "_" + indexName;
      String fullIndexWithSuffix = fullIndexName + config.getIndexSuffix();

      // 1. Delete index and alias if they exist
      for (String name : List.of(fullIndexName, fullIndexWithSuffix)) {
        try {
          client.indices().delete(d -> d.index(name));
          log.info("Deleted index: {}", name);
        } catch (OpenSearchException e) {
          log.warn("Index " + name + " does not exist or already deleted");
        }
      }

      // 2. Delete index template
      try {
        client.indices().deleteIndexTemplate(d -> d.name(fullIndexName));
        log.info("Deleted index template: " + fullIndexName);
      } catch (OpenSearchException e) {
        log.warn("Index template {} does not exist or already deleted", fullIndexName);
      }
    } catch (IOException e) {
      throw new AnalyticsEngineException("Failed to delete index " + indexName, e);
    }
  }
}
