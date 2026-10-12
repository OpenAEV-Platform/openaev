package io.openaev.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import es8.co.elastic.clients.elasticsearch.ElasticsearchClient;
import es8.co.elastic.clients.elasticsearch._types.ElasticsearchException;
import es8.co.elastic.clients.elasticsearch._types.Refresh;
import es8.co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import es8.co.elastic.clients.elasticsearch._types.mapping.Property;
import io.openaev.IntegrationTest;
import io.openaev.config.EngineConfig;
import io.openaev.database.model.IndexingStatus;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.engine.impl.elasticsearch.es8.ElasticDriver;
import java.time.Instant;
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
 * rebuilding the index - which would leave its dashboards incomplete for the whole rebuild.
 *
 * <p>Runs on the driver the test suite is configured with ({@code engine.engine-selector=elk}, the
 * Elasticsearch 8 driver). The Elasticsearch 9 and OpenSearch drivers carry the same update.
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
  private static final Map<String, Property> LEGACY_PROPERTIES =
      Map.of(LEGACY_FIELD, Property.of(p -> p.date(d -> d)));

  @Autowired private ElasticDriver elasticDriver;
  @Autowired private EngineConfig engineConfig;
  @Autowired private IndexingStatusRepository indexingStatusRepository;

  private ElasticsearchClient client;
  private String alias;

  @BeforeEach
  void setUp() throws Exception {
    client = elasticDriver.elasticClient();
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
    elasticDriver.cleanUpIndex(FINDING_MODEL, client, true);
    elasticDriver.elasticClient()._transport().close();
    client._transport().close();
  }

  /**
   * Replaces the finding index with one shaped like an index created by a version whose model had
   * fewer fields: the core settings, a strict mapping with the given properties only, and no
   * template left to complete it.
   */
  private void createLegacyFindingIndex(Map<String, Property> properties) throws Exception {
    elasticDriver.cleanUpIndex(FINDING_MODEL, client, true);
    client
        .indices()
        .create(
            c ->
                c.index(alias + engineConfig.getIndexSuffix())
                    .aliases(alias, a -> a)
                    // The core settings every OpenAEV index is created with: the keyword
                    // sub-fields of the model's mapping refer to this normalizer.
                    .settings(
                        st ->
                            st.analysis(
                                an ->
                                    an.normalizer(
                                        "string_normalizer",
                                        n ->
                                            n.custom(
                                                cn -> cn.filter("lowercase", "asciifolding")))))
                    .mappings(m -> m.dynamic(DynamicMapping.Strict).properties(properties)));
  }

  private Map<String, Property> mappingOfFindingIndex() throws Exception {
    return client
        .indices()
        .getMapping(g -> g.index(alias))
        .result()
        .values()
        .iterator()
        .next()
        .mappings()
        .properties();
  }

  @Nested
  @DisplayName("When the model gained a field since the index was created")
  class WhenTheModelGainedAField {

    @Test
    @DisplayName("Boot adds the field to the existing index, which then accepts it")
    void given_legacyIndexWithoutNewField_should_addFieldAndAcceptDocumentsCarryingIt()
        throws Exception {
      // Arrange
      createLegacyFindingIndex(LEGACY_PROPERTIES);
      assertThatThrownBy(
              () -> client.index(i -> i.index(alias).id("before").document(Map.of(NEW_FIELD, 1L))))
          .as("a strict legacy index rejects the field it does not know")
          .isInstanceOf(ElasticsearchException.class);

      // Act
      elasticDriver.elasticClient()._transport().close();

      // Assert
      assertThat(mappingOfFindingIndex()).containsKey(NEW_FIELD);
      assertThat(mappingOfFindingIndex().get(NEW_FIELD).isLong()).isTrue();
      assertThatCode(
              () ->
                  client.index(
                      i ->
                          i.index(alias)
                              .id("after")
                              .document(Map.of(LEGACY_FIELD, LEGACY_DATE, NEW_FIELD, 1L))))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Boot keeps the documents of the existing index: nothing to rebuild")
    void given_legacyIndexWithDocuments_should_keepThemThroughTheUpdate() throws Exception {
      // Arrange
      createLegacyFindingIndex(LEGACY_PROPERTIES);
      client.index(
          i ->
              i.index(alias)
                  .id("existing")
                  .document(Map.of(LEGACY_FIELD, LEGACY_DATE))
                  .refresh(Refresh.True));

      // Act
      elasticDriver.elasticClient()._transport().close();

      // Assert
      assertThat(client.exists(e -> e.index(alias).id("existing")).value()).isTrue();
    }
  }

  @Nested
  @DisplayName("When a field changed type since the index was created")
  class WhenAFieldChangedType {

    @Test
    @DisplayName("Boot does not fail, and leaves the conflicting field as it was")
    void given_legacyIndexWithConflictingFieldType_should_notFailTheBoot() throws Exception {
      // Arrange
      createLegacyFindingIndex(Map.of(NEW_FIELD, Property.of(p -> p.keyword(k -> k))));

      // Act & Assert
      assertThatCode(() -> elasticDriver.elasticClient()._transport().close())
          .doesNotThrowAnyException();
      assertThat(mappingOfFindingIndex().get(NEW_FIELD).isKeyword()).isTrue();
    }
  }
}
