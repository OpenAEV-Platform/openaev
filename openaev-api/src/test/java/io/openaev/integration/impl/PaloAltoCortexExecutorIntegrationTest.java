package io.openaev.integration.impl;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration.PALOALTOCORTEX_EXECUTOR_NAME;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.paloaltocortex.client.PaloAltoCortexExecutorClient;
import io.openaev.executors.paloaltocortex.config.PaloAltoCortexExecutorConfig;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegration;
import io.openaev.integration.impl.executors.paloaltocortex.PaloAltoCortexExecutorIntegrationFactory;
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
  @Autowired private PaloAltoCortexExecutorConfig config;
  @Autowired private EncryptionFactory encryptionFactory;
  @Autowired private HttpClientFactory httpClientFactory;
  @Autowired private BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;

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
  @DisplayName("When factory syncs with stopped instance, integration is of status stopped")
  public void whenFactorySyncWithStoppedInstance_integrationIsOfStatusStopped() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    List<Integration> syncedIntegrations = integrationFactory.sync(new ArrayList<>(instances));

    assertThat(syncedIntegrations).hasSize(1);
    assertThat(syncedIntegrations).first().isInstanceOf(PaloAltoCortexExecutorIntegration.class);
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
    assertThat(syncedIntegrations).first().isInstanceOf(PaloAltoCortexExecutorIntegration.class);
    assertThat(syncedIntegrations)
        .first()
        .satisfies(
            integration ->
                assertThat(
                        integration.requestComponent(
                            new ComponentRequest(PALOALTOCORTEX_EXECUTOR_NAME),
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
                        config.toInstanceConfigurationSet(
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
}
