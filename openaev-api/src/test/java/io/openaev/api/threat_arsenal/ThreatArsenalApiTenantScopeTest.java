package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Tenant;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.mockUser.WithMockUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * {@code ThreatArsenalApi#threatArsenal} is the threat-arsenal sibling of {@code
 * InjectorContractApi}'s singular endpoints over the same {@code InjectorContract} entity. Every
 * tenant holds its own copy of {@link ManualContract#MANUAL_DEFAULT} under the same id (#8485), so
 * a scope spanning several tenants that all hold it makes the by-id-or-external-id lookup match
 * more than one row.
 *
 * <p>Deliberately NOT {@code @Transactional}: the HTTP call must run in its own transaction with
 * its own tenant scope, the way a real request does.
 */
@DisplayName("ThreatArsenalApi#threatArsenal resolves a single tenant for a two-tenant caller")
@WithMockUser(isAdmin = true)
class ThreatArsenalApiTenantScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  private String otherTenantId;

  @BeforeEach
  void setUp() throws Exception {
    otherTenantId = tenantHelper.createTenantWithCurrentUser("threat-arsenal-scope").getId();
    // The caller must also belong to the default tenant, the other side of the ambiguous scope.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    // The built-in contract exists in both tenants, as it does in production (every tenant gets
    // its own copy at onboarding): this is what makes the bare-ambient-scope lookup match two rows
    // instead of failing to find any.
    builtinConnectorRegistration.register(manualInjectorIntegrationFactory, otherTenantId);
    builtinConnectorRegistration.register(
        manualInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void cleanup() {
    if (otherTenantId != null) {
      tenantHelper.deleteCommittedTenants(otherTenantId);
    }
  }

  @Test
  @DisplayName(
      "given a caller member of two tenants that both hold the built-in contract, should resolve"
          + " one tenant's copy instead of failing on a non-unique result")
  void given_callerInTwoTenantsWithTheBuiltinContractInBoth_should_resolveOneTenant()
      throws Exception {
    // -- ACT --
    MvcResult result =
        mvc.perform(get(THREAT_ARSENAL_URL + "/" + ManualContract.MANUAL_DEFAULT).with(csrf()))
            .andReturn();

    // -- ASSERT --
    assertThat(result.getResponse().getStatus())
        .as(
            "the endpoint resolves a single tenant's copy of the contract rather than throwing on"
                + " a non-unique result across the caller's two tenants")
        .isEqualTo(200);
  }

  @Test
  @DisplayName(
      "given a caller explicitly selecting both tenants that hold the built-in contract via"
          + " X-Tenant-Ids, should refuse with 400 instead of a non-unique result")
  void given_callerExplicitlySelectsBothTenantsHoldingTheContract_should_refuseWithBadRequest()
      throws Exception {
    // -- ACT --
    MvcResult result =
        mvc.perform(
                get(THREAT_ARSENAL_URL + "/" + ManualContract.MANUAL_DEFAULT)
                    .header("X-Tenant-Ids", otherTenantId + "," + Tenant.DEFAULT_TENANT_UUID)
                    .with(csrf()))
            .andReturn();

    // -- ASSERT --
    assertThat(result.getResponse().getStatus())
        .as(
            "an explicit multi-tenant selector is refused as a bad request rather than reaching"
                + " the ambiguous lookup")
        .isEqualTo(400);
  }
}
