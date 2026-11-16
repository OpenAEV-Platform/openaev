package io.openaev.aop;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The access check of an endpoint whose method opens no transaction ({@code Propagation.SUPPORTS},
 * such as the scenario bulk delete) runs with none. On the plain route whose {@code X-Tenant-Ids}
 * header names a tenant other than the default one, it decides in the default tenant on the caller
 * as the request loaded it: with no transaction, that load reads the roles of every tenant the
 * caller belongs to.
 *
 * <p>So this class is NOT {@code @Transactional}: the request must run with no transaction, as in
 * production. Everything it creates is committed, and the tenant is removed in {@link #cleanUp()}.
 */
@DisplayName("Access check of an endpoint that opens no transaction, on the plain route")
class AccessControlWithoutTransactionTest extends IntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  private static final Set<Capability> DELETE_RIGHTS =
      Set.of(Capability.ACCESS_ASSESSMENT, Capability.DELETE_ASSESSMENT);

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;

  @AfterEach
  void cleanUp() {
    tenantHelper.deleteCommittedTenants(tenantA);
    tenantA = null;
  }

  @Nested
  @DisplayName(
      "a caller without the admin flag, holding the delete right in its default role and A")
  class NonAdminCallerOfTheDefaultTenantAndA {

    @Test
    @WithMockUser(
        withCapabilities = {Capability.ACCESS_ASSESSMENT, Capability.DELETE_ASSESSMENT},
        autoJoinDefaultTenant = true)
    @DisplayName("given_aBulkDeleteSelectingAByHeader_should_authorizeIt")
    void given_aBulkDeleteSelectingAByHeader_should_authorizeIt() throws Exception {
      // Arrange
      tenantA =
          tenantHelper.createTenantWithCapabilities("no-tx-access-check-a", DELETE_RIGHTS).getId();
      TenantContext.clearCurrentTenant();
      assertEquals(Tenant.DEFAULT_TENANT_UUID, TenantContext.getCurrentTenant());

      // Act + Assert
      mvc.perform(
              delete(SCENARIO_URI)
                  .header(TENANT_HEADER, tenantA)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      asJsonString(
                          Map.of("scenario_ids_to_process", List.of(UUID.randomUUID().toString()))))
                  .with(csrf()))
          .andExpect(status().isOk())
          .andExpect(content().json("[]"));
    }
  }
}
