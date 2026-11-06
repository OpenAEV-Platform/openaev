package io.openaev.rest.atomic_testing;

import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.rest.atomic_testing.form.AtomicTestingInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.WithMockUser;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code injectors_contracts} is tenant-active and the built-in email contract id repeats across
 * tenants (#8485). {@code AtomicTestingApi#createAtomicTesting} is only reachable through the
 * tenant-prefixed route or the non-prefixed one with an {@code X-Tenant-Ids} selector; on the
 * non-prefixed route {@code TenantInterceptor} never sets the v1 ambient tenant, so a service
 * resolving the contract through {@code TenantContext} would silently reach for the platform
 * default tenant's copy instead of the selected tenant's, while the caller asked to write into
 * another tenant entirely.
 *
 * <p>Deliberately NOT {@code @Transactional}: the HTTP call must run in its own transaction with
 * its own tenant scope, the way a real request does.
 */
@DisplayName(
    "AtomicTestingApi resolves the built-in contract in the write tenant, not the ambient one")
@WithMockUser(isAdmin = true)
class AtomicTestingApiWriteTenantContractTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private DataSource dataSource;

  private String otherTenantId;

  @BeforeEach
  void setUp() throws Exception {
    otherTenantId = tenantHelper.createTenantWithCurrentUser("at-write-tenant-contract").getId();
    // The built-in contract exists in both tenants, as it does in production: this is what makes
    // the ambient-tenant lookup resolve a copy at all (rather than throw outright) when it reaches
    // for the wrong tenant.
    builtinConnectorRegistration.register(emailInjectorIntegrationFactory, otherTenantId);
    builtinConnectorRegistration.register(
        emailInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void cleanup() {
    if (otherTenantId != null) {
      tenantHelper.deleteCommittedTenants(otherTenantId);
    }
  }

  @Test
  @DisplayName(
      "given the non-prefixed route with a selector naming a member tenant, should create the"
          + " inject in that tenant, not the ambient default")
  void given_nonPrefixedRouteWithMemberTenantSelector_should_createInSelectedTenant()
      throws Exception {
    // -- ARRANGE --
    // The request thread's ambient tenant is empty, exactly as a fresh request thread is: the
    // non-prefixed route never sets it, so it falls back to the platform default.
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

    AtomicTestingInput input = InjectFixture.createAtomicTesting("write-tenant-contract", null);

    // -- ACT --
    MockHttpServletResponse response =
        mvc.perform(
                post(ATOMIC_TESTING_URI)
                    .header(TENANT_IDS_HEADER, otherTenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse();

    // -- ASSERT --
    String injectId = JsonPath.read(response.getContentAsString(), "$.inject_id");
    String tenantId =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT tenant_id FROM injects WHERE inject_id = ?", String.class, injectId);
    assertThat(tenantId)
        .as("the inject is attributed to the selected tenant, not the ambient default")
        .isEqualTo(otherTenantId);
  }

  @Test
  @DisplayName(
      "given the non-prefixed route and an explicit injector of the selected tenant, should create"
          + " the inject with that injector, not look it up in the ambient default")
  void given_nonPrefixedRouteWithExplicitInjectorOfSelectedTenant_should_createWithThatInjector()
      throws Exception {
    // -- ARRANGE --
    // The injector id repeats across tenants (a composite key), so only a lookup in the contract's
    // tenant finds the selected tenant's copy; the ambient tenant of this route is the default one.
    String injectorId =
        new JdbcTemplate(dataSource)
            .queryForObject(
                "SELECT i.injector_id FROM injectors i JOIN injectors_injector_contracts l ON"
                    + " l.injector_id = i.injector_id AND l.tenant_id = i.tenant_id WHERE"
                    + " l.injector_contract_id = ? AND i.tenant_id = ?",
                String.class,
                EMAIL_DEFAULT,
                otherTenantId);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    AtomicTestingInput input = InjectFixture.createAtomicTesting("write-tenant-injector", null);
    input.setInjectorId(injectorId);

    // -- ACT --
    MockHttpServletResponse response =
        mvc.perform(
                post(ATOMIC_TESTING_URI)
                    .header(TENANT_IDS_HEADER, otherTenantId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(asJsonString(input))
                    .with(csrf()))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse();

    // -- ASSERT --
    String injectId = JsonPath.read(response.getContentAsString(), "$.inject_id");
    assertThat(
            new JdbcTemplate(dataSource)
                .queryForObject(
                    "SELECT inject_injector FROM injects WHERE inject_id = ?",
                    String.class,
                    injectId))
        .isEqualTo(injectorId);
  }
}
