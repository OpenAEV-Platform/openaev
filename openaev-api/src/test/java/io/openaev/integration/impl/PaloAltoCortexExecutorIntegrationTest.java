package io.openaev.integration.impl;

import static io.openaev.helper.StreamHelper.fromIterable;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration;
import io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegrationFactory;
import io.openaev.service.*;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import io.openaev.utils.reflection.FieldUtils;
import io.openaev.utilstest.RabbitMQTestListener;
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
public class PaloAltoCortexExecutorIntegrationTest {
  @Autowired private PaloAltoCortexExecutorClient client;
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
  @Autowired private HttpClientFactory httpClientFactory;
  @Autowired private BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;
  @Autowired private PreviewFeatureService previewFeatureService;
  @Autowired private EncryptionFactory encryptionFactory;

  @Autowired private FileService fileService;

  private PaloAltoCortexExecutorIntegrationFactory getFactory() {
    return new PaloAltoCortexExecutorIntegrationFactory(
        connectorInstanceService,
        catalogConnectorService,
        executorService,
        componentRequestEngine,
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

  /**
   * PaloAlto has no ConfigurationMigration, so we manually create a persisted instance with the
   * default configuration set attached to the catalog connector.
   */
  private ConnectorInstancePersisted createInstanceForCatalog(CatalogConnector catalogConnector) {
    ConnectorInstancePersisted instance = new ConnectorInstancePersisted();
    instance.setCatalogConnector(catalogConnector);
    instance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    instance.setSource(ConnectorInstancePersisted.SOURCE.CATALOG_DEPLOYMENT);
    PaloAltoCortexExecutorConfig config =
        baseIntegrationConfigurationBuilder.build(PaloAltoCortexExecutorConfig.class);
    instance.setConfigurations(
        config.toInstanceConfigurationSet(
            instance, encryptionFactory.getEncryptionService(catalogConnector)));
    return (ConnectorInstancePersisted) connectorInstanceService.save(instance);
  }

  @Test
  @DisplayName("Factory is initialised correctly and creates catalog object")
  public void factoryIsInitialisedCorrectlyAndCreatesCatalogObject() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    assertThat(connectors).hasSize(1);
    AssertionsForClassTypes.assertThat(connectors.getFirst().getClassName())
        .isEqualTo(PaloAltoCortexExecutorIntegrationFactory.class.getCanonicalName());
  }

  @Test
  @DisplayName("When factory is initialised, there is a connector with correct configuration")
  public void whenFactoryIsInitialised_thereIsAConnectorWithCorrectConfiguration()
      throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    assertThat(connectors).hasSize(1);
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
    ConnectorInstancePersisted instance = createInstanceForCatalog(connectors.getFirst());

    // Act & Assert — passing null baseIntegrationConfigurationBuilder causes refresh() to fail
    assertThatThrownBy(
            () ->
                new PaloAltoCortexExecutorIntegration(
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
    ConnectorInstancePersisted instance = createInstanceForCatalog(connectors.getFirst());

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
    ConnectorInstancePersisted instance = createInstanceForCatalog(connectors.getFirst());

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
