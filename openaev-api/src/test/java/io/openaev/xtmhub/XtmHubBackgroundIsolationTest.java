package io.openaev.xtmhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.rest.settings.response.PlatformSettings;
import io.openaev.service.PlatformSettingsService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Production-like proof for the background refresh path with {@code tenant_xtmhub_registrations}
 * activated. The job must read the candidate registrations across tenants, then update each tenant
 * under its own scoped transaction so one tenant's failure rolls back only that tenant.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=tenant_xtmhub_registrations")
@WithMockUser(isAdmin = true)
@DisplayName("tenant_xtmhub_registrations background isolation")
class XtmHubBackgroundIsolationTest extends IntegrationTest {

  @Autowired private XtmHubService xtmHubService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private XtmHubClient xtmHubClient;
  @MockitoBean private PlatformSettingsService platformSettingsService;
  @MockitoBean private TenantSettingsService tenantSettingsService;
  @MockitoBean private XtmHubEmailService xtmHubEmailService;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    PlatformSettings settings = new PlatformSettings();
    settings.setPlatformId("platform-id");
    settings.setPlatformVersion("1.0.0");
    settings.setPlatformBaseUrl("http://localhost");
    settings.setXtmHubShouldSendConnectivityEmail("true");
    when(platformSettingsService.findSettings()).thenReturn(settings);
    when(tenantSettingsService.buildTenantUrl(anyString()))
        .thenAnswer(invocation -> "http://localhost/" + invocation.getArgument(0));

    tenantA = tenantHelper.createTenantWithCurrentUser("xtmhub-bg-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("xtmhub-bg-b").getId();
    seedRegistration(tenantA, "bg-token-a");
    seedRegistration(tenantB, "bg-token-b");
  }

  @AfterEach
  void cleanup() {
    for (String tenantId : Arrays.asList(tenantA, tenantB)) {
      if (tenantId == null) {
        continue;
      }
      jdbcTemplate.update("DELETE FROM tenant_xtmhub_registrations WHERE tenant_id = ?", tenantId);
    }
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  @Test
  @DisplayName(
      "given one tenant fails after its write when refreshing all tenants then only that tenant rolls back")
  void givenOneTenantFailsAfterItsWrite_whenRefreshingAllTenants_thenOnlyThatTenantRollsBack() {
    // Arrange
    when(xtmHubClient.refreshRegistrationStatusAllTenants(anyString(), anyString(), any()))
        .thenReturn(
            Map.of(
                tenantA, XtmHubConnectivityStatus.INACTIVE,
                tenantB, XtmHubConnectivityStatus.INACTIVE));
    doThrow(new IllegalStateException("boom for tenant B"))
        .when(xtmHubEmailService)
        .sendTenantLostConnectivityEmail(tenantB, "http://localhost/" + tenantB);

    // Act
    IllegalStateException aggregate =
        assertThrows(IllegalStateException.class, xtmHubService::refreshConnectivityAllTenants);

    // Assert
    assertEquals(
        XtmHubRegistrationStatus.LOST_CONNECTIVITY.name(),
        rawStatus(tenantA),
        "tenant A must commit its scoped update");
    assertEquals(
        XtmHubRegistrationStatus.REGISTERED.name(),
        rawStatus(tenantB),
        "tenant B must roll back its scoped update");
    assertEquals(
        false,
        rawConnectivityEligible(tenantA),
        "tenant A must commit its connectivity-notification flag update");
    assertEquals(
        true,
        rawConnectivityEligible(tenantB),
        "tenant B must roll back its connectivity-notification flag update");
    assertEquals(1, aggregate.getSuppressed().length, "the failing tenant cause must be preserved");
    assertTrue(
        aggregate.getSuppressed()[0].getMessage().contains("boom for tenant B"),
        "the aggregate must retain the failing tenant cause");
  }

  private void seedRegistration(String tenantId, String token) {
    jdbcTemplate.update(
        """
        INSERT INTO tenant_xtmhub_registrations (
          registration_id,
          tenant_id,
          registration_token,
          registration_date,
          registration_status,
          registration_user_id,
          registration_user_name,
          registration_last_connectivity_check,
          registration_connectivity_email_eligible
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID().toString(),
        tenantId,
        token,
        LocalDateTime.now().minusDays(3),
        XtmHubRegistrationStatus.REGISTERED.name(),
        "user-" + tenantId,
        "User " + tenantId,
        LocalDateTime.now().minusHours(25),
        true);
  }

  private String rawStatus(String tenantId) {
    return jdbcTemplate.queryForObject(
        "SELECT registration_status FROM tenant_xtmhub_registrations WHERE tenant_id = ?",
        String.class,
        tenantId);
  }

  private boolean rawConnectivityEligible(String tenantId) {
    Boolean eligible =
        jdbcTemplate.queryForObject(
            "SELECT registration_connectivity_email_eligible FROM tenant_xtmhub_registrations"
                + " WHERE tenant_id = ?",
            Boolean.class,
            tenantId);
    return Boolean.TRUE.equals(eligible);
  }
}
