package io.openaev.rest.injector_contract;

import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
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
 * The raw contract list ({@code GET /api/injector_contracts}) is scoped by the request's tenant
 * scope alone. On the non-prefixed route the selector sits in {@code X-Tenant-Ids} while the
 * ambient tenant stays the default one, so a list that also filtered on the ambient tenant would
 * intersect the two and answer nothing for a caller whose only tenant is not the default.
 *
 * <p>Deliberately NOT {@code @Transactional}: the HTTP call must run in its own transaction with
 * its own tenant scope, the way a real request does.
 */
@DisplayName("The raw injector contract list follows the request's tenant scope")
@WithMockUser(isAdmin = true)
class InjectorContractApiRawListTenantScopeTest extends IntegrationTest {

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    // The caller's only tenant is B, which holds the built-in contract.
    tenantB = tenantHelper.createTenantWithCurrentUser("ic-raw-list-b").getId();
    builtinConnectorRegistration.register(manualInjectorIntegrationFactory, tenantB);
  }

  @AfterEach
  void cleanup() {
    tenantHelper.deleteCommittedTenants(tenantB);
  }

  @Test
  @DisplayName("given_callerOfOneNonDefaultTenantSelectingItByHeader_should_listItsContracts")
  void given_callerOfOneNonDefaultTenantSelectingItByHeader_should_listItsContracts()
      throws Exception {
    // Act
    MvcResult result =
        mvc.perform(get(INJECTOR_CONTRACT_URL).header("X-Tenant-Ids", tenantB).with(csrf()))
            .andReturn();

    // Assert
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<String> ids =
        JsonPath.read(result.getResponse().getContentAsString(), "$[*].injector_contract_id");
    assertThat(ids).containsOnlyOnce(ManualContract.MANUAL_DEFAULT);
  }

  @Test
  @DisplayName("given_callerOfTwoTenantsAndNoHeader_should_listOneRowPerTenantCopyOfTheSameId")
  void given_callerOfTwoTenantsAndNoHeader_should_listOneRowPerTenantCopyOfTheSameId()
      throws Exception {
    // Arrange: the caller belongs to the default tenant and to B, both holding the built-in
    // contract under the same id. With no header the scope is the whole membership while the
    // ambient tenant stays the default one: TenantContext is set on the prefixed route only.
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    builtinConnectorRegistration.register(
        manualInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);

    // Act
    MvcResult result = mvc.perform(get(INJECTOR_CONTRACT_URL).with(csrf())).andReturn();

    // Assert: one row per tenant copy. A list that also filtered on the ambient tenant would
    // answer the default tenant's copy alone.
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    List<String> ids =
        JsonPath.read(result.getResponse().getContentAsString(), "$[*].injector_contract_id");
    assertThat(ids.stream().filter(ManualContract.MANUAL_DEFAULT::equals).count()).isEqualTo(2);
  }
}
