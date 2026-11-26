package io.openaev.integration.impl.collector;

import static io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig.COLLECTOR_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.authorisation.HttpClientFactory;
import io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig;
import io.openaev.collectors.expectations_expiration_manager.service.ExpectationsExpirationManagerService;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Tenant;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.impl.collectors.expiration_manager.ExpirationManagerIntegration;
import io.openaev.integration.impl.collectors.expiration_manager.ExpirationManagerIntegrationFactory;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.scheduler.TenantScopedJobRunner;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("LocalSecretsProviderIntegration tests")
public class ExpirationManagerIntegrationTest {

  @Autowired private ComponentRequestEngine componentRequestEngine;
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private HttpClientFactory httpClientFactory;
  @Autowired private ThreadPoolTaskScheduler threadPoolTaskScheduler;
  @Autowired private CollectorService collectorService;
  @Autowired private ExpectationsExpirationManagerService expectationsExpirationManagerService;
  @Autowired private ExpectationsExpirationManagerConfig expectationsExpirationManagerConfig;
  @Autowired private TenantScopedJobRunner tenantScopedJobRunner;

  private ExpirationManagerIntegrationFactory getFactory() {
    return new ExpirationManagerIntegrationFactory(
        connectorInstanceService,
        catalogConnectorService,
        httpClientFactory,
        threadPoolTaskScheduler,
        componentRequestEngine,
        collectorService,
        expectationsExpirationManagerService,
        expectationsExpirationManagerConfig,
        tenantScopedJobRunner);
  }

  @Nested
  @DisplayName("Integration Lifecycle")
  class IntegrationLifecycle {

    @Test
    @DisplayName("Factory initialization reports the local autostart instance")
    void given_initializedFactory_should_reportAutostartInstance() throws Exception {
      // Arrange
      IntegrationFactory factory = getFactory();
      factory.initialise(Tenant.DEFAULT_TENANT_UUID);

      // Act
      List<ConnectorInstance> instances = factory.findRelatedInstances("test-tenant");

      // Assert
      assertThat(instances).hasSize(1);
      assertThat(instances)
          .usingComparatorForType(
              Comparator.comparing(ConnectorInstance::getId), ConnectorInstance.class)
          .hasSameElementsAs(
              List.of(
                  connectorInstanceService.createAutostartInstance(
                      COLLECTOR_ID,
                      factory.getClass().getCanonicalName(),
                      ConnectorType.COLLECTOR)));
    }

    @Test
    @DisplayName("Sync starts the autostart instance as ExpirationManagerIntegration integration")
    void given_syncedAutostartInstance_should_beStartedAndInstanceOfExpirationManagerIntegration()
        throws Exception {
      // Arrange
      IntegrationFactory factory = getFactory();
      factory.initialise(Tenant.DEFAULT_TENANT_UUID);

      // Act
      List<Integration> integrations =
          factory.sync(factory.findRelatedInstances("test-tenant"), Tenant.DEFAULT_TENANT_UUID);

      // Assert
      assertThat(integrations).hasSize(1);
      assertThat(integrations.getFirst()).isInstanceOf(ExpirationManagerIntegration.class);
      assertThat(integrations.getFirst().getCurrentStatus())
          .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);
    }
  }
}
