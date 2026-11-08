package io.openaev.integration.impl.executor;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.integration.impl.executors.caldera.CalderaExecutorIntegration.CALDERA_EXECUTOR_NAME;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorContextService;
import io.openaev.executors.ExecutorService;
import io.openaev.executors.caldera.client.CalderaExecutorClient;
import io.openaev.executors.caldera.config.CalderaExecutorConfig;
import io.openaev.executors.exception.ExecutorException;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.configuration.BaseIntegrationConfigurationBuilder;
import io.openaev.integration.impl.executors.caldera.CalderaExecutorIntegration;
import io.openaev.integration.impl.executors.caldera.CalderaExecutorIntegrationFactory;
import io.openaev.integration.migration.CalderaExecutorConfigurationMigration;
import io.openaev.service.*;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.EncryptionFactory;
import io.openaev.utils.mockConfig.executors.WithMockCalderaConfig;
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
// The legacy properties migration only seeds an instance when the legacy config is enabled;
// these tests exercise the factory around that migrated instance.
@WithMockCalderaConfig(
    enable = true,
    url = "caldera_url",
    publicUrl = "caldera_public_url",
    apiKey = "caldera_api_key")
public class CalderaExecutorIntegrationTest {
  @Autowired private CalderaExecutorClient client;
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

  @Autowired
  private io.openaev.database.repository.ConnectorInstanceRepository connectorInstanceRepository;

  @Autowired private CalderaExecutorConfig calderaExecutorConfig;
  @Autowired private EncryptionFactory encryptionFactory;
  @Autowired private BaseIntegrationConfigurationBuilder baseIntegrationConfigurationBuilder;
  @Autowired private HttpClientFactory httpClientFactory;

  @Autowired private CalderaExecutorConfigurationMigration calderaExecutorConfigurationMigration;

  @Autowired private FileService fileService;
  @Autowired private InjectorService injectorService;
  @Autowired private PlatformSettingsService platformSettingsService;
  @Autowired private TenantScopedTransaction tenantTx;

  private CalderaExecutorIntegrationFactory getFactory() {
    return getFactory(tenantTx);
  }

  private CalderaExecutorIntegrationFactory getFactory(TenantScopedTransaction transactions) {
    return new CalderaExecutorIntegrationFactory(
        connectorInstanceService,
        catalogConnectorService,
        executorService,
        componentRequestEngine,
        calderaExecutorConfigurationMigration,
        agentService,
        endpointService,
        injectorService,
        platformSettingsService,
        taskScheduler,
        fileService,
        baseIntegrationConfigurationBuilder,
        httpClientFactory,
        transactions);
  }

