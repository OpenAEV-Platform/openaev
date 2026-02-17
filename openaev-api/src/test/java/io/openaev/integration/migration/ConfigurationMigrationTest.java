package io.openaev.integration.migration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.integration.configuration.BaseIntegrationConfiguration;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import io.openaev.utils.fixtures.CatalogConnectorFixture;
import io.openaev.utils.fixtures.ConnectorInstanceFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utilstest.RabbitMQTestListener;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class ConfigurationMigrationTest {
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private EntityManager entityManager;

  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private EncryptionFactory encryptionFactory;

  private static class TestIntegrationConfiguration extends BaseIntegrationConfiguration {}

  private class TestConfigurationMigration extends ConfigurationMigration {
    public static String FACTORY_CLASSNAME = "TestConfigurationMigration_TestIntegrationFactory";

    public TestConfigurationMigration() {
      super(
          new TestIntegrationConfiguration(),
          FACTORY_CLASSNAME,
          catalogConnectorService,
          connectorInstanceService,
          encryptionFactory);
    }
  }

  @Test
  @DisplayName("When catalog connector does not exist, migration fails")
  public void whenCatalogConnectorDoesNotExist_migrationFails() throws Exception {
    ConfigurationMigration migration = new TestConfigurationMigration();

    assertThatThrownBy(migration::migrate)
        .hasMessageContaining(
            "Configuration found for %s but no related connector in catalog"
                .formatted(TestConfigurationMigration.FACTORY_CLASSNAME));
  }

  @Nested
  @DisplayName("When catalog connector exists")
  public class WhenCatalogConnectorExists {
    @Test
    @DisplayName("When configuration not yet migrated, migrate successfully")
    public void whenConfigurationNotYetMigrated_migrateSuccessfully() throws Exception {
      CatalogConnector connector =
          catalogConnectorComposer
              .forCatalogConnector(
                  CatalogConnectorFixture.createCatalogConnectorWithClassName(
                      TestConfigurationMigration.FACTORY_CLASSNAME))
              .persist()
              .get();
      ConfigurationMigration migration = new TestConfigurationMigration();

      migration.migrate();

      List<ConnectorInstance> instances =
          connectorInstanceService.findAllByCatalogConnector(connector).stream()
              .map(i -> (ConnectorInstance) i)
              .toList();

      assertThat(instances.size()).isEqualTo(1);

      ConnectorInstancePersisted singleInstance = (ConnectorInstancePersisted) instances.getFirst();
      assertThat(singleInstance.getCatalogConnector()).isEqualTo(connector);
      assertThat(singleInstance.getClassName())
          .isEqualTo(TestConfigurationMigration.FACTORY_CLASSNAME);
      assertThat(singleInstance.getSource())
          .isEqualTo(ConnectorInstance.SOURCE.PROPERTIES_MIGRATION);
    }

    @Test
    @DisplayName("When configuration already migrated, abort migration")
    public void whenConfigurationAlreadyMigrated_abortMigration() throws Exception {
      CatalogConnector connector =
          catalogConnectorComposer
              .forCatalogConnector(
                  CatalogConnectorFixture.createCatalogConnectorWithClassName(
                      TestConfigurationMigration.FACTORY_CLASSNAME))
              .withConnectorInstance(
                  connectorInstanceComposer.forConnectorInstance(
                      ConnectorInstanceFixture.createMigratedInstance()))
              .persist()
              .get();
      ConfigurationMigration migration = new TestConfigurationMigration();

      migration.migrate();

      List<ConnectorInstance> instances =
          connectorInstanceService.findAllByCatalogConnector(connector).stream()
              .map(i -> (ConnectorInstance) i)
              .toList();

      assertThat(instances.size()).isEqualTo(1);

      ConnectorInstancePersisted singleInstance = (ConnectorInstancePersisted) instances.getFirst();
      assertThat(singleInstance.getCatalogConnector()).isEqualTo(connector);
      assertThat(singleInstance.getClassName())
          .isEqualTo(TestConfigurationMigration.FACTORY_CLASSNAME);
      assertThat(singleInstance.getSource())
          .isEqualTo(ConnectorInstance.SOURCE.PROPERTIES_MIGRATION);
    }
  }
}
