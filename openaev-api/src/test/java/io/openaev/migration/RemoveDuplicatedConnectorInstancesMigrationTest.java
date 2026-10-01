package io.openaev.migration;

import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies that instances sharing the same connector id are reduced to one: the started instance
 * wins, then the oldest; instances with distinct ids are untouched and re-running is a no-op.
 *
 * <p>{@code @Transactional} so the seeded rows and migration side effects roll back with the test
 * transaction.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Remove duplicated connector instances migration")
class RemoveDuplicatedConnectorInstancesMigrationTest extends IntegrationTest {

  @Autowired private V6_20261001120000000__Remove_duplicated_connector_instances migration;

  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

  private CatalogConnectorComposer.Composer catalogConnector;

  @BeforeEach
  void setUp() {
    catalogConnector =
        catalogConnectorComposer.forCatalogConnector(
            createDefaultCatalogConnectorManagedByXtmComposer("MITRE Att&ck"));
  }

  private String persistInstance(String collectorId, ConnectorInstance.CURRENT_STATUS_TYPE status)
      throws JsonProcessingException {
    ConnectorInstancePersisted instance = createDefaultConnectorInstance();
    instance.setCurrentStatus(status);
    String instanceId =
        connectorInstanceComposer
            .forConnectorInstance(instance)
            .withConnectorInstanceConfiguration(
                connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                    createConnectorInstanceConfiguration("COLLECTOR_ID", collectorId)))
            .withCatalogConnector(catalogConnector)
            .persist()
            .get()
            .getId();
    entityManager.flush();
    return instanceId;
  }

  private void setCreatedAt(String instanceId, String createdAt) {
    entityManager
        .createNativeQuery(
            "UPDATE connector_instances SET connector_instance_created_at = CAST(:createdAt AS"
                + " timestamptz) WHERE connector_instance_id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", instanceId)
        .executeUpdate();
  }

  private boolean instanceExists(String instanceId) {
    return ((Number)
                entityManager
                    .createNativeQuery(
                        "SELECT count(*) FROM connector_instances WHERE connector_instance_id = :id")
                    .setParameter("id", instanceId)
                    .getSingleResult())
            .longValue()
        == 1;
  }

  private long configurationCount(String instanceId) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM connector_instance_configurations"
                        + " WHERE connector_instance_id = :id")
                .setParameter("id", instanceId)
                .getSingleResult())
        .longValue();
  }

  private void runMigration() {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                migration.migrate(
                    new Context() {
                      @Override
                      public Configuration getConfiguration() {
                        return null;
                      }

                      @Override
                      public java.sql.Connection getConnection() {
                        return connection;
                      }
                    });
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
    entityManager.clear();
  }

  @Test
  @DisplayName("Keeps the started instance and removes the other one with its configurations")
  void started_instance_is_kept() throws JsonProcessingException {
    String collectorId = UUID.randomUUID().toString();
    String stopped = persistInstance(collectorId, ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    String started = persistInstance(collectorId, ConnectorInstance.CURRENT_STATUS_TYPE.started);
    setCreatedAt(stopped, "2026-09-01T10:00:00Z");
    setCreatedAt(started, "2026-09-02T10:00:00Z");

    runMigration();

    assertThat(instanceExists(started)).isTrue();
    assertThat(instanceExists(stopped)).isFalse();
    assertThat(configurationCount(stopped)).isZero();
  }

  @Test
  @DisplayName("Keeps the oldest instance when none is started")
  void oldest_instance_is_kept() throws JsonProcessingException {
    String collectorId = UUID.randomUUID().toString();
    String newer = persistInstance(collectorId, ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    String older = persistInstance(collectorId, ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    setCreatedAt(newer, "2026-09-02T10:00:00Z");
    setCreatedAt(older, "2026-09-01T10:00:00Z");

    runMigration();

    assertThat(instanceExists(older)).isTrue();
    assertThat(instanceExists(newer)).isFalse();
  }

  @Test
  @DisplayName("Leaves instances with distinct connector ids untouched, and a re-run is a no-op")
  void distinct_instances_are_untouched() throws JsonProcessingException {
    String first =
        persistInstance(
            UUID.randomUUID().toString(), ConnectorInstance.CURRENT_STATUS_TYPE.started);
    String second =
        persistInstance(
            UUID.randomUUID().toString(), ConnectorInstance.CURRENT_STATUS_TYPE.stopped);

    runMigration();
    runMigration();

    assertThat(instanceExists(first)).isTrue();
    assertThat(instanceExists(second)).isTrue();
  }
}
