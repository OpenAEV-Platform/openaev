package io.openaev.rest.collector.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Collector;
import io.openaev.database.model.ConnectorCompositeId;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.CollectorMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A started collector can never be deleted (OpenCTI parity): an unmanaged one must have stopped
 * pinging, a managed one must have a stop requested or effective on its owning instance.
 */
@ExtendWith(MockitoExtension.class)
class CollectorServiceDeleteTest {

  private static final String COLLECTOR_ID = "76110e06-12b5-4132-baf4-56b2c2b5771a";
  private static final String TENANT_ID = "2cffad3a-0001-4078-b0e2-ef74274022c3";
  private static final String INSTANCE_ID = "55220888-d568-4c3b-a581-52e3851d21b7";

  @Mock private CollectorRepository collectorRepository;
  @Mock private CollectorTypeRepository collectorTypeRepository;
  @Mock private ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;
  @Mock private SecurityPlatformRepository securityPlatformRepository;
  @Mock private FileService fileService;
  @Mock private ConnectorInstanceService connectorInstanceService;
  @Mock private CatalogConnectorService catalogConnectorService;
  @Mock private CollectorMapper collectorMapper;
  @Mock private CatalogConnectorMapper catalogConnectorMapper;

  @Mock private ConnectorInstanceConfigurationRepository.ConnectorIdsFromDatabase owningInstanceIds;

  private CollectorService service;

  @BeforeEach
  void setUp() {
    service =
        new CollectorService(
            collectorRepository,
            collectorTypeRepository,
            connectorInstanceConfigurationRepository,
            securityPlatformRepository,
            fileService,
            connectorInstanceService,
            catalogConnectorService,
            collectorMapper,
            catalogConnectorMapper);
  }

  private Collector collector(Instant heartbeat) {
    Collector collector = new Collector();
    collector.setId(COLLECTOR_ID);
    collector.setTenantId(TENANT_ID);
    collector.setName("Vault Demo Collector");
    collector.setExternal(true);
    collector.setUpdatedAt(heartbeat);
    when(collectorRepository.findById(ConnectorCompositeId.of(COLLECTOR_ID, TENANT_ID)))
        .thenReturn(Optional.of(collector));
    return collector;
  }

  @Test
  @DisplayName("An unmanaged collector with a fresh heartbeat cannot be deleted")
  void given_unmanagedCollectorWithFreshHeartbeat_should_rejectDeletion() {
    collector(Instant.now());
    when(connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValueAndTenantId(
            "COLLECTOR_ID", COLLECTOR_ID, TENANT_ID))
        .thenReturn(null);

    assertThatThrownBy(() -> service.deleteCollector(COLLECTOR_ID, TENANT_ID))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("stop it before deleting it");
    verify(collectorRepository, never()).deleteByCollectorId(any());
  }

  @Test
  @DisplayName("An unmanaged collector that stopped pinging can be deleted")
  void given_unmanagedCollectorWithStaleHeartbeat_should_allowDeletion() {
    Collector stale = collector(Instant.now().minus(Duration.ofMinutes(10)));
    when(connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValueAndTenantId(
            "COLLECTOR_ID", COLLECTOR_ID, TENANT_ID))
        .thenReturn(null);
    // deleteOwningConnectorInstance re-resolves the connector before looking for an owner.
    when(collectorRepository.findByCollectorId(COLLECTOR_ID)).thenReturn(Optional.of(stale));

    assertThatCode(() -> service.deleteCollector(COLLECTOR_ID, TENANT_ID))
        .doesNotThrowAnyException();
    verify(collectorRepository).deleteByCollectorId(COLLECTOR_ID);
  }

  @Test
  @DisplayName("A managed collector whose instance is started cannot be deleted")
  void given_managedCollectorWithStartedInstance_should_rejectDeletion() {
    collector(Instant.now());
    when(owningInstanceIds.getConnectorInstanceId()).thenReturn(INSTANCE_ID);
    when(connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValueAndTenantId(
            "COLLECTOR_ID", COLLECTOR_ID, TENANT_ID))
        .thenReturn(owningInstanceIds);
    doThrow(new BadRequestException("The connector instance is started: stop it before deleting"))
        .when(connectorInstanceService)
        .throwIfInstanceRunning(INSTANCE_ID);

    assertThatThrownBy(() -> service.deleteCollector(COLLECTOR_ID, TENANT_ID))
        .isInstanceOf(BadRequestException.class);
    verify(collectorRepository, never()).deleteByCollectorId(any());
  }

  @Test
  @DisplayName("A managed collector whose instance has a stop requested is deleted via its owner")
  void given_managedCollectorWithStopRequestedInstance_should_deleteThroughItsInstance()
      throws Exception {
    Collector managed = collector(Instant.now());
    when(owningInstanceIds.getConnectorInstanceId()).thenReturn(INSTANCE_ID);
    when(connectorInstanceConfigurationRepository.findInstanceAndCatalogIdsByKeyValueAndTenantId(
            "COLLECTOR_ID", COLLECTOR_ID, TENANT_ID))
        .thenReturn(owningInstanceIds);
    when(collectorRepository.findByCollectorId(COLLECTOR_ID)).thenReturn(Optional.of(managed));

    assertThatCode(() -> service.deleteCollector(COLLECTOR_ID, TENANT_ID))
        .doesNotThrowAnyException();
    // The instance delete removes the collector row too: no direct row delete.
    verify(connectorInstanceService).deleteById(INSTANCE_ID);
    verify(collectorRepository, never()).deleteByCollectorId(any());
  }
}
