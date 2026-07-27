package io.openaev.integration.impl.secret;

import static io.openaev.integration.impl.secrets.local.LocalSecretsProviderIntegration.LOCAL_SECRETS_PROVIDER_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.repository.SecretReferenceRepository;
import io.openaev.database.repository.SecretsRepository;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.ComponentRequestEngine;
import io.openaev.integration.Integration;
import io.openaev.integration.IntegrationFactory;
import io.openaev.integration.impl.secrets.local.LocalSecretsProviderIntegration;
import io.openaev.integration.impl.secrets.local.LocalSecretsProviderIntegrationFactory;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.secrets.provider.impl.LocalSecretsProvider;
import io.openaev.service.catalog_connectors.CatalogConnectorService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connector_instances.NativeEncryptionService;
import io.openaev.utilstest.RabbitMQTestListener;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
@DisplayName("LocalSecretsProviderIntegration tests")
public class LocalSecretsProviderIntegrationTest {

  @Autowired private ComponentRequestEngine componentRequestEngine;
  @Autowired private CatalogConnectorService catalogConnectorService;
  @Autowired private ConnectorInstanceService connectorInstanceService;
  @Autowired private NativeEncryptionService nativeEncryptionService;
  @Autowired private SecretsRepository secretsRepository;
  @Autowired private SecretReferenceRepository secretReferenceRepository;

  private LocalSecretsProviderIntegrationFactory getFactory() {
    return new LocalSecretsProviderIntegrationFactory(
        connectorInstanceService,
        catalogConnectorService,
        componentRequestEngine,
        null,
        nativeEncryptionService,
        secretsRepository,
        secretReferenceRepository);
  }

  @Nested
  @DisplayName("Integration Lifecycle")
  class IntegrationLifecycle {

    @Test
    @DisplayName("Factory initialization reports the local autostart instance")
    void given_initializedFactory_should_reportAutostartInstance() throws Exception {
      // Arrange
      IntegrationFactory factory = getFactory();
      factory.initialise();

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
                      LOCAL_SECRETS_PROVIDER_ID,
                      factory.getClass().getCanonicalName(),
                      ConnectorType.SECRETS_PROVIDER)));
    }

    @Test
    @DisplayName("Sync starts the autostart instance as LocalSecretsProvider integration")
    void given_syncedAutostartInstance_should_beStartedAndInstanceOfLocalSecretsProvider()
        throws Exception {
      // Arrange
      IntegrationFactory factory = getFactory();
      factory.initialise();

      // Act
      List<Integration> integrations = factory.sync(factory.findRelatedInstances("test-tenant"));

      // Assert
      assertThat(integrations).hasSize(1);
      assertThat(integrations.getFirst()).isInstanceOf(LocalSecretsProviderIntegration.class);
      assertThat(integrations.getFirst().getCurrentStatus())
          .isEqualTo(ConnectorInstance.CURRENT_STATUS_TYPE.started);
    }

    @Test
    @DisplayName("Started integration exposes the LocalSecretsProvider component")
    void given_startedIntegration_should_exposeLocalSecretsProviderComponent() throws Exception {
      // Arrange
      IntegrationFactory factory = getFactory();
      factory.initialise();
      List<Integration> integrations = factory.sync(factory.findRelatedInstances("test-tenant"));
      Integration integration = integrations.getFirst();

      // Act
      List<SecretsProvider> providers =
          integration.requestComponent(
              new ComponentRequest("secrets-provider"), SecretsProvider.class);

      // Assert
      assertThat(providers).hasSize(1);
      assertThat(providers.getFirst()).isInstanceOf(LocalSecretsProvider.class);
      assertThat(providers.getFirst().getId()).isEqualTo(LOCAL_SECRETS_PROVIDER_ID);
    }
  }
}
