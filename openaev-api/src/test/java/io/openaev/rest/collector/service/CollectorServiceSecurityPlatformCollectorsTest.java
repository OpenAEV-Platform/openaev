package io.openaev.rest.collector.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Collector;
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

/** Security-platform collectors used to seed expectation results come from the active query. */
@ExtendWith(MockitoExtension.class)
class CollectorServiceSecurityPlatformCollectorsTest {

  private static final String TENANT_ID = "2cffad3a-0001-4078-b0e2-ef74274022c3";

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

  @Test
  @DisplayName("Reads the active collectors with the collector id configuration key")
  void given_tenant_should_readActiveSecurityPlatformCollectors() {
    Collector active = new Collector();
    active.setId("8b2c3f6e-5d1a-4c47-9a0e-0d4f1b2a7c11");
    when(collectorRepository.findAllActiveByTenantIdAndSecurityPlatformIsNotNull(
            TENANT_ID, "COLLECTOR_ID"))
        .thenReturn(List.of(active));

    assertThat(service.securityPlatformCollectors(TENANT_ID)).containsExactly(active);
  }
}
