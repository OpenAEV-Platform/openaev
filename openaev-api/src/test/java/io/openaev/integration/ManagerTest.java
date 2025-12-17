package io.openaev.integration;

import static io.openaev.helper.StreamHelper.fromIterable;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import io.openaev.integration.local_fixtures.TestIntegrationConfiguration;
import io.openaev.integration.local_fixtures.TestIntegrationFactory;
import io.openaev.rest.connector_instance.service.ConnectorInstanceService;
import io.openaev.utils.fixtures.CatalogConnectorFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
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
public class ManagerTest {
  @Autowired private TestIntegrationFactory testIntegrationFactory;
  @Autowired private CatalogConnectorRepository catalogConnectorRepository;
  @Autowired private ConnectorInstanceRepository connectorInstanceRepository;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;

  @Test
  @DisplayName(
      "When the Manager is instantiated, configured integration factories create their catalog entry.")
  public void whenInstantiatingManager_factoriesAreInitialised() {
    // ACT: instantiate the manager
    // this will trigger factories to register their catalog item where applicable
    new Manager(List.of(testIntegrationFactory));

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    assertThat(connectors).hasSize(1);
    assertThat(connectors.getFirst().getClassName())
        .isEqualTo(TestIntegrationFactory.class.getCanonicalName());
  }

  @Test
  @DisplayName(
      "When the Manager is instantiated and factory catalog entry is already create, configured integration factories DO NOT create their catalog entry.")
  public void whenInstantiatingManagerAndCatalogEntryExists_factoriesDONOTCreateCatalogEntry() {
    catalogConnectorComposer
        .forCatalogConnector(
            CatalogConnectorFixture.createCatalogConnectorWithClassName(
                TestIntegrationFactory.class.getCanonicalName()))
        .persist();

    // ACT: instantiate the manager
    // this will trigger factories to register their catalog item where applicable
    new Manager(List.of(testIntegrationFactory));

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    assertThat(connectors).hasSize(1);
    assertThat(connectors.getFirst().getClassName())
        .isEqualTo(TestIntegrationFactory.class.getCanonicalName());
  }

  @Test
  @DisplayName("When the Manager is instantiated, configured factories run their own migrations")
  public void whenInstantiatingManager_migrationsAreRun() {
    new Manager(List.of(testIntegrationFactory));

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    List<ConnectorInstancePersisted> instances =
        connectorInstanceRepository.findByCatalogConnectorId(connectors.getFirst().getId());

    assertThat(instances).hasSize(1);

    ConnectorInstance singleInstance = instances.getFirst();
    assertThat(singleInstance.getConfigurations()).hasSize(1);

    ConnectorInstanceConfiguration configItem =
        singleInstance.getConfigurations().stream().findFirst().get();
    assertThat(configItem.getConnectorInstance()).isEqualTo(singleInstance);
    assertThat(configItem.getKey()).isEqualTo("TEST_INTEGRATION_ID");
    assertThat(configItem.getValue().asText())
        .isEqualTo(TestIntegrationConfiguration.TEST_INTEGRATION_ID);
    assertThat(configItem.isEncrypted()).isFalse();
  }

  @Test
  @DisplayName(
      "When requested state of instance changes state, manager changes state of integration")
  public void whenRequestedStateOfInstanceSetToStopping_managerStopsIntegration() {
    Manager manager = new Manager(List.of(testIntegrationFactory));

    // START integrations
    manager.monitorIntegrations();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    List<ConnectorInstancePersisted> instances =
        connectorInstanceRepository.findByCatalogConnectorId(connectors.getFirst().getId());
    ConnectorInstance singleInstance = instances.getFirst();
    assertThat(singleInstance.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);
    assertThat(singleInstance.getRequestedStatus())
        .isEqualTo(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    assertThat(manager.getSpawnedIntegrations().get(singleInstance).getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);

    singleInstance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    connectorInstanceService.save(singleInstance);

    // REFRESH integrations
    manager.monitorIntegrations();

    List<ConnectorInstancePersisted> refreshedInstances =
        connectorInstanceRepository.findByCatalogConnectorId(connectors.getFirst().getId());
    ConnectorInstance refreshedInstance = refreshedInstances.getFirst();
    assertThat(refreshedInstance.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
    assertThat(refreshedInstance.getRequestedStatus())
        .isEqualTo(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);

    assertThat(manager.getSpawnedIntegrations().get(refreshedInstance).getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);

    refreshedInstance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    connectorInstanceService.save(refreshedInstance);

    // REFRESH integrations
    manager.monitorIntegrations();

    List<ConnectorInstancePersisted> refreshedAgainInstances =
        connectorInstanceRepository.findByCatalogConnectorId(connectors.getFirst().getId());
    ConnectorInstance refreshedAgainInstance = refreshedAgainInstances.getFirst();
    assertThat(refreshedAgainInstance.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);
    assertThat(refreshedAgainInstance.getRequestedStatus())
        .isEqualTo(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    assertThat(manager.getSpawnedIntegrations().get(refreshedAgainInstance).getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);
  }

  @Test
  @DisplayName("When instance is deleted, manager stops integration and deletes")
  public void whenInstanceIsDeleted_managerStopsIntegrationAndDeletes() {
    Manager manager = new Manager(List.of(testIntegrationFactory));

    // START integrations
    manager.monitorIntegrations();

    List<CatalogConnector> connectors = fromIterable(catalogConnectorRepository.findAll());

    List<ConnectorInstancePersisted> instances =
        connectorInstanceRepository.findByCatalogConnectorId(connectors.getFirst().getId());
    ConnectorInstance singleInstance = instances.getFirst();
    assertThat(singleInstance.getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);
    assertThat(singleInstance.getRequestedStatus())
        .isEqualTo(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    assertThat(manager.getSpawnedIntegrations().get(singleInstance).getCurrentStatus())
        .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);

    connectorInstanceService.deleteById(singleInstance.getId());

    // REFRESH integrations
    manager.monitorIntegrations();

    List<ConnectorInstancePersisted> refreshedInstances =
        connectorInstanceRepository.findByCatalogConnectorId(connectors.getFirst().getId());

    assertThat(refreshedInstances).isEmpty();
    assertThat(manager.getSpawnedIntegrations()).isEmpty();
  }
}
