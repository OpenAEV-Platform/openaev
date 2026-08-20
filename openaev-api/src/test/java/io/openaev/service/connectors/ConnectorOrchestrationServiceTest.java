package io.openaev.service.connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.Collector;
import io.openaev.database.model.ConnectorType;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.executors.ExecutorService;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.service.InjectorService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceLogService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class ConnectorOrchestrationServiceTest {

  private static final String TENANT_ID = "2cffad3a-0001-4078-b0e2-ef74274022c3";
  private static final String COLLECTOR_ID = "c4b850fa-6893-4b0b-bafd-ac571e502590";

  @Mock private ConnectorInstanceService connectorInstanceService;
  @Mock private XtmComposerService xtmComposerService;
  @Mock private EnterpriseEditionService enterpriseEditionService;
  @Mock private CatalogConnectorService catalogConnectorService;
  @Mock private CollectorService collectorService;
  @Mock private InjectorService injectorService;
  @Mock private ExecutorService executorService;
  @Mock private ConnectorInstanceLogService connectorInstanceLogService;
  @Mock private LicenseCacheManager licenseCacheManager;

  private ConnectorOrchestrationService service;

  @BeforeEach
  void setUp() {
    service =
        new ConnectorOrchestrationService(
            connectorInstanceService,
            xtmComposerService,
            enterpriseEditionService,
            catalogConnectorService,
            collectorService,
            injectorService,
            executorService,
            connectorInstanceLogService,
            licenseCacheManager);
    when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);
  }

  private ConnectorOrchestrationService.CatalogConnectorWithConfigMap collectorCatalog() {
    CatalogConnector catalogConnector = new CatalogConnector();
    catalogConnector.setContainerType(ConnectorType.COLLECTOR);
    catalogConnector.setManagerSupported(false);
    return new ConnectorOrchestrationService.CatalogConnectorWithConfigMap(
        catalogConnector, Map.of());
  }

  private CreateConnectorInstanceInput migrationInput(String collectorId) {
    CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
    CreateConnectorInstanceInput.ConfigurationInput idConfig =
        new CreateConnectorInstanceInput.ConfigurationInput();
    idConfig.setKey("COLLECTOR_ID");
    idConfig.setValue(new TextNode(collectorId));
    input.setConfigurations(List.of(idConfig));
    return input;
  }

  @Test
  @DisplayName(
      "Migrating with a collector id unknown in the current tenant fails with a 400 carrying the"
          + " real reason, not a 409 that the UI renders as 'already exists'")
  void given_migrationWithUnknownCollectorId_should_failWithBadRequest() {
    when(collectorService.collector(COLLECTOR_ID))
        .thenThrow(new ElementNotFoundException("Collector not found with id: " + COLLECTOR_ID));

    assertThatThrownBy(
            () ->
                service.createConnectorInstance(
                    collectorCatalog(), migrationInput(COLLECTOR_ID), TENANT_ID))
        .isInstanceOf(BadRequestException.class)
        .isNotInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining(COLLECTOR_ID)
        .hasMessageContaining("visible in the current tenant");
  }

  @Test
  @DisplayName(
      "Migrating with a collector id resolvable in the current tenant creates the instance")
  void given_migrationWithExistingCollectorId_should_createInstance() {
    when(collectorService.collector(COLLECTOR_ID)).thenReturn(new Collector());

    CreateConnectorInstanceInput input = migrationInput(COLLECTOR_ID);
    ConnectorOrchestrationService.CatalogConnectorWithConfigMap catalog = collectorCatalog();
    service.createConnectorInstance(catalog, input, TENANT_ID);

    verify(connectorInstanceService).createConnectorInstance(catalog, input, TENANT_ID);
  }

  @Test
  @DisplayName(
      "Migrating with a JSON null connector id fails with a 400 asking for an id, not a 500")
  void given_migrationWithNullConnectorId_should_failWithBadRequest() {
    CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
    CreateConnectorInstanceInput.ConfigurationInput idConfig =
        new CreateConnectorInstanceInput.ConfigurationInput();
    idConfig.setKey("COLLECTOR_ID");
    idConfig.setValue(NullNode.getInstance());
    input.setConfigurations(List.of(idConfig));

    assertThatThrownBy(() -> service.createConnectorInstance(collectorCatalog(), input, TENANT_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("A connector id is required");
  }

  @Test
  @DisplayName(
      "Migrating with an absent (Java null) connector id value fails with a 400 asking for an id,"
          + " not an NPE")
  void given_migrationWithAbsentConnectorIdValue_should_failWithBadRequest() {
    CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
    CreateConnectorInstanceInput.ConfigurationInput idConfig =
        new CreateConnectorInstanceInput.ConfigurationInput();
    idConfig.setKey("COLLECTOR_ID");
    idConfig.setValue(null);
    input.setConfigurations(List.of(idConfig));

    assertThatThrownBy(() -> service.createConnectorInstance(collectorCatalog(), input, TENANT_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("A connector id is required");
  }

  @Test
  @DisplayName("A plain create (no connector id in input) skips the migration existence check")
  void given_plainCreateWithoutConnectorId_should_skipMigrationCheck() {
    CreateConnectorInstanceInput input = new CreateConnectorInstanceInput();
    ConnectorOrchestrationService.CatalogConnectorWithConfigMap catalog = collectorCatalog();

    service.createConnectorInstance(catalog, input, TENANT_ID);

    verify(connectorInstanceService).createConnectorInstance(catalog, input, TENANT_ID);
    assertThat(input.getConfigurations()).isEmpty();
  }
}
