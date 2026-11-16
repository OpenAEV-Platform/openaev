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
import com.opencsv.CSVReader;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.config.TenantUriUtils;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Tenant;
import io.openaev.injectors.manual.ManualContract;
import io.openaev.integration.impl.injectors.manual.ManualInjectorIntegrationFactory;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.mapper.MapperApi;
import io.openaev.utils.CsvType;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.BuiltinConnectorRegistration;
import io.openaev.utils.fixtures.ContractRightsFixture;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * A right that makes a contract row visible (a THREAT_ARSENAL grant, the {@code
 * ACCESS_THREAT_ARSENALS} capability, a bypass) counts for a tenant's copy of the contract only
 * when it comes from a group of that copy's own tenant; a grant also counts from a platform group.
 * A contract id repeats across tenants, so the same id has one copy per tenant and the caller's
 * groups of another tenant must not show it.
 *
 * <p>The caller is a non-admin member of the default tenant and of tenant A with no capability of
 * its own: the default tenant carries the mock user's own empty group, and every right below comes
 * from a group the test names. Each case holds one contract id copied into the default tenant and
 * into tenant A, labelled by tenant, and the built-in manual contract exists in both tenants.
 *
 * <p>Deliberately NOT {@code @Transactional}: each HTTP call runs in its own transaction with its
 * own tenant scope, the way a real request does. The ambient tenant of a request on the
 * non-prefixed route is the default one, whatever {@code X-Tenant-Ids} names.
 */
@DisplayName("Rights per tenant copy of a contract")
@WithMockUser
class InjectorContractRightsPerCopyTest extends ProductionTablesIntegrationTest {

  private static final String LABEL_PREFIX = "rights-copy-";
  private static final String HEADER = "X-Tenant-Ids";

  /** Both surfaces name the contract of a row by this field. */
  private static final String CONTRACT_ID_FIELD = "injector_contract_id";

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TestUserHolder testUserHolder;
  @Autowired private BuiltinConnectorRegistration builtinConnectorRegistration;
  @Autowired private ManualInjectorIntegrationFactory manualInjectorIntegrationFactory;

  private ContractRightsFixture rights;
  private String userId;
  private String tenantA;
  private String contractId;

  /** The way the request names the tenant of its scope. */
  enum Route {
    /** The non-prefixed route with {@code X-Tenant-Ids: A}: the ambient tenant is the default. */
    HEADER,
    /** {@code /api/tenants/{A}/...}: the ambient tenant is A. */
    PREFIXED
  }