  @Test
  @DisplayName("Factory is initialised correctly and creates catalog object")
  public void factoryIsInitialisedCorrectlyAndCreatesCatalogObject() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise(Tenant.DEFAULT_TENANT_UUID);

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    assertThat(connectors).hasSize(1);
    AssertionsForClassTypes.assertThat(connectors.getFirst().getClassName())
        .isEqualTo(CalderaExecutorIntegrationFactory.class.getCanonicalName());
  }

  @Test
  @DisplayName("When factory syncs with stopped instance, integration is of status stopped")
  public void whenFactorySyncWithStoppedInstance_integrationIsOfStatusStopped() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise(Tenant.DEFAULT_TENANT_UUID);

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    // The migrated instance requests 'starting' (legacy config enabled): request a stop
    // so the sync exercises the stopped path instead of attempting a real start.
    instances.getFirst().setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    connectorInstanceService.save(instances.getFirst());
    List<Integration> syncedIntegrations = integrationFactory.sync(new ArrayList<>(instances));

    assertThat(syncedIntegrations).hasSize(1);
    assertThat(syncedIntegrations).first().isInstanceOf(CalderaExecutorIntegration.class);
    assertThat(syncedIntegrations)
        .first()
        .satisfies(
            integration ->
                assertThat(integration.getCurrentStatus())
                    .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped));
  }

  @Test
  @DisplayName(
      "When the instance has disappeared, stopping the integration clears the banner inside the"
          + " caller's transaction")
  public void given_instanceGone_should_stopWithoutRefusingThePlatformWrite() throws Exception {
    // Arrange: a synced integration whose instance is then deleted
    IntegrationFactory integrationFactory = getFactory();
    integrationFactory.initialise(Tenant.DEFAULT_TENANT_UUID);
    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    instances.getFirst().setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    connectorInstanceService.save(instances.getFirst());
    Integration integration = integrationFactory.sync(new ArrayList<>(instances)).getFirst();
    connectorInstanceRepository.deleteById(instances.getFirst().getId());

    // Act + Assert: Integration.initialise() stops on a missing instance, in the transaction the
    // monitoring job already opened; the banner write opens its own platform-write transaction
    // there instead of refusing to nest
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(integration::initialise);
  }

  @Test
  @DisplayName(
      "When the instance has disappeared and the banner write fails, stopping the integration"
          + " still succeeds, and the write is not made on the caller's connection")
  public void given_bannerWriteFails_should_stillStopTheIntegration() throws Exception {
    // Arrange: the same integration, with a transaction primitive that refuses every write
    TenantScopedTransaction refusing = mock(TenantScopedTransaction.class);
    doThrow(new IllegalStateException("write refused"))
        .when(refusing)
        .executeAsPlatformWrite(any(TxCtx.class), any(Runnable.class));
    IntegrationFactory integrationFactory = getFactory(refusing);
    integrationFactory.initialise(Tenant.DEFAULT_TENANT_UUID);
    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    instances.getFirst().setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    connectorInstanceService.save(instances.getFirst());
    Integration integration = integrationFactory.sync(new ArrayList<>(instances)).getFirst();
    connectorInstanceRepository.deleteById(instances.getFirst().getId());

    // Act: this test holds a transaction, as the monitoring job does
    org.junit.jupiter.api.Assertions.assertDoesNotThrow(integration::initialise);

    // Assert: the write is attempted on the scheduler, in a transaction of its own, never nested
    // in the caller's
    verify(refusing, timeout(10_000)).executeAsPlatformWrite(any(TxCtx.class), any(Runnable.class));
    verify(refusing, never()).executeNewAsPlatformWrite(any(TxCtx.class), any(Runnable.class));
    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
  }

  @Test
  @DisplayName("When factory syncs with stopped instance, integration has no component of type")
  public void whenFactorySyncWithStoppedInstance_stoppedIntegrationHasNoComponentOfType()
      throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise(Tenant.DEFAULT_TENANT_UUID);

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    // The migrated instance requests 'starting' (legacy config enabled): request a stop
    // so the sync exercises the stopped path instead of attempting a real start.
    instances.getFirst().setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    connectorInstanceService.save(instances.getFirst());
    List<Integration> syncedIntegrations = integrationFactory.sync(new ArrayList<>(instances));

    assertThat(syncedIntegrations).hasSize(1);
    assertThat(syncedIntegrations).first().isInstanceOf(CalderaExecutorIntegration.class);
    assertThat(syncedIntegrations)
        .first()
        .satisfies(
            integration ->
                assertThat(
                        integration.requestComponent(
                            new ComponentRequest(CALDERA_EXECUTOR_NAME),
                            ExecutorContextService.class))
                    .isEmpty());
  }

  @Test
  @DisplayName("When factory is initialised, there is an instance with correct configuration")
  public void whenFactoryIsInitialised_thereIsAnInstanceWithCorrectConfiguration()
      throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise(Tenant.DEFAULT_TENANT_UUID);

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
                        calderaExecutorConfig.toInstanceConfigurationSet(
                            instance,
                            encryptionFactory.getEncryptionService(
                                instance.getCatalogConnector()))));
  }

  @Test
  @DisplayName(
      "When factory is initialised and an instance is spawned with an unsupported connector instance type, the encryption service is null")
  public void whenInstanceIsSpawn_encryptionServiceIsNull() throws Exception {
    IntegrationFactory integrationFactory = getFactory();

    integrationFactory.initialise(Tenant.DEFAULT_TENANT_UUID);

    Integration integration = integrationFactory.spawn(new ConnectorInstanceInMemory());
    assertThat(FieldUtils.computeAllFieldValues(integration).get("encryptionService")).isNull();
  }

  @Test
  @DisplayName(
      "When spawning an integration with a null configuration builder, should throw ExecutorException")
  public void whenSpawnWithNullConfigBuilder_should_throwExecutorException() throws Exception {
    IntegrationFactory integrationFactory = getFactory();
    integrationFactory.initialise(Tenant.DEFAULT_TENANT_UUID);

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    List<ConnectorInstancePersisted> instances =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst());
    ConnectorInstancePersisted instance = instances.getFirst();

    // Act & Assert — passing null baseIntegrationConfigurationBuilder causes refresh() to fail
    assertThatThrownBy(
            () ->
                new CalderaExecutorIntegration(
                    instance,
                    connectorInstanceService,
                    endpointService,
                    agentService,
                    executorService,
                    componentRequestEngine,
                    platformSettingsService,
                    injectorService,
                    taskScheduler,
                    null,
                    httpClientFactory,
                    tenantTx))
        .isInstanceOf(ExecutorException.class)
        .hasMessageContaining("Error during initialization of the Executor");
  }

  @Test
  @DisplayName(
      "When integration is stopped and requested status is starting but innerStart fails, initialise should throw and status should remain stopped")
  public void whenStoppedAndStartingRequested_innerStartFails_should_remainStopped()
      throws Exception {
    // Arrange
    IntegrationFactory integrationFactory = getFactory();
    integrationFactory.initialise(Tenant.DEFAULT_TENANT_UUID);

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    ConnectorInstancePersisted instance =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst()).getFirst();

    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    connectorInstanceService.save(instance);

    Integration integration = integrationFactory.spawn(instance);
    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);

    // Act & Assert — innerStart() fails because Caldera server is not available
    assertThatThrownBy(integration::initialise).isInstanceOf(RuntimeException.class);

    // Status should remain stopped since start() did not complete
    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
  }

  @Test
  @DisplayName(
      "When integration failed to start and stopping is requested, initialise should be a no-op (already stopped)")
  public void whenFailedStartAndStoppingRequested_initialise_should_remainStopped()
      throws Exception {
    // Arrange — attempt to start but it fails
    IntegrationFactory integrationFactory = getFactory();
    integrationFactory.initialise(Tenant.DEFAULT_TENANT_UUID);

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());
    ConnectorInstancePersisted instance =
        connectorInstanceService.findAllByCatalogConnector(connectors.getFirst()).getFirst();

    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    connectorInstanceService.save(instance);

    Integration integration = integrationFactory.spawn(instance);
    try {
      integration.initialise();
    } catch (Exception ignored) {
      // Expected: innerStart fails because no Caldera server
    }
    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);

    // Arrange — now request stopping (already stopped)
    instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    connectorInstanceService.save(instance);

    // Act — should not throw, the stop on already-stopped is a no-op
    integration.initialise();

    // Assert
    assertThat(integration.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
  }
}
