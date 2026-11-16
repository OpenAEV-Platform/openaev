package io.openaev.database.repository;

import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Collector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.model.ConnectorType;
import io.openaev.utils.fixtures.CollectorFixture;
import io.openaev.utils.fixtures.SecurityPlatformFixture;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.CollectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.fixtures.composers.SecurityPlatformComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("CollectorRepository integration tests")
class CollectorRepositoryTest extends IntegrationTest {

  @Autowired private CollectorRepository collectorRepository;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private SecurityPlatformComposer securityPlatformComposer;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

  private Collector securityPlatformCollector(String name) {
    return collectorComposer
        .forCollector(CollectorFixture.createDefaultCollector(name))
        .withSecurityPlatform(
            securityPlatformComposer.forSecurityPlatform(
                SecurityPlatformFixture.createDefault(name, "EDR")))
        .persist()
        .get();
  }

  private void collectorInstance(
      Collector collector,
      ConnectorInstance.CURRENT_STATUS_TYPE currentStatus,
      ConnectorInstance.REQUESTED_STATUS_TYPE requestedStatus)
      throws JsonProcessingException {
    ConnectorInstancePersisted instance = createDefaultConnectorInstance();
    instance.setCurrentStatus(currentStatus);
    instance.setRequestedStatus(requestedStatus);
    connectorInstanceComposer
        .forConnectorInstance(instance)
        .withCatalogConnector(
            catalogConnectorComposer.forCatalogConnector(
                createDefaultCatalogConnectorManagedByXtmComposer(collector.getName())))
        .withConnectorInstanceConfiguration(
            connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                createConnectorInstanceConfiguration(
                    ConnectorType.COLLECTOR.getIdKeyName(), collector.getId())))
        .persist();
  }

  @Test
  @DisplayName("Active collectors leave out those of a stopped or stopping connector instance")
  void given_pausedConnectorInstances_should_excludeTheirCollectors() throws Exception {
    Collector running = securityPlatformCollector("collector-repo-running");
    Collector stopped = securityPlatformCollector("collector-repo-stopped");
    Collector stopping = securityPlatformCollector("collector-repo-stopping");
    Collector unmanaged = securityPlatformCollector("collector-repo-unmanaged");
    collectorInstance(
        running,
        ConnectorInstance.CURRENT_STATUS_TYPE.started,
        ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    collectorInstance(
        stopped,
        ConnectorInstance.CURRENT_STATUS_TYPE.stopped,
        ConnectorInstance.REQUESTED_STATUS_TYPE.starting);
    collectorInstance(
        stopping,
        ConnectorInstance.CURRENT_STATUS_TYPE.started,
        ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
    entityManager.flush();
    entityManager.clear();

    String tenantId = running.getTenantId();
    List<Collector> active =
        collectorRepository.findAllActiveByTenantIdAndSecurityPlatformIsNotNull(
            tenantId, ConnectorType.COLLECTOR.getIdKeyName());

    assertThat(active)
        .extracting(Collector::getId)
        .contains(running.getId(), unmanaged.getId())
        .doesNotContain(stopped.getId(), stopping.getId());
  }
}
