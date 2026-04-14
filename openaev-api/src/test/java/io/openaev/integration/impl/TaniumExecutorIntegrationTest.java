package io.openaev.integration.impl;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration.TANIUM_EXECUTOR_NAME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.tanium.client.TaniumExecutorClient;
import io.openaev.executors.tanium.config.TaniumExecutorConfig;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegration;
import io.openaev.integration.impl.executors.tanium.TaniumExecutorIntegrationFactory;
import io.openaev.integration.migration.TaniumExecutorConfigurationMigration;
import io.openaev.service.AgentService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.EndpointService;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import io.openaev.utils.reflection.FieldUtils;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@TestExecutionListeners(
    value = {RabbitMQTestListener.class},
    mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public class TaniumExecutorIntegrationTest {
  @Autowired private TaniumExecutorClient client;
  @Autowired private EndpointService endpointService;
  @Autowired private AgentService agentService;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private ExecutorService executorService;
  @Autowired private EnterpriseEditionService enterpriseEditionService;
  @Autowired private LicenseCacheManager licenseCacheManager;
  @Autowired private ComponentRequestEngine componentRequestEngine;
  @Autowired private ThreadPoolTaskScheduler taskScheduler;
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private CatalogConnectorRepository catalogConnectorRepository;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private TaniumExecutorConfig taniumExecutorConfig;
  @Autowired private EncryptionFactory encryptionFactory;
  @Autowired private BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;
  @Autowired private HttpClientFactory httpClientFactory;

  @Autowired private TaniumExecutorConfigurationMigration taniumExecutorConfigurationMigration;

  @Autowired private FileService fileService;

  private TaniumExecutorIntegrationFactory getFactory() {
    return new TaniumExecutorIntegrationFactory(
        connectorInstanceService,
        catalogConnectorService,
        executorService,
        componentRequestEngine,
        taniumExecutorConfigurationMigration,
        agentService,
        endpointService,
        assetGroupService,
        enterpriseEditionService,
        licenseCacheManager,
        taskScheduler,
        fileService,
        baseIntegrationConfigurationBuilder,
        httpClientFactory);
  }

  @Test
  @DisplayName("Factory is initialised correctly and creates catalog object")
  public void factoryIsInitialisedCorrectlyAndCreatesCatalogObject() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    assertThat(connectors).hasSize(1);
    AssertionsForClassTypes.assertThat(connectors.getFirst().getClassName())
        .isEqualTo(TaniumExecutorIntegrationFactory.class.getCanonicalName());
  }

  @Test
  @DisplayName("When factory syncs with stopped instance, integration is of status stopped")
  public void whenFactorySyncWithStoppedInstance_integrationIsOfStatusStopped() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    List<Integration> syncedIntegrations = integrationFactory.sync(new ArrayList<>(instances));

    assertThat(syncedIntegrations).hasSize(1);
    assertThat(syncedIntegrations).first().isInstanceOf(TaniumExecutorIntegration.class);
    assertThat(syncedIntegrations)
        .first()
        .satisfies(
            integration ->
                assertThat(integration.getCurrentStatus())
                    .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped));
  }

  @Test
  @DisplayName("When factory syncs with stopped instance, integration has no component of type")
  public void whenFactorySyncWithStoppedInstance_stoppedIntegrationHasNoComponentOfType()
      throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    List<Integration> syncedIntegrations = integrationFactory.sync(new ArrayList<>(instances));

    assertThat(syncedIntegrations).hasSize(1);
    assertThat(syncedIntegrations).first().isInstanceOf(TaniumExecutorIntegration.class);
    assertThat(syncedIntegrations)
        .first()
        .satisfies(
            integration ->
                assertThat(
                        integration.requestComponent(
                            new ComponentRequest(TANIUM_EXECUTOR_NAME),
                            ExecutorContextService.class))
                    .isEmpty());
  }

  @Test
  @DisplayName("When factory is initialised, there is an instance with correct configuration")
  public void whenFactoryIsInitialised_thereIsAnInstanceWithCorrectConfiguration()
      throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());

    assertThat(instances)
        .first()
        .satisfies(
            instance ->
                assertThat(instance.getConfigurations())
                    .usingComparatorForType(
                        (left, right) ->
                            left.getKey().compareTo(right.getKey())
                                & left.getValue().toString().compareTo(right.getValue().toString()),
                        ConnectorInstanceConfiguration.class)
                    .hasSameElementsAs(
                        taniumExecutorConfig.toInstanceConfigurationSet(
                            instance,
                            encryptionFactory.getEncryptionService(
                                instance.getCatalogConnector()))));
  }

  @Test
  @DisplayName(
      "When factory is initialised and an instance is spawned with an unsupported connector instance type, the encryption service is null")
  public void whenInstanceIsSpawn_encryptionServiceIsNull() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    Integration integration = integrationFactory.spawn(new ConnectorInstanceInMemory());
    AssertionsForClassTypes.assertThat(
            FieldUtils.computeAllFieldValues(integration).get("encryptionService"))
        .isNull();
  }

  @Test
  @DisplayName(
      "When spawning an integration with a null configuration builder, should throw ExecutorException")
  public void whenSpawnWithNullConfigBuilder_should_throwExecutorException() throws Exception {
    IntegrationFactory integrationFactory = getFactory();
    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    ConnectorInstancePersisted instance = instances.getFirst();

    // Act & Assert — passing null baseIntegrationConfigurationBuilder causes refresh() to fail
    assertThatThrownBy(
            () ->
                new TaniumExecutorIntegration(
                    instance,
                    connectorInstanceService,
                    endpointService,
                    agentService,
                    assetGroupService,
                    enterpriseEditionService,
                    licenseCacheManager,
                    componentRequestEngine,
                    executorService,
                    taskScheduler,
                    null,
                    httpClientFactory))
        .isInstanceOf(ExecutorException.class)
        .hasMessageContaining("Error during initialization of the Executor");
  }

  @Test
  @DisplayName(
      "When integration is stopped and requested status is starting, initialise should start it")
  public void whenStoppedAndStartingRequested_initialise_should_startIntegration()
      throws Exception {
    // Arrange
    IntegrationFactory integrationFactory = getFactory();
    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    ConnectorInstancePersisted instance =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst()).getFirst();

    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    connectorInstanceService.save(instance);

    Integration integration = integrationFactory.spawn(instance);
    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);

    // Act
    integration.initialise();

    // Assert
    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);
  }

  @Test
  @DisplayName(
      "When integration is started and requested status is stopping, initialise should stop it")
  public void whenStartedAndStoppingRequested_initialise_should_stopIntegration() throws Exception {
    // Arrange — start the integration first
    IntegrationFactory integrationFactory = getFactory();
    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    ConnectorInstancePersisted instance =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst()).getFirst();

    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    connectorInstanceService.save(instance);

    Integration integration = integrationFactory.spawn(instance);
    integration.initialise();
    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);

    // Arrange — now request stopping
    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    connectorInstanceService.save(instance);

    // Act
    integration.initialise();

    // Assert
    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
  }
}
