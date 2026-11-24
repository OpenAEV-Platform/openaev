package io.openaev.rest.injector_contract;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A built-in contract id repeats in every tenant that registers its injector, and a caller of
 * several tenants with no selector reads all of them. The search, its total and every facet count
 * read a contract once per id, so the same caller sees the numbers of a caller of one tenant.
 *
 * <p>Deliberately NOT {@code @Transactional}: each HTTP call runs in its own transaction with its
 * own tenant scope, the way a real request does.
 */
@DisplayName("Contract search and counts read a contract id once for a caller of several tenants")
@WithMockUser(isAdmin = true)
class InjectorContractApiSearchTenantScopeTest extends ProductionTablesIntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    // The caller belongs to the default tenant and to B, both holding the built-in manual contract
    // under the same id. With no header the scope is the whole membership while the ambient tenant
    // stays the default one: TenantContext is set on the prefixed route only.
    tenantB = tenantHelper.createTenantWithCurrentUser("ic-search-b").getId();
    builtinConnectorRegistration.register(manualInjectorIntegrationFactory, tenantB);
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    builtinConnectorRegistration.register(
        manualInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @AfterEach
  void cleanup() {
    tenantHelper.deleteCommittedTenants(tenantB);
  }

  private MvcResult call(String uri, Object body, String tenantHeader) throws Exception {
    MockHttpServletRequestBuilder request =
        post(uri).contentType(MediaType.APPLICATION_JSON).content(asJsonString(body)).with(csrf());
    if (tenantHeader != null) {
      request.header("X-Tenant-Ids", tenantHeader);
    }
    MvcResult result = mvc.perform(request).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return result;
  }

  private static InjectorContractSearchPaginationInput searchInput(boolean fullDetails) {
    InjectorContractSearchPaginationInput input = new InjectorContractSearchPaginationInput();
    input.setSize(1000);
    // Tenant B also holds the built-ins its onboarding registers: the text narrows every request to
    // the one contract both tenants hold, so a request of one tenant is the baseline.
    input.setTextSearch("Manual");
    input.setIncludeFullDetails(fullDetails);
    return input;
  }

  private void assertEachIdOnceAndTotalMatches(
      MvcResult result, String idPath, MvcResult oneTenantBaseline) throws Exception {
    String body = result.getResponse().getContentAsString();
    List<String> ids = JsonPath.read(body, idPath);
    assertThat(ids).containsExactly(ManualContract.MANUAL_DEFAULT);
    long total = ((Number) JsonPath.read(body, "$.totalElements")).longValue();
    assertThat(total).isEqualTo(1);
    long baselineTotal =
        ((Number)
                JsonPath.read(
                    oneTenantBaseline.getResponse().getContentAsString(), "$.totalElements"))
            .longValue();
    assertThat(total).isEqualTo(baselineTotal);
  }

  @Nested
  @DisplayName("Search")
  class Search {

    @Test
    @DisplayName("given_callerOfTwoTenantsAndNoHeader_should_searchContractsOncePerId")
    void given_callerOfTwoTenantsAndNoHeader_should_searchContractsOncePerId() throws Exception {
      // Act
      MvcResult full = call(INJECTOR_CONTRACT_URL + "/search", searchInput(true), null);
      MvcResult base = call(INJECTOR_CONTRACT_URL + "/search", searchInput(false), null);
      MvcResult defaultOnly =
          call(INJECTOR_CONTRACT_URL + "/search", searchInput(true), Tenant.DEFAULT_TENANT_UUID);

      // Assert: one row per id, a total that counts ids, the total of a caller of one tenant
      assertEachIdOnceAndTotalMatches(full, "$.content[*].injector_contract_id", defaultOnly);
      assertEachIdOnceAndTotalMatches(base, "$.content[*].injector_contract_id", defaultOnly);
    }

    @Test
    @DisplayName("given_callerOfTwoTenantsAndNoHeader_should_searchThreatArsenalOncePerId")
    void given_callerOfTwoTenantsAndNoHeader_should_searchThreatArsenalOncePerId()
        throws Exception {
      // Act
      MvcResult both = call(THREAT_ARSENAL_URL + "/search", searchInput(true), null);
      MvcResult defaultOnly =
          call(THREAT_ARSENAL_URL + "/search", searchInput(true), Tenant.DEFAULT_TENANT_UUID);

      // Assert
      assertEachIdOnceAndTotalMatches(both, "$.content[*].injector_contract_id", defaultOnly);
    }
  }

  @Nested
  @DisplayName("Facet counts")
  class Counts {

    private Map<String, Integer> platforms(MvcResult result) throws Exception {
      return JsonPath.read(result.getResponse().getContentAsString(), "$.platforms");
    }

    private int sumOfCounts(MvcResult result) throws Exception {
      List<Integer> counts = JsonPath.read(result.getResponse().getContentAsString(), "$[*].count");
      return counts.stream().mapToInt(Integer::intValue).sum();
    }

    @Test
    @DisplayName("given_callerOfTwoTenantsAndNoHeader_should_countPlatformsOncePerId")
    void given_callerOfTwoTenantsAndNoHeader_should_countPlatformsOncePerId() throws Exception {
      // Act
      MvcResult both = call(INJECTOR_CONTRACT_URL + "/facet-counts", searchInput(true), null);
      MvcResult defaultOnly =
          call(
              INJECTOR_CONTRACT_URL + "/facet-counts",
              searchInput(true),
              Tenant.DEFAULT_TENANT_UUID);
      MvcResult arsenalBoth = call(THREAT_ARSENAL_URL + "/facet-counts", searchInput(true), null);

      // Assert
      assertThat(platforms(defaultOnly).values()).anyMatch(count -> count > 0);
      assertThat(platforms(both)).isEqualTo(platforms(defaultOnly));
      assertThat(platforms(arsenalBoth)).isEqualTo(platforms(defaultOnly));
    }

    @Test
    @DisplayName("given_callerOfTwoTenantsAndNoHeader_should_countDomainsOncePerId")
    void given_callerOfTwoTenantsAndNoHeader_should_countDomainsOncePerId() throws Exception {
      // Act
      MvcResult both = call(INJECTOR_CONTRACT_URL + "/domain-counts", searchInput(true), null);
      MvcResult defaultOnly =
          call(
              INJECTOR_CONTRACT_URL + "/domain-counts",
              searchInput(true),
              Tenant.DEFAULT_TENANT_UUID);
      MvcResult arsenalBoth = call(THREAT_ARSENAL_URL + "/domain-counts", searchInput(true), null);

      // Assert: the number of (contract, domain) pairs, not the number of copies
      assertThat(sumOfCounts(both)).isEqualTo(sumOfCounts(defaultOnly));
      assertThat(sumOfCounts(arsenalBoth)).isEqualTo(sumOfCounts(defaultOnly));
    }

    @Test
    @DisplayName("given_callerOfTwoTenantsAndNoHeader_should_countAuthorsOncePerId")
    void given_callerOfTwoTenantsAndNoHeader_should_countAuthorsOncePerId() throws Exception {
      // Act
      MvcResult both = call(INJECTOR_CONTRACT_URL + "/author-counts", searchInput(true), null);
      MvcResult defaultOnly =
          call(
              INJECTOR_CONTRACT_URL + "/author-counts",
              searchInput(true),
              Tenant.DEFAULT_TENANT_UUID);
      MvcResult arsenalBoth = call(THREAT_ARSENAL_URL + "/author-counts", searchInput(true), null);

      // Assert
      assertThat(sumOfCounts(both)).isEqualTo(sumOfCounts(defaultOnly));
      assertThat(sumOfCounts(arsenalBoth)).isEqualTo(sumOfCounts(defaultOnly));
    }
  }
}
