package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Tenant;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.rest.exception.TenantSingleScopeRequiredException;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.mockUser.WithMockUser;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
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

  private static final String URL = THREAT_ARSENAL_URL;
  private static final String DEFAULT_LABEL = "default-tenant-copy";
  private static final String OTHER_LABEL = "other-tenant-copy";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
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
    // Each tenant's copy carries its own label, so a response tells which copy it came from.
    labelCopy(Tenant.DEFAULT_TENANT_UUID, DEFAULT_LABEL);
    labelCopy(otherTenantId, OTHER_LABEL);
  }

  private void labelCopy(String tenantId, String label) {
    new JdbcTemplate(dataSource)
        .update(
            "UPDATE injectors_contracts SET injector_contract_labels = hstore('en', ?)"
                + " WHERE injector_contract_id = ? AND tenant_id = ?",
            label,
            ManualContract.MANUAL_DEFAULT,
            tenantId);
  }

  @AfterEach
  void cleanup() {
    if (otherTenantId != null) {
      tenantHelper.deleteCommittedTenants(otherTenantId);
    }
  }

  @Test
  @DisplayName(
      "given a caller member of two tenants that both hold the built-in contract and no selector,"
          + " should answer with the default tenant's copy")
  void
      given_callerInTwoTenantsWithTheBuiltinContractInBothAndNoSelector_should_getDefaultTenantCopy()
          throws Exception {
    // -- ACT --
    // @RequireTenantSelector pins a caller with no selector to the default tenant: the
    // behaviour is the project's rule, stated here because it also applies to a caller who meant
    // another tenant.
    MvcResult result =
        mvc.perform(get(URL + "/" + ManualContract.MANUAL_DEFAULT).with(csrf())).andReturn();

    // -- ASSERT --
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(labelOf(result)).isEqualTo(DEFAULT_LABEL);
  }

  @Test
  @DisplayName(
      "given a caller selecting one of the two tenants, should answer with that tenant's copy")
  void given_callerSelectsOneOfTheTwoTenants_should_getThatTenantCopy() throws Exception {
    // -- ACT --
    MvcResult result =
        mvc.perform(
                get(URL + "/" + ManualContract.MANUAL_DEFAULT)
                    .header("X-Tenant-Ids", otherTenantId)
                    .with(csrf()))
            .andReturn();

    // -- ASSERT --
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(labelOf(result)).isEqualTo(OTHER_LABEL);
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
                get(URL + "/" + ManualContract.MANUAL_DEFAULT)
                    .header("X-Tenant-Ids", otherTenantId + "," + Tenant.DEFAULT_TENANT_UUID)
                    .with(csrf()))
            .andReturn();

    // -- ASSERT --
    assertThat(result.getResponse().getStatus())
        .as(
            "an explicit multi-tenant selector is refused as a bad request rather than reaching"
                + " the ambiguous lookup")
        .isEqualTo(400);
    assertThat(result.getResponse().getContentAsString())
        .as("the refusal says a single tenant is needed, not that a write was refused")
        .contains(TenantSingleScopeRequiredException.CODE);
  }

  private static String labelOf(MvcResult result) throws Exception {
    return JsonPath.read(result.getResponse().getContentAsString(), "$.action_labels.en");
  }
}
