package io.openaev.integration.migration;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.executors.sentinelone.config.SentinelOneExecutorConfig;
import io.openaev.integration.impl.executors.sentinelone.SentinelOneExecutorIntegrationFactory;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import io.openaev.utils.fixtures.CatalogConnectorFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.mockConfig.executors.WithMockSentinelOneConfig;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

public class SentinelOneExecutorConfigurationMigrationTest {

  @Nested
  @SpringBootTest
  @Transactional
  @TestExecutionListeners(
      value = {RabbitMQTestListener.class},
      mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
  @WithMockSentinelOneConfig(
      enable = true,
      url = "sentinelOne_url",
      apiKey = "sentinelOne_api_key",
      apiRegisterInterval = 1234,
      accountId = "so_acct_id",
      cleanImplantInterval = 4321,
      apiBatchExecutionActionPagination = 5678,
      windowsScriptId = "so_windows_script_id",
      unixScriptId = "so_unix_script_id",
      groupId = "so_group_id",
      siteId = "so_site_id")
  @DisplayName("With enabled configuration")
  public class WithEnabledConfiguration {

    @Autowired
    private SentinelOneExecutorConfigurationMigration sentinelOneExecutorConfigurationMigration;

    @Autowired private CatalogConnectorService catalogConnectorService;
    @Autowired private ConnectorInstanceService connectorInstanceService;
    @Autowired private CatalogConnectorComposer catalogConnectorComposer;
    @Autowired private EncryptionFactory encryptionFactory;

    @Autowired private SentinelOneExecutorConfig beanConfig;

    @Test
    @DisplayName("Resulting instance is running")
    public void whenConfigIsEnabled_resultingInstanceIsRunning() throws Exception {
      catalogConnectorComposer
          .forCatalogConnector(
              CatalogConnectorFixture.createCatalogConnectorWithClassName(
                  SentinelOneExecutorIntegrationFactory.class.getCanonicalName()))
          .persist();

      sentinelOneExecutorConfigurationMigration.migrate();

      Optional<CatalogConnector> connector =
          catalogConnectorService.findByFactoryClassName(
              SentinelOneExecutorIntegrationFactory.class.getCanonicalName());
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
                  SentinelOneExecutorIntegrationFactory.class.getCanonicalName()))
          .persist();

      sentinelOneExecutorConfigurationMigration.migrate();

      Optional<CatalogConnector> connector =
          catalogConnectorService.findByFactoryClassName(
              SentinelOneExecutorIntegrationFactory.class.getCanonicalName());
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
          .hasSameElementsAs(
              beanConfig.toInstanceConfigurationSet(
                  instance,
                  encryptionFactory.getEncryptionService(instance.getCatalogConnector())));

      assertThat("sentinelOne_api_key")
          .isNotEqualTo(
              instance.getConfigurations().stream()
                  .filter(
                      connectorInstanceConfiguration ->
                          "EXECUTOR_SENTINELONE_API_KEY"
                              .equals(connectorInstanceConfiguration.getKey()))
                  .findFirst()
                  .orElseThrow()
                  .getValue()
                  .asText());
    }
  }

  @Nested
  @SpringBootTest
  @Transactional
  @TestExecutionListeners(
      value = {RabbitMQTestListener.class},
      mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
  @WithMockSentinelOneConfig(
      enable = false,
      url = "sentinelOne_url",
      apiKey = "sentinelOne_api_key",
      apiRegisterInterval = 1234,
      accountId = "so_acct_id",
      cleanImplantInterval = 4321,
      apiBatchExecutionActionPagination = 5678,
      windowsScriptId = "so_windows_script_id",
      unixScriptId = "so_unix_script_id",
      groupId = "so_group_id",
      siteId = "so_site_id")
  @DisplayName("With disabled configuration")
  public class WithDisabledConfiguration {

    @Autowired
    private SentinelOneExecutorConfigurationMigration sentinelOneExecutorConfigurationMigration;

    @Autowired private CatalogConnectorService catalogConnectorService;
    @Autowired private ConnectorInstanceService connectorInstanceService;
    @Autowired private CatalogConnectorComposer catalogConnectorComposer;

    @Test
    @DisplayName("Resulting instance is stopped")
    public void whenConfigIsEnabled_resultingInstanceIsStopped() throws Exception {
      catalogConnectorComposer
          .forCatalogConnector(
              CatalogConnectorFixture.createCatalogConnectorWithClassName(
                  SentinelOneExecutorIntegrationFactory.class.getCanonicalName()))
          .persist();

      sentinelOneExecutorConfigurationMigration.migrate();

      Optional<CatalogConnector> connector =
          catalogConnectorService.findByFactoryClassName(
              SentinelOneExecutorIntegrationFactory.class.getCanonicalName());
      assertThat(connector).isPresent();

      ConnectorInstancePersisted instance =
          connectorInstanceService.findAllByCatalogConnector(connector.get()).getFirst();

      assertThat(instance).isInstanceOf(ConnectorInstancePersisted.class);
      assertThat(instance.getRequestedStatus())
          .isEqualTo(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    }
  }
}
