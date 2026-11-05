package io.openaev.rest.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Collector;
import io.openaev.database.model.SecurityPlatform;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.CollectorTypeRepository;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.SecurityPlatformRepository;
import io.openaev.service.FileService;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utils.mapper.CatalogConnectorMapper;
import io.openaev.utils.mapper.CollectorMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The security-platform collectors used to seed inject expectation results leave out the collectors
 * whose owning connector instance is paused (issue #4830): a paused collector never reports, so a
 * pending result seeded for it holds the expectation until it expires.
 */
@ExtendWith(MockitoExtension.class)
class CollectorServiceSecurityPlatformCollectorsTest {

  private static final String TENANT_ID = "2cffad3a-0001-4078-b0e2-ef74274022c3";
  private static final String ACTIVE_ID = "8b2c3f6e-5d1a-4c47-9a0e-0d4f1b2a7c11";
  private static final String PAUSED_ID = "c9e1a4d2-7f3b-4e58-8b16-3a5d9c0e2f44";
  private static final String UNMANAGED_ID = "1f7d2b9a-3c64-4a85-b0e7-6e2c8d4f9a33";

  @Mock private CollectorRepository collectorRepository;
  @Mock private CollectorTypeRepository collectorTypeRepository;
  @Mock private ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;
  @Mock private SecurityPlatformRepository securityPlatformRepository;
  @Mock private FileService fileService;
  @Mock private ConnectorInstanceService connectorInstanceService;
  @Mock private CatalogConnectorService catalogConnectorService;
  @Mock private CollectorMapper collectorMapper;
  @Mock private CatalogConnectorMapper catalogConnectorMapper;

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

  private static Collector securityPlatformCollector(String id) {
    Collector collector = new Collector();
    collector.setId(id);
    collector.setTenantId(TENANT_ID);
    collector.setName("collector " + id);
    collector.setSecurityPlatform(new SecurityPlatform());
    return collector;
  }

  @Test
  @DisplayName("A collector whose connector instance is paused is left out")
  void given_pausedConnectorInstance_should_excludeItsCollector() {
    Collector active = securityPlatformCollector(ACTIVE_ID);
    Collector paused = securityPlatformCollector(PAUSED_ID);
    Collector unmanaged = securityPlatformCollector(UNMANAGED_ID);
    when(collectorRepository.findAllByTenantIdAndSecurityPlatformIsNotNull(TENANT_ID))
        .thenReturn(List.of(active, paused, unmanaged));
    when(connectorInstanceConfigurationRepository.findConnectorIdsOfPausedInstancesByKeyAndTenantId(
            "COLLECTOR_ID", TENANT_ID))
        .thenReturn(List.of(PAUSED_ID));

    List<Collector> collectors = service.securityPlatformCollectors(TENANT_ID);

    assertThat(collectors).containsExactly(active, unmanaged);
  }

  @Test
  @DisplayName("Every collector is kept when no connector instance is paused")
  void given_noPausedConnectorInstance_should_keepEveryCollector() {
    Collector active = securityPlatformCollector(ACTIVE_ID);
    Collector unmanaged = securityPlatformCollector(UNMANAGED_ID);
    when(collectorRepository.findAllByTenantIdAndSecurityPlatformIsNotNull(TENANT_ID))
        .thenReturn(List.of(active, unmanaged));
    when(connectorInstanceConfigurationRepository.findConnectorIdsOfPausedInstancesByKeyAndTenantId(
            "COLLECTOR_ID", TENANT_ID))
        .thenReturn(List.of());

    List<Collector> collectors = service.securityPlatformCollectors(TENANT_ID);

    assertThat(collectors).containsExactly(active, unmanaged);
  }

  @Test
  @DisplayName("No connector instance is read when the tenant has no security-platform collector")
  void given_noSecurityPlatformCollector_should_notReadConnectorInstances() {
    when(collectorRepository.findAllByTenantIdAndSecurityPlatformIsNotNull(TENANT_ID))
        .thenReturn(List.of());

    assertThat(service.securityPlatformCollectors(TENANT_ID)).isEmpty();
    verify(connectorInstanceConfigurationRepository, never())
        .findConnectorIdsOfPausedInstancesByKeyAndTenantId(anyString(), any());
  }
}
