package io.openaev.integration.migration;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.integration.impl.executors.caldera.CalderaExecutorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utils.fixtures.CatalogConnectorFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.mockConfig.executors.WithMockCalderaConfig;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

public class CalderaExecutorConfigurationMigrationTest {

  @Nested
  @SpringBootTest
  @TestExecutionListeners(
      value = {RabbitMQTestListener.class},
      mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
  @Transactional
  @WithMockCalderaConfig(
      enable = true,
      url = "caldera_url",
      publicUrl = "caldera_public_url",
      apiKey = "caldera_api_key")
  @DisplayName("With enabled configuration")
  public class WithEnabledConfiguration {

    @Autowired private CalderaExecutorConfigurationMigration calderaExecutorConfigurationMigration;

    @Autowired private CatalogConnectorService catalogConnectorService;
    @Autowired private ConnectorInstanceService connectorInstanceService;
    @Autowired private CatalogConnectorComposer catalogConnectorComposer;

    @Autowired private CalderaExecutorConfig beanConfig;

    @Test
    @DisplayName("Resulting instance is running")
    public void whenConfigIsEnabled_resultingInstanceIsRunning() throws Exception {
      catalogConnectorComposer
          .forCatalogConnector(
              CatalogConnectorFixture.createCatalogConnectorWithClassName(
                  CalderaExecutorIntegrationFactory.class.getCanonicalName()))
          .persist();

      calderaExecutorConfigurationMigration.migrate();

      Optional<CatalogConnector> connector =
          catalogConnectorService.findByFactoryClassName(
              CalderaExecutorIntegrationFactory.class.getCanonicalName());
      assertThat(connector).isPresent();

      ConnectorInstancePersisted instance =
          connectorInstanceService.findAllByCatalogConnector(connector.get()).getFirst();

      assertThat(instance).isInstanceOf(ConnectorInstancePersisted.class);
      assertThat(instance.getRequestedStatus())
          .isEqualTo(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    }

    @Test
    @DisplayName("It migrates the config")
    public void whenConfigIsEnabled_itMigratesTheConfig() throws Exception {
      catalogConnectorComposer
          .forCatalogConnector(
              CatalogConnectorFixture.createCatalogConnectorWithClassName(
                  CalderaExecutorIntegrationFactory.class.getCanonicalName()))
          .persist();

      calderaExecutorConfigurationMigration.migrate();

      Optional<CatalogConnector> connector =
          catalogConnectorService.findByFactoryClassName(
              CalderaExecutorIntegrationFactory.class.getCanonicalName());
      assertThat(connector).isPresent();

      ConnectorInstancePersisted instance =
          connectorInstanceService.findAllByCatalogConnector(connector.get()).getFirst();

      assertThat(instance).isInstanceOf(ConnectorInstancePersisted.class);
      assertThat(instance.getConfigurations())
          .usingComparatorForType(
              (left, right) ->
                  left.getKey().compareTo(right.getKey())
                      & left.getValue().toString().compareTo(right.getValue().toString()),
              ConnectorInstanceConfiguration.class)
          .hasSameElementsAs(beanConfig.toInstanceConfigurationSet(instance));
    }
  }

  @Nested
  @SpringBootTest
  @Transactional
  @TestExecutionListeners(
      value = {RabbitMQTestListener.class},
      mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
  @WithMockCalderaConfig(
      enable = false,
      url = "caldera_url",
      publicUrl = "caldera_public_url",
      apiKey = "caldera_api_key")
  @DisplayName("With disabled configuration")
  public class WithDisabledConfiguration {

    @Autowired private CalderaExecutorConfigurationMigration calderaExecutorConfigurationMigration;

    @Autowired private CatalogConnectorService catalogConnectorService;
    @Autowired private ConnectorInstanceService connectorInstanceService;
    @Autowired private CatalogConnectorComposer catalogConnectorComposer;

    @Test
    @DisplayName("Resulting instance is stopped")
    public void whenConfigIsEnabled_resultingInstanceIsStopped() throws Exception {
      catalogConnectorComposer
          .forCatalogConnector(
              CatalogConnectorFixture.createCatalogConnectorWithClassName(
                  CalderaExecutorIntegrationFactory.class.getCanonicalName()))
          .persist();

      calderaExecutorConfigurationMigration.migrate();

      Optional<CatalogConnector> connector =
          catalogConnectorService.findByFactoryClassName(
              CalderaExecutorIntegrationFactory.class.getCanonicalName());
      assertThat(connector).isPresent();

      ConnectorInstancePersisted instance =
          connectorInstanceService.findAllByCatalogConnector(connector.get()).getFirst();

      assertThat(instance).isInstanceOf(ConnectorInstancePersisted.class);
      assertThat(instance.getRequestedStatus())
          .isEqualTo(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    }
  }
}
