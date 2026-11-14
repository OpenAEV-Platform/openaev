package io.openaev.rest.injector_contract;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;
import static io.openaev.rest.injector_contract.InjectorContractApi.INJECTOR_CONTRACT_URL;
import static io.openaev.rest.injector_contract.InjectorContractApi.TENANT_INJECTOR_CONTRACT_URL;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.TenantUriUtils;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.ContractRightsFixture;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A caller of tenant A never reaches a contract that only tenant B holds: by id it is not found,
 * and the search and the contract list return no row of B. The same calls on a contract of A
 * succeed.
 *
 * <p>The caller is a non-admin member of tenant A with {@code ACCESS_THREAT_ARSENALS} through a
 * group of A. On the non-prefixed route the access gate also reads the caller's rights in the
 * ambient tenant, the default one, so that route gives the caller a membership of the default
 * tenant and the same capability through a default-tenant group. Deliberately NOT
 * {@code @Transactional}: each HTTP call runs in its own transaction with its own tenant scope.
 */
@DisplayName("A caller of one tenant and a contract held by another tenant")
@CoversTenantIsolation("injectors_contracts")
@WithMockUser
class InjectorContractCrossTenantRefusalTest extends ProductionTablesIntegrationTest {

  private static final String LABEL_PREFIX = "refusal-copy-";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TestUserHolder testUserHolder;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  private ContractRightsFixture rights;
  private String userId;
  private String tenantA;
  private String tenantB;
  private String contractOfA;
  private String contractOfB;

  /** How the request names tenant A. */
  enum Route {
    /** The non-prefixed route with {@code X-Tenant-Ids: A}: the ambient tenant is the default. */
    HEADER,
    /** {@code /api/tenants/{A}/...}. */
    PREFIXED
  }

  enum Surface {
    CONTRACT(INJECTOR_CONTRACT_URL, TENANT_INJECTOR_CONTRACT_URL, "injector_contract_labels"),
    ARSENAL(THREAT_ARSENAL_URL, TENANT_THREAT_ARSENAL_URL, "action_labels");

    final String url;
    final String tenantUrl;
    final String labelsField;

    Surface(String url, String tenantUrl, String labelsField) {
      this.url = url;
      this.tenantUrl = tenantUrl;
      this.labelsField = labelsField;
    }
  }

  @BeforeEach
  void setup() throws Exception {
    rights = new ContractRightsFixture(dataSource);
    userId = testUserHolder.get().getId();
    tenantA = tenantHelper.createTenantWithCurrentUser("refusal-a").getId();
    // the onboarding of a tenant enrols its creator: tenant B has no member among the callers
    tenantB = tenantHelper.createTenant("refusal-b").getId();
    tenantHelper.detachCurrentUserFromTenant(tenantB);
    builtinConnectorRegistration.register(manualInjectorIntegrationFactory, tenantA);
    builtinConnectorRegistration.register(manualInjectorIntegrationFactory, tenantB);
    contractOfA = "refusal-a-" + UUID.randomUUID();
    contractOfB = "refusal-b-" + UUID.randomUUID();
    rights.cloneManualContract(contractOfA, tenantA, LABEL_PREFIX + tenantA);
    rights.cloneManualContract(contractOfB, tenantB, LABEL_PREFIX + tenantB);
    rights.group(tenantA, userId, Set.of(Capability.ACCESS_THREAT_ARSENALS));
    testUserHolder.refreshSecurityContext();
  }

  @AfterEach
  void cleanup() {
    rights.cleanup(userId);
    tenantHelper.deleteCommittedTenants(tenantA, tenantB);
  }

  /** The caller of tenant A and, on the non-prefixed route, of the default tenant as well. */
  private void prepare(Route route) {
    if (route == Route.HEADER) {
      tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
      rights.group(Tenant.DEFAULT_TENANT_UUID, userId, Set.of(Capability.ACCESS_THREAT_ARSENALS));
      testUserHolder.refreshSecurityContext();
    }
  }

  private MockHttpServletRequestBuilder request(
      MockHttpServletRequestBuilder builder, Route route) {
    // a fixture leaves a tenant on the test thread: production's thread starts with none
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return route == Route.HEADER ? builder.header("X-Tenant-Ids", tenantA) : builder;
  }

