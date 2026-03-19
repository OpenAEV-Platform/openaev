package io.openaev.integration.impl;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.integration.impl.executors.openaev.OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_NAME;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.AssetAgentJobRepository;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.ee.Ee;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.openaev.service.OpenAEVExecutorContextService;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.impl.executors.openaev.OpenAEVExecutorIntegration;
import io.openaev.integration.impl.executors.openaev.OpenAEVExecutorIntegrationFactory;
import io.openaev.service.*;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.Comparator;
import java.util.List;
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
public class OpenAEVExecutorIntegrationTest {
  @Autowired private EndpointService endpointService;
  @Autowired private AgentService agentService;
  @Autowired private AssetGroupService assetGroupService;
  @Autowired private ExecutorService executorService;
  @Autowired private Ee eeService;
  @Autowired private LicenseCacheManager licenseCacheManager;
  @Autowired private ComponentRequestEngine componentRequestEngine;
  @Autowired private ThreadPoolTaskScheduler taskScheduler;
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private CatalogConnectorRepository catalogConnectorRepository;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private AssetAgentJobRepository assetAgentJobRepository;
  @Autowired private HttpClientFactory httpClientFactory;

  @Autowired private FileService fileService;
  @Autowired private InjectorService injectorService;
  @Autowired private PlatformSettingsService platformSettingsService;

  private OpenAEVExecutorIntegrationFactory getFactory() {
    return new OpenAEVExecutorIntegrationFactory(
        connectorInstanceService,
        catalogConnectorService,
        executorService,
        componentRequestEngine,
        assetAgentJobRepository,
        httpClientFactory);
  }

  @Test
  @DisplayName("Factory is initialised correctly and DOES NOT create a catalog entry")
  public void factoryIsInitialisedCorrectlyAndDoesNotCreateACatalogEntry() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    assertThat(connectors).isEmpty();
  }

  @Test
  @DisplayName("When the factory is initialised, it reports a static autostart instance")
  public void whenTheFactoryIsInitialised_itReportsAStaticAutostartInstance() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<ConnectorInstance> instances = integrationFactory.findRelatedInstances();

    assertThat(instances)
        .usingComparatorForType(
            Comparator.comparing(ConnectorInstance::getId), ConnectorInstance.class)
        .hasSameElementsAs(
            List.of(
                connectorInstanceService.createAutostartInstance(
                    OpenAEVExecutorIntegration.OPENAEV_EXECUTOR_ID,
                    integrationFactory.getClass().getCanonicalName(),
                    ConnectorType.EXECUTOR)));
  }

  @Test
  @DisplayName("When factory syncs with autostart instance, integration is of status started")
  public void whenFactorySyncWithAutostartInstance_integrationIsOfStatusStarted() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<Integration> syncedIntegrations =
        integrationFactory.sync(integrationFactory.findRelatedInstances());

    assertThat(syncedIntegrations).first().isInstanceOf(OpenAEVExecutorIntegration.class);
    assertThat(syncedIntegrations)
        .first()
        .satisfies(
            integration ->
                assertThat(integration.getCurrentStatus())
                    .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started));
  }

  @Test
  @DisplayName("When factory syncs with started instance, integration has component of type")
  public void whenFactorySyncWithStartedInstance_stoppedIntegrationHasComponentOfType()
      throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<Integration> syncedIntegrations =
        integrationFactory.sync(integrationFactory.findRelatedInstances());

    assertThat(syncedIntegrations).hasSize(1);
    assertThat(syncedIntegrations).first().isInstanceOf(OpenAEVExecutorIntegration.class);
    assertThat(syncedIntegrations)
        .first()
        .satisfies(
            integration ->
                assertThat(
                        integration.requestComponent(
                            new ComponentRequest(OPENAEV_EXECUTOR_NAME),
                            ExecutorContextService.class))
                    .first()
                    .isInstanceOf(OpenAEVExecutorContextService.class));
  }

  @Test
  @DisplayName("When factory is initialised, there is an instance with correct configuration")
  public void whenFactoryIsInitialised_thereIsAnInstanceWithCorrectConfiguration()
      throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise();

    List<ConnectorInstance> instances = integrationFactory.findRelatedInstances();

    assertThat(instances)
        .first()
        .satisfies(
            instance ->
                assertThat(instance.getConfigurations())
                    .first()
                    .extracting(ConnectorInstanceConfiguration::getKey)
                    .isEqualTo(ConnectorType.EXECUTOR.getIdKeyName()));
  }
}
