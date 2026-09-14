package io.openaev.secrets.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.integration.Manager;
import io.openaev.integration.ManagerFactory;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("SecretsProviderResolver tests")
class SecretsProviderResolverTest {

  private static final String TENANT_ID = "tenant-id";
  private static final String CONNECTOR_INSTANCE_ID = "connector-instance-id";

  private ManagerFactory managerFactory;
  private SecretsProviderResolver resolver;

  @BeforeEach
  void setUp() {
    managerFactory = mock(ManagerFactory.class);
    resolver = new SecretsProviderResolver(managerFactory);
  }

  @Nested
  @DisplayName("findByConnectorInstanceId")
  class FindByConnectorInstanceId {

    @Test
    @DisplayName("Given a spawned provider, should return it")
    void given_spawnedProvider_should_returnIt() {
      // Arrange
      SecretsProvider provider = new SecretsProvider.Placeholder();
      Manager manager = mock(Manager.class);
      when(managerFactory.getManager(TENANT_ID)).thenReturn(manager);
      when(manager.requestForInstance(any(), any())).thenReturn(provider);

      // Act
      Optional<SecretsProvider> resolved =
          resolver.findByConnectorInstanceId(TENANT_ID, CONNECTOR_INSTANCE_ID);

      // Assert
      assertThat(resolved).containsSame(provider);
    }

    @Test
    @DisplayName("Given an unknown instance, should return empty instead of throwing")
    void given_unknownInstance_should_returnEmpty() {
      // Arrange — the background run must degrade per credential, never abort the tenant batch.
      Manager manager = mock(Manager.class);
      when(managerFactory.getManager(TENANT_ID)).thenReturn(manager);
      when(manager.requestForInstance(any(), any()))
          .thenThrow(new NoSuchElementException("no spawned integration"));

      // Act
      Optional<SecretsProvider> resolved =
          resolver.findByConnectorInstanceId(TENANT_ID, CONNECTOR_INSTANCE_ID);

      // Assert
      assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("Given a null or blank instance id, should return empty without any lookup")
    void given_blankInstanceId_should_returnEmptyWithoutLookup() {
      // Act & Assert
      assertThat(resolver.findByConnectorInstanceId(TENANT_ID, null)).isEmpty();
      assertThat(resolver.findByConnectorInstanceId(TENANT_ID, "  ")).isEmpty();
      verifyNoInteractions(managerFactory);
    }
  }

  @Nested
  @DisplayName("resolveByConnectorInstanceId")
  class ResolveByConnectorInstanceId {

    @Test
    @DisplayName("Given an unresolvable instance, should throw")
    void given_unresolvableInstance_should_throw() {
      // Arrange — the interactive CRUD path wants a hard failure, unlike the background run.
      when(managerFactory.getManager(anyString()))
          .thenThrow(new IllegalStateException("no manager"));

      // Act & Assert
      assertThatThrownBy(
              () -> resolver.resolveByConnectorInstanceId(TENANT_ID, CONNECTOR_INSTANCE_ID))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining(CONNECTOR_INSTANCE_ID);
    }
  }
}