  private String path(Surface surface, Route route, String suffix) {
    String base =
        route == Route.PREFIXED
            ? surface.tenantUrl.replace("{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenantA)
            : surface.url;
    return base + suffix;
  }

  private int statusOfGetById(Surface surface, Route route, String contractId) throws Exception {
    return mvc.perform(request(get(path(surface, route, "/" + contractId)), route))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private List<String> searchedLabels(Surface surface, Route route) throws Exception {
    InjectorContractSearchPaginationInput input = new InjectorContractSearchPaginationInput();
    input.setSize(1000);
    input.setTextSearch(LABEL_PREFIX);
    input.setIncludeFullDetails(true);
    MvcResult result =
        mvc.perform(
                request(
                    post(path(surface, route, "/search"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(input))
                        .with(csrf()),
                    route))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return JsonPath.read(
        result.getResponse().getContentAsString(), "$.content[*]." + surface.labelsField + ".en");
  }

  private List<String> listedIds(Route route) throws Exception {
    String uri =
        route == Route.PREFIXED
            ? TENANT_INJECTOR_CONTRACT_URL.replace(
                "{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenantA)
            : INJECTOR_CONTRACT_URL;
    MvcResult result = mvc.perform(request(get(uri), route)).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return JsonPath.read(result.getResponse().getContentAsString(), "$[*].injector_contract_id");
  }

  @Nested
  @DisplayName("A contract held only by tenant B")
  class HeldOnlyByTenantB {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_contractHeldOnlyByTenantB_should_answerNotFoundById")
    void given_contractHeldOnlyByTenantB_should_answerNotFoundById(Route route) throws Exception {
      // Arrange
      prepare(route);

      // Act / Assert
      for (Surface surface : Surface.values()) {
        assertThat(statusOfGetById(surface, route, contractOfB))
            .as(surface + " by id")
            .isEqualTo(404);
      }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_contractHeldOnlyByTenantB_should_returnNoRowOfTenantBFromTheSearch")
    void given_contractHeldOnlyByTenantB_should_returnNoRowOfTenantBFromTheSearch(Route route)
        throws Exception {
      // Arrange
      prepare(route);

      // Act / Assert: the search finds tenant A's copy and none of tenant B's
      for (Surface surface : Surface.values()) {
        assertThat(searchedLabels(surface, route))
            .as(surface + " search")
            .containsExactly(LABEL_PREFIX + tenantA);
      }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_contractHeldOnlyByTenantB_should_listNoContractOfTenantB")
    void given_contractHeldOnlyByTenantB_should_listNoContractOfTenantB(Route route)
        throws Exception {
      // Arrange
      prepare(route);

      // Act / Assert
      assertThat(listedIds(route)).contains(contractOfA).doesNotContain(contractOfB);
    }
  }

  @Nested
  @DisplayName("A contract held only by tenant B, with a grant of a platform group")
  class HeldOnlyByTenantBWithAPlatformGrant {

    /**
     * A platform group's grant counts for every copy, so only the tenant scope refuses tenant B.
     */
    private void grantThroughPlatformGroup(Route route) {
      prepare(route);
      rights.grantObserver(rights.group(null, userId, Set.of()), contractOfB);
      testUserHolder.refreshSecurityContext();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_platformGrantOnContractOfTenantB_should_answerNotFoundById")
    void given_platformGrantOnContractOfTenantB_should_answerNotFoundById(Route route)
        throws Exception {
      // Arrange
      grantThroughPlatformGroup(route);

      // Act / Assert
      for (Surface surface : Surface.values()) {
        assertThat(statusOfGetById(surface, route, contractOfB))
            .as(surface + " by id")
            .isEqualTo(404);
      }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_platformGrantOnContractOfTenantB_should_returnNoRowOfTenantB")
    void given_platformGrantOnContractOfTenantB_should_returnNoRowOfTenantB(Route route)
        throws Exception {
      // Arrange
      grantThroughPlatformGroup(route);

      // Act / Assert: the search, and the contract list
      for (Surface surface : Surface.values()) {
        assertThat(searchedLabels(surface, route))
            .as(surface + " search")
            .containsExactly(LABEL_PREFIX + tenantA);
      }
      assertThat(listedIds(route)).contains(contractOfA).doesNotContain(contractOfB);
    }
  }

  @Nested
  @DisplayName("A contract held by tenant A")
  class HeldByTenantA {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_contractHeldByTenantA_should_serveItById")
    void given_contractHeldByTenantA_should_serveItById(Route route) throws Exception {
      // Arrange
      prepare(route);

      // Act / Assert
      for (Surface surface : Surface.values()) {
        assertThat(statusOfGetById(surface, route, contractOfA))
            .as(surface + " by id")
            .isEqualTo(200);
      }
    }
  }
}