  /** The two surfaces that run the same search, count and facet specification. */
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
    tenantA = tenantHelper.createTenantWithCurrentUser("rights-a").getId();
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    builtinConnectorRegistration.register(manualInjectorIntegrationFactory, tenantA);
    builtinConnectorRegistration.register(
        manualInjectorIntegrationFactory, Tenant.DEFAULT_TENANT_UUID);
    assertThat(rights.builtinRowsIn(tenantA))
        .as("the built-in contract id exists in tenant A")
        .isEqualTo(1);
    assertThat(rights.builtinRowsIn(Tenant.DEFAULT_TENANT_UUID))
        .as("the built-in contract id exists in the default tenant")
        .isEqualTo(1);
    contractId = "rights-" + UUID.randomUUID();
    rights.cloneManualContract(
        contractId, Tenant.DEFAULT_TENANT_UUID, labelOf(Tenant.DEFAULT_TENANT_UUID));
    rights.cloneManualContract(contractId, tenantA, labelOf(tenantA));
  }

  @AfterEach
  void cleanup() {
    rights.cleanup(userId);
    tenantHelper.deleteCommittedTenants(tenantA);
  }

  private static String labelOf(String tenantId) {
    return LABEL_PREFIX + tenantId;
  }

  private String groupOf(String tenantId, Set<Capability> capabilities) {
    String groupId = rights.group(tenantId, userId, capabilities);
    testUserHolder.refreshSecurityContext();
    return groupId;
  }

  private void grantOnContract(String groupId) {
    rights.grantObserver(groupId, contractId);
    testUserHolder.refreshSecurityContext();
  }

  private void grantOnIdentity(String groupId, String identityId) {
    rights.grantObserver(groupId, identityId);
    testUserHolder.refreshSecurityContext();
  }

  /** A contract id with a copy in the default tenant and one in tenant A, each labelled apart. */
  private String copiesOf(String name) {
    String id = "rights-" + name + "-" + UUID.randomUUID();
    rights.cloneManualContract(
        id, Tenant.DEFAULT_TENANT_UUID, copyLabel(name, Tenant.DEFAULT_TENANT_UUID));
    rights.cloneManualContract(id, tenantA, copyLabel(name, tenantA));
    return id;
  }

  private static String copyLabel(String name, String tenantId) {
    return LABEL_PREFIX + name + "-" + tenantId;
  }

  private MockHttpServletRequestBuilder request(
      MockHttpServletRequestBuilder builder, Route route, String scope) {
    // a fixture leaves a tenant on the test thread: production's thread starts with none
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return route == Route.HEADER && scope != null ? builder.header(HEADER, scope) : builder;
  }

  private String path(Surface surface, Route route, String suffix) {
    String base =
        route == Route.PREFIXED
            ? surface.tenantUrl.replace("{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenantA)
            : surface.url;
    return base + suffix;
  }

  private MvcResult postSearch(
      Surface surface, Route route, String scope, String suffix, Object body) throws Exception {
    MvcResult result =
        mvc.perform(
                request(
                    post(path(surface, route, suffix))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(body))
                        .with(csrf()),
                    route,
                    scope))
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    return result;
  }

  private static InjectorContractSearchPaginationInput search(String textSearch) {
    InjectorContractSearchPaginationInput input = new InjectorContractSearchPaginationInput();
    input.setSize(1000);
    input.setTextSearch(textSearch);
    input.setIncludeFullDetails(true);
    return input;
  }

  /** The labels of the copies the search returns, and the total it counts, on both surfaces. */
  private void assertSearchReturns(Route route, String scope, String... expectedLabels)
      throws Exception {
    for (Surface surface : Surface.values()) {
      String body =
          postSearch(surface, route, scope, "/search", search(LABEL_PREFIX))
              .getResponse()
              .getContentAsString();
      List<String> labels = JsonPath.read(body, "$.content[*]." + surface.labelsField + ".en");
      long total = ((Number) JsonPath.read(body, "$.totalElements")).longValue();
      assertThat(labels).as(surface + " labels").containsExactlyInAnyOrder(expectedLabels);
      assertThat(total).as(surface + " total").isEqualTo(expectedLabels.length);
    }
  }

  /**
   * The copies the search returns as {@code contract id|label}, and the total it counts, on one
   * surface. The id is read with the label so that one contract's copy cannot pass as another's.
   */
  private void assertSearchReturnsCopies(Surface surface, String scope, String... expectedCopies)
      throws Exception {
    String body =
        postSearch(surface, Route.HEADER, scope, "/search", search(LABEL_PREFIX))
            .getResponse()
            .getContentAsString();
    List<String> ids = JsonPath.read(body, "$.content[*]." + CONTRACT_ID_FIELD);
    List<String> labels = JsonPath.read(body, "$.content[*]." + surface.labelsField + ".en");
    long total = ((Number) JsonPath.read(body, "$.totalElements")).longValue();
    assertThat(ids).as(surface + " ids").hasSameSizeAs(labels);
    List<String> copies = new ArrayList<>();
    for (int i = 0; i < ids.size(); i++) {
      copies.add(ids.get(i) + "|" + labels.get(i));
    }
    assertThat(copies).as(surface + " copies").containsExactlyInAnyOrder(expectedCopies);
    assertThat(total).as(surface + " total").isEqualTo(expectedCopies.length);
  }

  @Nested
  @DisplayName("Search and count on a scope of tenant A")
  class SearchOfTenantA {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_grantOnlyInDefaultTenantGroup_should_notReturnTenantACopy")
    void given_grantOnlyInDefaultTenantGroup_should_notReturnTenantACopy(Route route)
        throws Exception {
      // Arrange: the caller holds the grant through a default-tenant group only
      grantOnContract(groupOf(Tenant.DEFAULT_TENANT_UUID, Set.of()));

      // Act / Assert
      assertSearchReturns(route, tenantA);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_grantInTenantAGroup_should_returnTenantACopy")
    void given_grantInTenantAGroup_should_returnTenantACopy(Route route) throws Exception {
      // Arrange
      grantOnContract(groupOf(tenantA, Set.of()));

      // Act / Assert
      assertSearchReturns(route, tenantA, labelOf(tenantA));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_grantInPlatformGroup_should_returnTenantACopy")
    void given_grantInPlatformGroup_should_returnTenantACopy(Route route) throws Exception {
      // Arrange
      grantOnContract(groupOf(null, Set.of()));

      // Act / Assert
      assertSearchReturns(route, tenantA, labelOf(tenantA));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_capabilityOnlyInDefaultTenantGroup_should_notReturnTenantACopies")
    void given_capabilityOnlyInDefaultTenantGroup_should_notReturnTenantACopies(Route route)
        throws Exception {
      // Arrange
      groupOf(Tenant.DEFAULT_TENANT_UUID, Set.of(Capability.ACCESS_THREAT_ARSENALS));

      // Act / Assert
      assertSearchReturns(route, tenantA);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_capabilityInTenantAGroup_should_returnTenantACopy")
    void given_capabilityInTenantAGroup_should_returnTenantACopy(Route route) throws Exception {
      // Arrange
      groupOf(tenantA, Set.of(Capability.ACCESS_THREAT_ARSENALS));

      // Act / Assert: the copy of A, and no other copy of the id
      assertSearchReturns(route, tenantA, labelOf(tenantA));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_bypassOnlyInDefaultTenantGroup_should_notReturnTenantACopy")
    void given_bypassOnlyInDefaultTenantGroup_should_notReturnTenantACopy(Route route)
        throws Exception {
      // Arrange
      groupOf(Tenant.DEFAULT_TENANT_UUID, Set.of(Capability.BYPASS));

      // Act / Assert
      assertSearchReturns(route, tenantA);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_bypassInTenantAGroup_should_returnTenantACopy")
    void given_bypassInTenantAGroup_should_returnTenantACopy(Route route) throws Exception {
      // Arrange
      groupOf(tenantA, Set.of(Capability.BYPASS));

      // Act / Assert
      assertSearchReturns(route, tenantA, labelOf(tenantA));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_bypassOnlyInPlatformGroup_should_notReturnTenantACopy")
    void given_bypassOnlyInPlatformGroup_should_notReturnTenantACopy(Route route) throws Exception {
      // Arrange: a platform bypass yields nothing for a threat arsenal, as the access gate says
      groupOf(null, Set.of(Capability.BYPASS));

      // Act / Assert
      assertSearchReturns(route, tenantA);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @WithMockUser(isAdmin = true)
    @DisplayName("given_platformAdministrator_should_returnTenantACopy")
    void given_platformAdministrator_should_returnTenantACopy(Route route) throws Exception {
      // Act / Assert: the administrator keeps every row of the scope
      assertSearchReturns(route, tenantA, labelOf(tenantA));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_payloadIdentityGrantOnlyInDefaultTenantGroup_should_notReturnTenantACopy")
    void given_payloadIdentityGrantOnlyInDefaultTenantGroup_should_notReturnTenantACopy(Route route)
        throws Exception {
      // Arrange: A's copy has a payload; the grant on its identity sits in a default-tenant group
      String identityId = rights.withPayloadIdentity(contractId, tenantA);
      rights.grantObserver(groupOf(Tenant.DEFAULT_TENANT_UUID, Set.of()), identityId);
      testUserHolder.refreshSecurityContext();

      // Act / Assert
      assertSearchReturns(route, tenantA);
    }

    @Test
    @DisplayName("given_payloadIdentityGrantInPlatformGroup_should_returnTenantACopy")
    void given_payloadIdentityGrantInPlatformGroup_should_returnTenantACopy() throws Exception {
      // Arrange: A's copy has a payload; the grant on its identity sits in a platform group
      grantOnIdentity(groupOf(null, Set.of()), rights.withPayloadIdentity(contractId, tenantA));

      // Act / Assert
      assertSearchReturns(Route.HEADER, tenantA, labelOf(tenantA));
    }

    @Test
    @DisplayName("given_payloadIdentityGrantInTenantAGroup_should_returnTenantACopy")
    void given_payloadIdentityGrantInTenantAGroup_should_returnTenantACopy() throws Exception {
      // Arrange
      grantOnIdentity(groupOf(tenantA, Set.of()), rights.withPayloadIdentity(contractId, tenantA));

      // Act / Assert
      assertSearchReturns(Route.HEADER, tenantA, labelOf(tenantA));
    }

    @Test
    @DisplayName("given_plannerGrantOnContractIdInTenantAGroup_should_returnTenantACopy")
    void given_plannerGrantOnContractIdInTenantAGroup_should_returnTenantACopy() throws Exception {
      // Arrange: a grant above OBSERVER counts as well
      rights.grant(
          groupOf(tenantA, Set.of()),
          contractId,
          Grant.GRANT_TYPE.PLANNER,
          Grant.GRANT_RESOURCE_TYPE.THREAT_ARSENAL);
      testUserHolder.refreshSecurityContext();

      // Act / Assert
      assertSearchReturns(Route.HEADER, tenantA, labelOf(tenantA));
    }

    @Test
    @DisplayName("given_grantHeldByTenantAGroupOfAnotherUser_should_notReturnTenantACopy")
    void given_grantHeldByTenantAGroupOfAnotherUser_should_notReturnTenantACopy() throws Exception {
      // Arrange: the group holds another user and not the caller
      grantOnContract(rights.group(tenantA, rights.otherUser(), Set.of()));

      // Act / Assert
      assertSearchReturns(Route.HEADER, tenantA);
    }

    @Test
    @DisplayName("given_grantOfAnotherResourceTypeOnTheContractId_should_notReturnTenantACopy")
    void given_grantOfAnotherResourceTypeOnTheContractId_should_notReturnTenantACopy()
        throws Exception {
      // Arrange: a SCENARIO grant whose resource id equals the contract id
      rights.grant(
          groupOf(tenantA, Set.of()),
          contractId,
          Grant.GRANT_TYPE.OBSERVER,
          Grant.GRANT_RESOURCE_TYPE.SCENARIO);
      testUserHolder.refreshSecurityContext();

      // Act / Assert
      assertSearchReturns(Route.HEADER, tenantA);
    }
  }

  @Nested
  @DisplayName("Search, count and facets on a scope of the default tenant and tenant A")
  class SearchOfBothTenants {

    private String bothTenants() {
      return Tenant.DEFAULT_TENANT_UUID + "," + tenantA;
    }

    @Test
    @DisplayName("given_grantOnlyInTenantAGroup_should_returnTheContractOnceAsTenantACopy")
    void given_grantOnlyInTenantAGroup_should_returnTheContractOnceAsTenantACopy()
        throws Exception {
      // Arrange: the default copy outranks A's, but the caller cannot see it
      grantOnContract(groupOf(tenantA, Set.of()));

      // Act / Assert
      assertSearchReturns(Route.HEADER, bothTenants(), labelOf(tenantA));
    }

    @Test
    @DisplayName("given_grantOnlyInDefaultTenantGroup_should_returnTheContractOnceAsDefaultCopy")
    void given_grantOnlyInDefaultTenantGroup_should_returnTheContractOnceAsDefaultCopy()
        throws Exception {
      // Arrange
      grantOnContract(groupOf(Tenant.DEFAULT_TENANT_UUID, Set.of()));

      // Act / Assert
      assertSearchReturns(Route.HEADER, bothTenants(), labelOf(Tenant.DEFAULT_TENANT_UUID));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Surface.class)
    @DisplayName("given_grantOnlyInTenantAGroup_should_countTheFacetsOfTenantACopy")
    void given_grantOnlyInTenantAGroup_should_countTheFacetsOfTenantACopy(Surface surface)
        throws Exception {
      // Arrange: the copies differ by platform, with no text search and no filter on the request
      rights.platformsOf(contractId, Tenant.DEFAULT_TENANT_UUID, "Linux");
      rights.platformsOf(contractId, tenantA, "Windows");
      grantOnContract(groupOf(tenantA, Set.of()));

      // Act
      String body =
          postSearch(surface, Route.HEADER, bothTenants(), "/facet-counts", search(null))
              .getResponse()
              .getContentAsString();

      // Assert
      Map<String, Integer> platforms = JsonPath.read(body, "$.platforms");
      assertThat(platforms).containsEntry("Windows", 1).containsEntry("Linux", 0);
    }

    @Test
    @DisplayName("given_grantOnlyInDefaultTenantGroup_should_notCountTenantACopyInTheFacets")
    void given_grantOnlyInDefaultTenantGroup_should_notCountTenantACopyInTheFacets()
        throws Exception {
      // Arrange
      rights.platformsOf(contractId, Tenant.DEFAULT_TENANT_UUID, "Linux");
      rights.platformsOf(contractId, tenantA, "Windows");
      grantOnContract(groupOf(Tenant.DEFAULT_TENANT_UUID, Set.of()));

      // Act: scope of A alone
      String body =
          postSearch(Surface.CONTRACT, Route.HEADER, tenantA, "/facet-counts", search(null))
              .getResponse()
              .getContentAsString();

      // Assert
      Map<String, Integer> platforms = JsonPath.read(body, "$.platforms");
      assertThat(platforms.values()).containsOnly(0);
    }
  }

  @Nested
  @DisplayName("Rights held through several groups, with no selector or over two tenants")
  class RightsAcrossGroups {

    private String bothTenants() {
      return Tenant.DEFAULT_TENANT_UUID + "," + tenantA;
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Surface.class)
    @DisplayName("given_grantOnlyInTenantAGroup_should_countTheFacetsOfTenantACopyForAPayloadGrant")
    void given_grantOnlyInTenantAGroup_should_countTheFacetsOfTenantACopyForAPayloadGrant(
        Surface surface) throws Exception {
      // Arrange: the grant is on the identity of A's copy, the copies differ by platform
      rights.platformsOf(contractId, Tenant.DEFAULT_TENANT_UUID, "Linux");
      rights.platformsOf(contractId, tenantA, "Windows");
      grantOnIdentity(groupOf(tenantA, Set.of()), rights.withPayloadIdentity(contractId, tenantA));

      // Act
      String body =
          postSearch(surface, Route.HEADER, bothTenants(), "/facet-counts", search(null))
              .getResponse()
              .getContentAsString();

      // Assert
      Map<String, Integer> platforms = JsonPath.read(body, "$.platforms");
      assertThat(platforms).containsEntry("Windows", 1).containsEntry("Linux", 0);
    }

    @Test
    @DisplayName("given_noSelectorAndGrantInTenantAGroup_should_returnTenantACopyOnce")
    void given_noSelectorAndGrantInTenantAGroup_should_returnTenantACopyOnce() throws Exception {
      // Arrange: the caller belongs to the default tenant and to A, and names neither
      grantOnContract(groupOf(tenantA, Set.of()));

      // Act / Assert: with no selector the scope is the caller's authorized set
      assertSearchReturns(Route.HEADER, null, labelOf(tenantA));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Surface.class)
    @DisplayName("given_rightsHeldInGroupsOfEachTenant_should_returnEachContractOnceWhereItIsHeld")
    void given_rightsHeldInGroupsOfEachTenant_should_returnEachContractOnceWhereItIsHeld(
        Surface surface) throws Exception {
      // Arrange: one group of each kind, and seven contract ids with a copy in each tenant
      String defaultTenant = Tenant.DEFAULT_TENANT_UUID;
      String defaultGroup = groupOf(defaultTenant, Set.of());
      String groupA = groupOf(tenantA, Set.of());
      String platformGroup = groupOf(null, Set.of());
      String x1 = copiesOf("x1");
      String x2 = copiesOf("x2");
      String x3 = copiesOf("x3");
      String x4 = copiesOf("x4");
      String x5 = copiesOf("x5");
      String x6 = copiesOf("x6");
      String x7 = copiesOf("x7");

      // Act / Assert: the grant on the id of X1 sits in a default group and X2's in an A group
      rights.grantObserver(defaultGroup, x1);
      rights.grantObserver(groupA, x2);
      testUserHolder.refreshSecurityContext();
      assertSearchReturnsCopies(
          surface,
          bothTenants(),
          x1 + "|" + copyLabel("x1", defaultTenant),
          x2 + "|" + copyLabel("x2", tenantA));

      // a platform group counts in both tenants, and the default copy outranks A's
      rights.grantObserver(platformGroup, x3);
      testUserHolder.refreshSecurityContext();
      assertSearchReturnsCopies(
          surface,
          bothTenants(),
          x1 + "|" + copyLabel("x1", defaultTenant),
          x2 + "|" + copyLabel("x2", tenantA),
          x3 + "|" + copyLabel("x3", defaultTenant));

      // each copy has a payload identity of its own: a grant on it names that copy's payload
      grantOnIdentity(defaultGroup, rights.withPayloadIdentity(x4, defaultTenant));
      grantOnIdentity(groupA, rights.withPayloadIdentity(x5, tenantA));
      grantOnIdentity(platformGroup, rights.withPayloadIdentity(x6, tenantA));
      grantOnIdentity(groupA, rights.withPayloadIdentity(x7, defaultTenant));
      assertSearchReturnsCopies(
          surface,
          bothTenants(),
          x1 + "|" + copyLabel("x1", defaultTenant),
          x2 + "|" + copyLabel("x2", tenantA),
          x3 + "|" + copyLabel("x3", defaultTenant),
          x4 + "|" + copyLabel("x4", defaultTenant),
          x5 + "|" + copyLabel("x5", tenantA),
          x6 + "|" + copyLabel("x6", tenantA));
    }
  }

  @Nested
  @DisplayName("Read by id, decided by the access gate in the ambient tenant")
  class ReadById {

    private MvcResult getById(Surface surface, Route route) throws Exception {
      return mvc.perform(request(get(path(surface, route, "/" + contractId)), route, tenantA))
          .andReturn();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Surface.class)
    @DisplayName("given_grantOnlyInDefaultTenantGroup_should_refuseTenantACopyOnThePrefixedRoute")
    void given_grantOnlyInDefaultTenantGroup_should_refuseTenantACopyOnThePrefixedRoute(
        Surface surface) throws Exception {
      // Arrange
      grantOnContract(groupOf(Tenant.DEFAULT_TENANT_UUID, Set.of()));

      // Act / Assert
      assertThat(getById(surface, Route.PREFIXED).getResponse().getStatus()).isEqualTo(403);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Surface.class)
    @DisplayName(
        "given_capabilityOnlyInDefaultTenantGroup_should_refuseTenantACopyOnThePrefixedRoute")
    void given_capabilityOnlyInDefaultTenantGroup_should_refuseTenantACopyOnThePrefixedRoute(
        Surface surface) throws Exception {
      // Arrange
      groupOf(Tenant.DEFAULT_TENANT_UUID, Set.of(Capability.ACCESS_THREAT_ARSENALS));

      // Act / Assert
      assertThat(getById(surface, Route.PREFIXED).getResponse().getStatus()).isEqualTo(403);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Surface.class)
    @DisplayName("given_grantInTenantAGroup_should_serveTenantACopyOnThePrefixedRoute")
    void given_grantInTenantAGroup_should_serveTenantACopyOnThePrefixedRoute(Surface surface)
        throws Exception {
      // Arrange
      grantOnContract(groupOf(tenantA, Set.of()));

      // Act
      MvcResult result = getById(surface, Route.PREFIXED);

      // Assert
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      String label =
          JsonPath.read(
              result.getResponse().getContentAsString(), "$." + surface.labelsField + ".en");
      assertThat(label).isEqualTo(labelOf(tenantA));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Surface.class)
    @DisplayName(
        "given_grantOnlyInTenantAGroup_should_refuseOnTheHeaderRouteWhoseAmbientTenantIsTheDefaultOne")
    void
        given_grantOnlyInTenantAGroup_should_refuseOnTheHeaderRouteWhoseAmbientTenantIsTheDefaultOne(
            Surface surface) throws Exception {
      // Arrange
      grantOnContract(groupOf(tenantA, Set.of()));

      // Act / Assert: the gate reads the caller's rights in the default tenant too
      assertThat(getById(surface, Route.HEADER).getResponse().getStatus()).isEqualTo(403);
    }
  }

  @Nested
  @DisplayName("Raw contract list")
  class RawList {

    private List<String> listedIds(Route route) throws Exception {
      String uri =
          route == Route.PREFIXED
              ? TENANT_INJECTOR_CONTRACT_URL.replace(
                  "{" + TenantUriUtils.TENANT_ID_PATH_VARIABLE + "}", tenantA)
              : INJECTOR_CONTRACT_URL;
      MvcResult result = mvc.perform(request(get(uri), route, tenantA)).andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      return JsonPath.read(result.getResponse().getContentAsString(), "$[*].injector_contract_id");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_capabilityOnlyInDefaultTenantGroup_should_listNoContractOfTenantA")
    void given_capabilityOnlyInDefaultTenantGroup_should_listNoContractOfTenantA(Route route)
        throws Exception {
      // Arrange
      groupOf(Tenant.DEFAULT_TENANT_UUID, Set.of(Capability.ACCESS_THREAT_ARSENALS));

      // Act / Assert
      assertThat(listedIds(route)).doesNotContain(contractId, ManualContract.MANUAL_DEFAULT);
    }

    @Test
    @DisplayName("given_capabilityOnlyInTenantAGroup_should_mergeThePatternsOfTheVisibleCopyOnly")
    void given_capabilityOnlyInTenantAGroup_should_mergeThePatternsOfTheVisibleCopyOnly()
        throws Exception {
      // Arrange: each copy carries its own attack pattern, the caller sees tenant A's copy only
      rights.attackPatternOf(contractId, Tenant.DEFAULT_TENANT_UUID);
      String visiblePattern = rights.attackPatternOf(contractId, tenantA);
      groupOf(tenantA, Set.of(Capability.ACCESS_THREAT_ARSENALS));

      // Act
      MvcResult result =
          mvc.perform(
                  request(
                      get(INJECTOR_CONTRACT_URL),
                      Route.HEADER,
                      Tenant.DEFAULT_TENANT_UUID + "," + tenantA))
              .andReturn();

      // Assert: one row for the id, with the patterns of tenant A's copy
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      String body = result.getResponse().getContentAsString();
      List<List<String>> patterns =
          JsonPath.read(
              body,
              "$[?(@.injector_contract_id == '"
                  + contractId
                  + "')].injector_contract_attack_patterns_external_id");
      assertThat(patterns).hasSize(1);
      assertThat(patterns.getFirst()).containsExactly(visiblePattern);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Route.class)
    @DisplayName("given_capabilityInTenantAGroup_should_listTheContractsOfTenantA")
    void given_capabilityInTenantAGroup_should_listTheContractsOfTenantA(Route route)
        throws Exception {
      // Arrange
      groupOf(tenantA, Set.of(Capability.ACCESS_THREAT_ARSENALS));

      // Act / Assert
      assertThat(listedIds(route)).contains(contractId, ManualContract.MANUAL_DEFAULT);
    }
  }

  @Nested
  @DisplayName("Threat arsenal CSV export on a scope of the default tenant and tenant A")
  class ExportOfBothTenants {

    private String bothTenants() {
      return Tenant.DEFAULT_TENANT_UUID + "," + tenantA;
    }

    /** The {@code name} column of the exported rows: the CSV holds no contract id. */
    private List<String> exportedNames(String scope) throws Exception {
      SearchPaginationInput input = new SearchPaginationInput();
      input.setSize(1000);
      input.setTextSearch(LABEL_PREFIX);
      MvcResult result =
          mvc.perform(
                  request(
                      post(MapperApi.MAPPER_URI
                              + "/export/csv?csvType="
                              + CsvType.INJECTOR_CONTRACTS)
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(asJsonString(input))
                          .with(csrf()),
                      Route.HEADER,
                      scope))
              .andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      String csv = result.getResponse().getContentAsString();
      if (csv.isBlank()) {
        return List.of();
      }
      try (CSVReader reader = new CSVReader(new StringReader(csv))) {
        List<String[]> lines = reader.readAll();
        int nameColumn = List.of(lines.getFirst()).indexOf("name");
        assertThat(nameColumn).as("name column of the export").isGreaterThanOrEqualTo(0);
        return lines.stream().skip(1).map(line -> line[nameColumn]).toList();
      }
    }

    @Test
    @DisplayName("given_grantOnlyInTenantAGroup_should_exportTenantACopyOnceAsTheSearchLists")
    void given_grantOnlyInTenantAGroup_should_exportTenantACopyOnceAsTheSearchLists()
        throws Exception {
      // Arrange: the default copy outranks A's but the caller cannot see it; a second contract has
      // no visible copy
      String seen = copiesOf("seen");
      copiesOf("hidden");
      rights.grantObserver(groupOf(tenantA, Set.of()), seen);
      testUserHolder.refreshSecurityContext();

      // Act
      List<String> exported = exportedNames(bothTenants());

      // Assert: the export lists the row the search lists
      assertSearchReturnsCopies(
          Surface.ARSENAL, bothTenants(), seen + "|" + copyLabel("seen", tenantA));
      assertThat(exported).containsExactly(copyLabel("seen", tenantA));
    }

    @Test
    @DisplayName("given_noVisibleCopy_should_exportNoRowAsTheSearchListsNone")
    void given_noVisibleCopy_should_exportNoRowAsTheSearchListsNone() throws Exception {
      // Arrange: two contracts with a copy in each tenant, none of them visible to the caller
      copiesOf("hidden-1");
      copiesOf("hidden-2");

      // Act
      List<String> exported = exportedNames(bothTenants());

      // Assert
      assertSearchReturnsCopies(Surface.ARSENAL, bothTenants());
      assertThat(exported).isEmpty();
    }

    @Test
    @WithMockUser(isAdmin = true)
    @DisplayName("given_platformAdministrator_should_exportTheRankedCopyOfEachContractOnce")
    void given_platformAdministrator_should_exportTheRankedCopyOfEachContractOnce()
        throws Exception {
      // Arrange
      String first = copiesOf("first");
      String second = copiesOf("second");

      // Act
      List<String> exported = exportedNames(bothTenants());

      // Assert: the default copy outranks A's for every contract id
      assertSearchReturnsCopies(
          Surface.ARSENAL,
          bothTenants(),
          contractId + "|" + labelOf(Tenant.DEFAULT_TENANT_UUID),
          first + "|" + copyLabel("first", Tenant.DEFAULT_TENANT_UUID),
          second + "|" + copyLabel("second", Tenant.DEFAULT_TENANT_UUID));
      assertThat(exported)
          .containsExactlyInAnyOrder(
              labelOf(Tenant.DEFAULT_TENANT_UUID),
              copyLabel("first", Tenant.DEFAULT_TENANT_UUID),
              copyLabel("second", Tenant.DEFAULT_TENANT_UUID));
    }
  }
}
