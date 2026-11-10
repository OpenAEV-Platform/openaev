package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.config.EngineConfig;
import io.openaev.database.model.IndexingStatus;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.impl.elasticsearch.es8.ElasticDriver;
import io.openaev.engine.impl.opensearch.os3.OpenSearchDriver;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * The boot-time setup of an index that already exists: the fields its model gained since the index
 * was created are added in place, so documents carrying them are accepted without wiping and
 * rebuilding the index - which would leave its dashboards incomplete for the whole rebuild. An
 * index that cannot take the current mapping in place is reset instead: its documents carrying a
 * new field would otherwise be rejected and skipped for good.
 *
 * <p>Runs on whichever engine the suite is configured with (Elasticsearch 8 in CI, Elasticsearch 9
 * and OpenSearch in the nightly matrix). Two things have no path through the {@link EngineService}
 * facade, so this test, and this test only, goes around it:
 *
 * <ul>
 *   <li>the boot-time setup is private to the drivers and runs when one builds its client: {@link
 *       #runBootSetup} calls the driver matching the detected engine type;
 *   <li>creating an index with a legacy mapping, writing a raw document into it and reading its
 *       mapping go through raw HTTP ({@link #engineRequest}): these calls are the same REST API on
 *       Elasticsearch 8, Elasticsearch 9 and OpenSearch.
 * </ul>
 */
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Engine index mapping update at boot")
class EngineIndexMappingUpdateTest extends IntegrationTest {

  private static final String FINDING_MODEL = "finding";
  private static final String NEW_FIELD = "finding_masking_version";

  /**
   * A field mapped exactly as the current model maps it, so that only the new field differs: the
   * text fields carry a normalized keyword sub-field, which a hand-written mapping would not match.
   */
  private static final String LEGACY_FIELD = "base_created_at";

  private static final String LEGACY_DATE = "2026-01-01T00:00:00Z";
  private static final Map<String, Object> LEGACY_PROPERTIES =
      Map.of(LEGACY_FIELD, Map.of("type", "date"));

  @Autowired private ElasticDriver es8Driver;
  @Autowired private io.openaev.engine.impl.elasticsearch.es9.ElasticDriver es9Driver;
  @Autowired private OpenSearchDriver openSearchDriver;
  @Autowired private EngineService engineService;
  @Autowired private EngineConfig engineConfig;
  @Autowired private IndexingStatusRepository indexingStatusRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private String alias;

  @BeforeEach
  void setUp() {
    alias = engineConfig.getIndexPrefix() + "_" + FINDING_MODEL;
    // An initialized model: a missing row or a reset request would wipe the index at boot
    // instead of updating it, which is precisely the path these tests must not take.
    IndexingStatus status =
        indexingStatusRepository
            .findByType(FINDING_MODEL)
            .orElseGet(
                () -> {
                  IndexingStatus newStatus = new IndexingStatus();
                  newStatus.setType(FINDING_MODEL);
                  return newStatus;
                });
    status.setLastIndexing(Instant.EPOCH);
    status.setLastId(null);
    indexingStatusRepository.save(status);
  }

  @AfterEach
  void tearDown() throws Exception {
    // Leave a regular, up-to-date index behind for the next test classes
    engineService.cleanUpIndex(FINDING_MODEL, true);
    runBootSetup();
  }

  // -- Engine access outside the facade (see the class Javadoc) --

  /**
   * Runs the boot-time index setup of the configured engine: it runs when a driver builds its
   * client, which is closed right away.
   */
  private void runBootSetup() throws Exception {
    switch (engineService.getEngineType()) {
      case ELASTICSEARCH_8 -> es8Driver.elasticClient()._transport().close();
      case ELASTICSEARCH_9 -> es9Driver.elasticClient()._transport().close();
      case OPENSEARCH -> openSearchDriver.opensearchClient()._transport().close();
      default ->
          throw new IllegalStateException(
              "Unsupported engine type: " + engineService.getEngineType());
    }
  }

  private HttpResponse<String> engineRequest(String method, String path, Object body)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(engineConfig.getUrl() + path))
            .header("Content-Type", "application/json")
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
    if (engineConfig.getUsername() != null) {
      String credentials = engineConfig.getUsername() + ":" + engineConfig.getPassword();
      request.header(
          "Authorization",
          "Basic "
              + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    }
    return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Replaces the finding index with one shaped like an index created by a version whose model had
   * fewer fields: the core settings, a strict mapping with the given properties only, and no
   * template left to complete it.
   */
  private void createLegacyFindingIndex(Map<String, Object> properties) throws Exception {
    engineService.cleanUpIndex(FINDING_MODEL, true);
    // The core settings every OpenAEV index is created with: the keyword sub-fields of the
    // model's mapping refer to this normalizer.
    Map<String, Object> settings =
        Map.of(
            "analysis",
            Map.of(
                "normalizer",
                Map.of(
                    "string_normalizer",
                    Map.of("type", "custom", "filter", List.of("lowercase", "asciifolding")))));
    HttpResponse<String> response =
        engineRequest(
            "PUT",
            "/" + alias + engineConfig.getIndexSuffix(),
            Map.of(
                "aliases",
                Map.of(alias, Map.of()),
                "settings",
                settings,
                "mappings",
                Map.of("dynamic", "strict", "properties", properties)));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
  }

  /** Writes a raw document to the finding index, and returns whether the engine accepted it. */
  private boolean indexRawDocument(String id, Map<String, Object> document) throws Exception {
    int status =
        engineRequest("PUT", "/" + alias + "/_doc/" + id + "?refresh=true", document).statusCode();
    return status >= 200 && status < 300;
  }

  private boolean documentExists(String id) throws Exception {
    return engineRequest("GET", "/" + alias + "/_doc/" + id, null).statusCode() == 200;
  }

  private JsonNode mappingOfFindingIndex() throws Exception {
    HttpResponse<String> response = engineRequest("GET", "/" + alias + "/_mapping", null);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    // Keyed by the physical index name the alias resolves to
    return objectMapper
        .readTree(response.body())
        .elements()
        .next()
        .path("mappings")
        .path("properties");
  }

  // -- Tests --

  @Nested
  @DisplayName("When the model gained a field since the index was created")
  class WhenTheModelGainedAField {

    @Test
    @DisplayName("Boot adds the field to the existing index, which then accepts it")
    void given_legacyIndexWithoutNewField_should_addFieldAndAcceptDocumentsCarryingIt()
        throws Exception {
      // Arrange
      createLegacyFindingIndex(LEGACY_PROPERTIES);
      assertThat(indexRawDocument("before", Map.of(NEW_FIELD, 1L)))
          .as("a strict legacy index rejects the field it does not know")
          .isFalse();

      // Act
      runBootSetup();

      // Assert
      assertThat(mappingOfFindingIndex().path(NEW_FIELD).path("type").asText()).isEqualTo("long");
      assertThat(indexRawDocument("after", Map.of(LEGACY_FIELD, LEGACY_DATE, NEW_FIELD, 1L)))
          .isTrue();
    }

    @Test
    @DisplayName("Boot keeps the documents of the existing index: nothing to rebuild")
    void given_legacyIndexWithDocuments_should_keepThemThroughTheUpdate() throws Exception {
      // Arrange
      createLegacyFindingIndex(LEGACY_PROPERTIES);
      assertThat(indexRawDocument("existing", Map.of(LEGACY_FIELD, LEGACY_DATE))).isTrue();

      // Act
      runBootSetup();

      // Assert
      assertThat(documentExists("existing")).isTrue();
    }
  }

  @Nested
  @DisplayName("When the existing index cannot take the current mapping")
  class WhenTheMappingCannotBeUpdated {

    @Test
    @DisplayName("Boot resets the index, which is rebuilt with the current mapping")
    void given_legacyIndexWithConflictingFieldType_should_resetTheIndex() throws Exception {
      // Arrange
      createLegacyFindingIndex(Map.of(NEW_FIELD, Map.of("type", "keyword")));
      assertThat(indexRawDocument("existing", Map.of(NEW_FIELD, "legacy"))).isTrue();
      IndexingStatus status = indexingStatusRepository.findByType(FINDING_MODEL).orElseThrow();
      status.setLastIndexing(Instant.parse(LEGACY_DATE));
      indexingStatusRepository.save(status);
      // The reset schedules an epoch re-assertion of the finding cursor: none may fire later in
      // the suite
      long resetDelay = engineConfig.getIndexingResetEpochReassertDelaySeconds();
      engineConfig.setIndexingResetEpochReassertDelaySeconds(0);
      try {
        // Act
        runBootSetup();
      } finally {
        engineConfig.setIndexingResetEpochReassertDelaySeconds(resetDelay);
      }

      // Assert
      assertThat(mappingOfFindingIndex().path(NEW_FIELD).path("type").asText()).isEqualTo("long");
      assertThat(documentExists("existing"))
          .as("the index was wiped: the sync rebuilds it from the database")
          .isFalse();
      assertThat(indexingStatusRepository.findByType(FINDING_MODEL).orElseThrow().getLastIndexing())
          .isEqualTo(Instant.EPOCH);
    }
  }
}
