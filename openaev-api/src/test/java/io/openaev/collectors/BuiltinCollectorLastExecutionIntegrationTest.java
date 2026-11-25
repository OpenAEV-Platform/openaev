package io.openaev.collectors;

import static io.openaev.collectors.expectations_expiration_manager.config.ExpectationsExpirationManagerConfig.COLLECTOR_ID;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.collectors.expectations_expiration_manager.ExpectationsExpirationManagerJob;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A built-in collector runs inside the platform and never pings the registration endpoint, so its
 * {@code updatedAt} stays at its registration time: the collectors list must report the last
 * execution its runs stamp instead (issue #9862).
 *
 * <p>The class is deliberately NOT {@code @Transactional}: the expiration job opens one top-level
 * transaction per tenant and refuses to run inside an active one, as in production. The rows are
 * committed in their own tenant and removed with it. The class arms the production activation list,
 * whose terminal {@code *} covers the collectors table.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Built-in collectors report the last execution of their runs")
class BuiltinCollectorLastExecutionIntegrationTest extends IntegrationTest {

  private static final String TENANT_COLLECTORS_URI = "/api/tenants/{tenantId}/collectors";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ExpectationsExpirationManagerJob expectationsExpirationManagerJob;

  private String tenantId;

  @BeforeEach
  void createTenant() throws Exception {
    // The caller is a member of this tenant only, and onboarding registers the built-in
    // expiration collector in it.
    tenantId = tenantHelper.createTenantWithCurrentUser("collector-last-execution").getId();
  }

  @AfterEach
  void cleanup() {
    inTenant(
        tenantId,
        () -> {
          tenantHelper.deleteCommittedTenants(tenantId);
          return null;
        });
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("given the expiration job ran, the collectors list reports a run after its start")
  void given_expirationJobRan_should_reportLastExecutionAfterTheRun() throws Exception {
    // -- Arrange --
    // The job resolves the built-in collector in every tenant it visits. The default tenant
    // registers it lazily, on its first manager start, so register it here as that start would.
    registerExpirationCollector(Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    Instant runStart = Instant.now();

    // -- Act --
    expectationsExpirationManagerJob.run();
    String response =
        mvc.perform(
                get(TENANT_COLLECTORS_URI, tenantId)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- Assert --
    String path = "$[?(@.collector_id == '" + COLLECTOR_ID + "')]";
    assertThatJson(response).inPath(path + ".collector_external").isArray().containsExactly(false);
    List<String> lastExecutions = JsonPath.read(response, path + ".collector_last_execution");
    assertThat(lastExecutions).hasSize(1);
    assertThat(Instant.parse(lastExecutions.getFirst())).isAfterOrEqualTo(runStart);
  }

  private void registerExpirationCollector(String tenant) {
    inTenant(
        tenant,
        () -> {
          try {
            expectationsExpirationManagerJob.registerForTenant(tenant);
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
          return null;
        });
  }

  private <T> T inTenant(String tenant, Supplier<T> work) {
    String previousTenant =
        TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenant);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenant), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }
}
