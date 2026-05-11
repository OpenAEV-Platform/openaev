package io.openaev.xtmhub.collector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.DefaultTenantExtension;
import io.openaev.xtmhub.TenantRegistrationDetails;
import io.openaev.xtmhub.XtmHubClient;
import io.openaev.xtmhub.XtmHubRegistrationStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Integration test for {@link XtmHubConnectivityCollectorService}. */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
@WithMockUser(isAdmin = true)
@ExtendWith(DefaultTenantExtension.class)
@DisplayName("XtmHubConnectivityCollectorService integration tests")
class XtmHubConnectivityCollectorServiceTest extends IntegrationTest {

  @Autowired private XtmHubConnectivityCollectorService collectorService;
  @Autowired private TenantXtmHubRegistrationRepository tenantXtmHubRegistrationRepository;
  @Autowired private TenantComposer tenantComposer;

  @MockitoBean private XtmHubClient xtmHubClient;

  // Tracks registrationId → tenantId so cleanup can switch to the right context per row.
  private final Map<String, String> createdRegistrationIdToTenantId = new LinkedHashMap<>();
  private final List<String> createdTenantIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    // Delete each registration while the tenant context matches its tenant_id (required by RLS).
    createdRegistrationIdToTenantId.forEach(
        (registrationId, tenantId) -> {
          TenantContext.setCurrentTenant(tenantId);
          try {
            tenantXtmHubRegistrationRepository.deleteById(registrationId);
          } finally {
            TenantContext.clearCurrentTenant();
          }
        });
    createdTenantIds.forEach(tenantRepository::deleteById);
    createdRegistrationIdToTenantId.clear();
    createdTenantIds.clear();
  }

  @Test
  @DisplayName(
      "Should call XTM Hub with only the 2 active tenant registrations,"
          + " excluding the one on a soft-deleted tenant")
  void whenThreeRegistrationsOneOnDeletedTenant_ShouldCallHubWithOnlyTwoActiveTenants() {
    // Given — create 3 tenants, 2 active and 1 soft-deleted
    Tenant tenantA = createTenant("Tenant-A");
    Tenant tenantB = createTenant("Tenant-B");
    Tenant tenantDeleted = createTenant("Tenant-Deleted");
    tenantDeleted.setDeletedAt(Instant.now());
    tenantRepository.save(tenantDeleted);

    // Register all 3 tenants on XTM Hub.
    saveRegistration("token-a", tenantA);
    saveRegistration("token-b", tenantB);
    saveRegistration("token-deleted", tenantDeleted);

    when(xtmHubClient.refreshRegistrationStatusAllTenants(any(), any(), any()))
        .thenReturn(Map.of());

    // When
    collectorService.run();

    // Then — the hub was called exactly once, carrying only the 2 active tenant IDs
    ArgumentCaptor<Map<String, TenantRegistrationDetails>> tenantsCaptor = ArgumentCaptor.captor();
    verify(xtmHubClient).refreshRegistrationStatusAllTenants(any(), any(), tenantsCaptor.capture());

    Map<String, TenantRegistrationDetails> sentTenants = tenantsCaptor.getValue();
    assertThat(sentTenants)
        .as("Hub payload must contain exactly the 2 active tenant IDs")
        .hasSize(2)
        .containsKey(tenantA.getId())
        .containsKey(tenantB.getId())
        .doesNotContainKey(tenantDeleted.getId());
  }

  // -- Helpers --

  private Tenant createTenant(String name) {
    Tenant tenant = TenantFixture.getTenant(name);
    tenantComposer.forTenant(tenant).persist();
    createdTenantIds.add(tenant.getId());
    return tenant;
  }

  private void saveRegistration(String token, Tenant tenant) {
    TenantContext.setCurrentTenant(tenant.getId());
    try {
      TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
      registration.setToken(token);
      registration.setRegistrationStatus(XtmHubRegistrationStatus.REGISTERED);
      registration.setConnectivityEmailEligible(true);
      TenantXtmHubRegistration saved = tenantXtmHubRegistrationRepository.save(registration);
      createdRegistrationIdToTenantId.put(saved.getId(), tenant.getId());
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }
}
