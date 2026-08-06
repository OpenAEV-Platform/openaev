package io.openaev.api.secrets_providers;

import static io.openaev.api.secrets_providers.SecretsProviderApi.TENANT_SECRETS_PROVIDER_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorInstanceInMemory;
import io.openaev.database.model.ConnectorType;
import io.openaev.database.model.Tenant;
import io.openaev.integration.ComponentRequest;
import io.openaev.integration.Integration;
import io.openaev.integration.ManagerFactory;
import io.openaev.integration.impl.secrets.local.LocalSecretsProviderIntegration;
import io.openaev.secrets.provider.SecretsProvider;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@TestPropertySource(properties = "openaev.enabled-dev-features=CREDENTIAL_ASSET")
@DisplayName("Secrets Provider API Integration Tests")
public class SecretsProviderApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;
  @Autowired private ManagerFactory managerFactory;

  @Nested
  @DisplayName("Tenant isolation")
  class TenantIsolation {

    @Test
    @DisplayName("List endpoint should only return providers from requested tenant")
    void given_twoTenants_should_listOnlyProvidersFromRequestedTenant() throws Exception {
      // Arrange
      String placeholderId = "id-placeholder";
      Tenant tenantA = tenantIsolationTestHelper.createTenantWithCurrentUser("tenant-a");
      tenantIsolationTestHelper.createTenantWithCurrentUser("tenant-b");
      addPlaceholderSecretProviderOnTenant(tenantA, placeholderId);

      String tenantAUri = TENANT_SECRETS_PROVIDER_URI.replace("{tenantId}", tenantA.getId());

      // Act
      String tenantAResponse =
          mvc.perform(get(tenantAUri))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> providerIds = JsonPath.read(tenantAResponse, "$[*].secrets_provider_id");

      assertThat(providerIds)
          .containsExactlyInAnyOrder(
              LocalSecretsProviderIntegration.LOCAL_SECRETS_PROVIDER_ID, placeholderId);
    }

    @Test
    @DisplayName("Read-by-id endpoint should return provider from the requested tenant scope")
    void given_existingProviderId_should_returnProviderInRequestedTenantScope() throws Exception {
      // Arrange
      String placeholderId = "id-placeholder-test";
      Tenant tenantA = tenantIsolationTestHelper.createTenantWithCurrentUser("tenant-a");
      tenantIsolationTestHelper.createTenantWithCurrentUser("tenant-b");
      addPlaceholderSecretProviderOnTenant(tenantA, placeholderId);

      // Act
      String tenantAProviderUri =
          TENANT_SECRETS_PROVIDER_URI.replace("{tenantId}", tenantA.getId()) + "/" + placeholderId;

      // Assert
      String tenantAResponse =
          mvc.perform(get(tenantAProviderUri))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String returnedProviderId = JsonPath.read(tenantAResponse, "$.secrets_provider_id");
      assertThat(returnedProviderId).isEqualTo(placeholderId);
    }

    private void addPlaceholderSecretProviderOnTenant(Tenant tenant, String placeholderId) {
      SecretsProvider.Placeholder placeholder = new SecretsProvider.Placeholder();
      placeholder.setId(placeholderId);
      placeholder.setName("Placeholder");

      ConnectorInstanceInMemory inMemoryInstance = new ConnectorInstanceInMemory();
      inMemoryInstance.setId("placeholder-instance-id");
      inMemoryInstance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.started);
      inMemoryInstance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.starting);

      ConnectorInstanceConfiguration conf = new ConnectorInstanceConfiguration();
      conf.setKey(ConnectorType.SECRETS_PROVIDER.getIdKeyName());
      conf.setValue(JsonNodeFactory.instance.textNode(placeholder.getId()));
      inMemoryInstance.setConfigurations(Set.of(conf));

      Integration integration = mock(Integration.class);
      when(integration.requestComponent(any(ComponentRequest.class), eq(SecretsProvider.class)))
          .thenReturn(List.of(placeholder));

      managerFactory
          .getManager(tenant.getId())
          .getSpawnedIntegrations()
          .put(inMemoryInstance, integration);
    }
  }
}
