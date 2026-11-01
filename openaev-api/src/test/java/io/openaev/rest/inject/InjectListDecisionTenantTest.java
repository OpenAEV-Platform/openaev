package io.openaev.rest.inject;

import static io.openaev.rest.asset.endpoint.EndpointApi.ASSET_URI;
import static io.openaev.rest.asset.endpoint.EndpointApi.TENANT_ASSET_URI;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.TENANT_ATOMIC_TESTING_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.rest.inject.InjectApi.TENANT_INJECT_URI;
import static io.openaev.rest.organization.OrganizationApi.ORGANIZATION_URI;
import static io.openaev.rest.organization.OrganizationApi.TENANT_ORGANIZATION_URI;
import static io.openaev.rest.team.TeamApi.TEAM_URI;
import static io.openaev.rest.team.TeamApi.TENANT_TEAM_URI;
import static io.openaev.rest.user.PlayerApi.PLAYER_URI;
import static io.openaev.rest.user.PlayerApi.TENANT_PLAYER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.TenantDecisionScopeAssertions.assertDecidedIn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.clearInvocations;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Team;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.service.PermissionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.TeamFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.OrganizationComposer;
import io.openaev.utils.fixtures.composers.TeamComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The inject lists and exports decide in the tenant of each inject whether the caller reads every
 * inject or only those of the resources it holds a grant on.
 *
 * <p>The caller holds the rights {@code @WithMockUser} grants in the default tenant, and is a
 * member of tenant B, where it holds only what a test grants there. The requests name both tenants
 * in the {@code X-Tenant-Ids} header while the ambient tenant stays the default one: an inject of
 * the default tenant is read, an inject of B that the caller holds no grant on is not. The member
 * contrast grants the right in B and reads B's injects on B's tenant route.
 *
 * <p>Each request also pins the scope its decisions are taken on: {@code assertDecidedIn} reads the
 * tenants {@link PermissionService} received for every row decision of the request, the two of the
 * header route and the single one of B's tenant route. What a member reads is the weaker of the two
 * contrasts until {@code injects}, {@code exercises} and {@code scenarios} are activated: their v1
 * tenant filter and the native predicates on the ambient tenant keep B's injects out of a read made
 * in the default tenant, and the injects that stay visible are those of the ambient tenant, which
 * the per-tenant decision and a decision taken in the ambient tenant alone answer the same way. The
 * decision itself is pinned by {@code PermissionServiceTenantDecisionTest}, on rows of both
 * tenants.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("Inject list decisions hold in the tenant of the injects")
class InjectListDecisionTenantTest extends IntegrationTest {

  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables",
        () ->
            String.join(
                ",",
                Stream.concat(
                        ProductionActiveTables.names().stream(),
                        Stream.of("exercises", "scenarios", "injects"))
                    .distinct()
                    .toList()));
  }

  @Autowired private MockMvc mvc;
  @MockitoSpyBean private PermissionService permissionService;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private InjectComposer injectComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private OrganizationComposer organizationComposer;

  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    injectComposer.reset();
    endpointComposer.reset();
    teamComposer.reset();
    userComposer.reset();
    organizationComposer.reset();
    tenantHelper.attachCurrentUserToTenant(Tenant.DEFAULT_TENANT_UUID);
    tenantB = tenantHelper.createTenantWithCurrentUser("inject-list-decision-b").getId();
    clearInvocations(permissionService);
  }

  @Nested
  @DisplayName("Atomic testing search")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  class AtomicTestingSearch {

    @Test
    @DisplayName(
        "given the access right only outside B should search the default atomic testing only")
    void given_accessRightOnlyOutsideB_should_searchDefaultAtomicTestingOnly() throws Exception {
      // -- ARRANGE --
      String injectOfDefault = atomicTestingIn(Tenant.DEFAULT_TENANT_UUID, null);
      String injectOfB = atomicTestingIn(tenantB, null);

      // -- ACT & ASSERT --
      mvc.perform(inBothTenants(post(ATOMIC_TESTING_URI + "/search")).content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(injectOfDefault)).isNotEmpty())
          .andExpect(jsonPath(searched(injectOfB)).isEmpty());
      assertDecidedIn(permissionService, Tenant.DEFAULT_TENANT_UUID, tenantB);
    }

    @Test
    @DisplayName("given the access right in B should search B's atomic testing")
    void given_accessRightInB_should_searchBAtomicTesting() throws Exception {
      // -- ARRANGE --
      String injectOfB = atomicTestingIn(tenantB, null);
      grantInB(Capability.ACCESS_ASSESSMENT);

      // -- ACT & ASSERT --
      mvc.perform(inB(post(TENANT_ATOMIC_TESTING_URI + "/search", tenantB)).content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(injectOfB)).isNotEmpty());
      assertDecidedIn(permissionService, tenantB);
    }
  }

  @Nested
  @DisplayName("Injects played on an asset")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT, Capability.ACCESS_ASSETS})
  class AssetInjectSearch {

    @Test
    @DisplayName("given the access right only outside B should search the default inject only")
    void given_accessRightOnlyOutsideB_should_searchDefaultInjectOnly() throws Exception {
      // -- ARRANGE --
      Endpoint endpoint = endpointIn(Tenant.DEFAULT_TENANT_UUID);
      String injectOfDefault = atomicTestingIn(Tenant.DEFAULT_TENANT_UUID, endpoint);
      String injectOfB = atomicTestingIn(tenantB, endpoint);

      // -- ACT & ASSERT --
      mvc.perform(
              inBothTenants(post(ASSET_URI + "/" + endpoint.getId() + "/injects/search"))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(injectOfDefault)).isNotEmpty())
          .andExpect(jsonPath(searched(injectOfB)).isEmpty());
      assertDecidedIn(permissionService, Tenant.DEFAULT_TENANT_UUID, tenantB);
    }

    @Test
    @DisplayName("given the access right in B should search B's inject")
    void given_accessRightInB_should_searchBInject() throws Exception {
      // -- ARRANGE --
      Endpoint endpoint = endpointIn(tenantB);
      String injectOfB = atomicTestingIn(tenantB, endpoint);
      grantInB(Capability.ACCESS_ASSESSMENT, Capability.ACCESS_ASSETS);

      // -- ACT & ASSERT --
      mvc.perform(
              inB(post(TENANT_ASSET_URI + "/" + endpoint.getId() + "/injects/search", tenantB))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(injectOfB)).isNotEmpty());
      assertDecidedIn(permissionService, tenantB);
    }
  }

  @Nested
  @DisplayName("Inject export")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  class InjectExport {

    @Test
    @DisplayName("given the access right only outside B should not export B's inject by id")
    void given_accessRightOnlyOutsideB_should_notExportBInjectById() throws Exception {
      // -- ARRANGE --
      String injectOfB = atomicTestingIn(tenantB, null);

      // -- ACT & ASSERT --
      mvc.perform(inBothTenants(post(INJECT_URI + "/export")).content(exportById(injectOfB)))
          .andExpect(status().isNotFound());
      assertDecidedIn(permissionService, Tenant.DEFAULT_TENANT_UUID, tenantB);
    }

    @Test
    @DisplayName("given the access right only outside B should not export B's inject from a search")
    void given_accessRightOnlyOutsideB_should_notExportBInjectFromSearch() throws Exception {
      // -- ARRANGE --
      String injectOfB = atomicTestingIn(tenantB, null);

      // -- ACT & ASSERT --
      mvc.perform(
              inBothTenants(post(INJECT_URI + "/search/export"))
                  .content(exportFromSearch(injectOfB)))
          .andExpect(status().isNotFound());
      assertDecidedIn(permissionService, Tenant.DEFAULT_TENANT_UUID, tenantB);
    }

    @Test
    @DisplayName("given the access right only outside B should export the default inject")
    void given_accessRightOnlyOutsideB_should_exportDefaultInject() throws Exception {
      // -- ARRANGE --
      String injectOfDefault = atomicTestingIn(Tenant.DEFAULT_TENANT_UUID, null);

      // -- ACT & ASSERT --
      mvc.perform(inBothTenants(post(INJECT_URI + "/export")).content(exportById(injectOfDefault)))
          .andExpect(status().isOk());
      mvc.perform(
              inBothTenants(post(INJECT_URI + "/search/export"))
                  .content(exportFromSearch(injectOfDefault)))
          .andExpect(status().isOk());
      assertDecidedIn(permissionService, Tenant.DEFAULT_TENANT_UUID, tenantB);
    }

    @Test
    @DisplayName("given the access right in B should export B's inject")
    void given_accessRightInB_should_exportBInject() throws Exception {
      // -- ARRANGE --
      String injectOfB = atomicTestingIn(tenantB, null);
      grantInB(Capability.ACCESS_ASSESSMENT);

      // -- ACT & ASSERT --
      mvc.perform(inB(post(TENANT_INJECT_URI + "/export", tenantB)).content(exportById(injectOfB)))
          .andExpect(status().isOk());
      mvc.perform(
              inB(post(TENANT_INJECT_URI + "/search/export", tenantB))
                  .content(exportFromSearch(injectOfB)))
          .andExpect(status().isOk());
      assertDecidedIn(permissionService, tenantB);
    }
  }

  @Nested
  @DisplayName("Injects played on a team, a player and an organization")
  @WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
  class TeamPlayerAndOrganizationInjectSearch {

    @Test
    @DisplayName("given the access right only outside B should search the default team inject only")
    void given_accessRightOnlyOutsideB_should_searchDefaultTeamInjectOnly() throws Exception {
      // -- ARRANGE --
      TeamTarget ofDefault = targetedTeamIn(Tenant.DEFAULT_TENANT_UUID);
      TeamTarget ofB = targetedTeamIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(
              inBothTenants(post(TEAM_URI + "/{teamId}/injects/search", ofDefault.teamId()))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(ofDefault.injectId())).isNotEmpty());
      mvc.perform(
              inBothTenants(post(TEAM_URI + "/{teamId}/injects/search", ofB.teamId()))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(ofB.injectId())).isEmpty());
      assertDecidedIn(permissionService, Tenant.DEFAULT_TENANT_UUID, tenantB);
    }

    @Test
    @DisplayName(
        "given the access right only outside B should search the default player inject only")
    void given_accessRightOnlyOutsideB_should_searchDefaultPlayerInjectOnly() throws Exception {
      // -- ARRANGE --
      TeamTarget ofDefault = targetedTeamIn(Tenant.DEFAULT_TENANT_UUID);
      TeamTarget ofB = targetedTeamIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(
              inBothTenants(post(PLAYER_URI + "/{userId}/injects/search", ofDefault.playerId()))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(ofDefault.injectId())).isNotEmpty());
      mvc.perform(
              inBothTenants(post(PLAYER_URI + "/{userId}/injects/search", ofB.playerId()))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(ofB.injectId())).isEmpty());
      assertDecidedIn(permissionService, Tenant.DEFAULT_TENANT_UUID, tenantB);
    }

    @Test
    @DisplayName(
        "given the access right only outside B should search the default organization inject only")
    void given_accessRightOnlyOutsideB_should_searchDefaultOrganizationInjectOnly()
        throws Exception {
      // -- ARRANGE --
      TeamTarget ofDefault = targetedTeamIn(Tenant.DEFAULT_TENANT_UUID);
      TeamTarget ofB = targetedTeamIn(tenantB);

      // -- ACT & ASSERT --
      mvc.perform(
              inBothTenants(
                      post(
                          ORGANIZATION_URI + "/{organizationId}/injects/search",
                          ofDefault.organizationId()))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(ofDefault.injectId())).isNotEmpty());
      mvc.perform(
              inBothTenants(
                      post(
                          ORGANIZATION_URI + "/{organizationId}/injects/search",
                          ofB.organizationId()))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(ofB.injectId())).isEmpty());
      assertDecidedIn(permissionService, Tenant.DEFAULT_TENANT_UUID, tenantB);
    }

    @Test
    @DisplayName("given the access right in B should search B's team inject")
    void given_accessRightInB_should_searchBTeamInject() throws Exception {
      // -- ARRANGE --
      TeamTarget ofB = targetedTeamIn(tenantB);
      grantInB(Capability.ACCESS_ASSESSMENT);

      // -- ACT & ASSERT --
      mvc.perform(
              inB(post(TENANT_TEAM_URI + "/{teamId}/injects/search", tenantB, ofB.teamId()))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(ofB.injectId())).isNotEmpty());
      assertDecidedIn(permissionService, tenantB);
    }

    @Test
    @DisplayName("given the access right in B should search B's player inject")
    void given_accessRightInB_should_searchBPlayerInject() throws Exception {
      // -- ARRANGE --
      TeamTarget ofB = targetedTeamIn(tenantB);
      grantInB(Capability.ACCESS_ASSESSMENT);

      // -- ACT & ASSERT --
      mvc.perform(
              inB(post(TENANT_PLAYER_URI + "/{userId}/injects/search", tenantB, ofB.playerId()))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(ofB.injectId())).isNotEmpty());
      assertDecidedIn(permissionService, tenantB);
    }

    @Test
    @DisplayName("given the access right in B should search B's organization inject")
    void given_accessRightInB_should_searchBOrganizationInject() throws Exception {
      // -- ARRANGE --
      TeamTarget ofB = targetedTeamIn(tenantB);
      grantInB(Capability.ACCESS_ASSESSMENT);

      // -- ACT & ASSERT --
      mvc.perform(
              inB(post(
                      TENANT_ORGANIZATION_URI + "/{organizationId}/injects/search",
                      tenantB,
                      ofB.organizationId()))
                  .content(largeSearch()))
          .andExpect(status().isOk())
          .andExpect(jsonPath(searched(ofB.injectId())).isNotEmpty());
      assertDecidedIn(permissionService, tenantB);
    }
  }

  // -- HELPERS --

  /** An inject with no scenario and no simulation, targeting {@code endpoint} when given. */
  private String atomicTestingIn(String tenantId, Endpoint endpoint) {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTenant(new Tenant(tenantId));
    if (endpoint != null) {
      inject.setAssets(new ArrayList<>(List.<Asset>of(endpoint)));
    }
    String id = injectComposer.forInject(inject).persist().get().getId();
    entityManager.flush();
    return id;
  }

  private Endpoint endpointIn(String tenantId) {
    Endpoint endpoint = EndpointFixture.createEndpoint();
    endpoint.setTenant(new Tenant(tenantId));
    Endpoint persisted = endpointComposer.forEndpoint(endpoint).persist().get();
    entityManager.flush();
    return persisted;
  }

  /** A team, the organization and the player it links, and the inject that targets the team. */
  private record TeamTarget(
      String teamId, String organizationId, String playerId, String injectId) {}

  /**
   * A team of {@code tenantId} with an organization and a player of its own, targeted by one inject
   * of the same tenant. The three target endpoints reach that inject through this team.
   */
  private TeamTarget targetedTeamIn(String tenantId) {
    Tenant tenant = new Tenant(tenantId);
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setTenant(tenant);
    organizationComposer.forOrganization(organization).persist();
    User player = UserFixture.getUserWithDefaultEmail();
    userComposer.forUser(player).persist();
    Team team = TeamFixture.getDefaultTeam();
    team.setTenant(tenant);
    team.setOrganization(organization);
    team.setUsers(new ArrayList<>(List.of(player)));
    teamComposer.forTeam(team).persist();
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTenant(tenant);
    inject.setTeams(new ArrayList<>(List.of(team)));
    String injectId = injectComposer.forInject(inject).persist().get().getId();
    entityManager.flush();
    return new TeamTarget(team.getId(), organization.getId(), player.getId(), injectId);
  }

  private static String searched(String injectId) {
    return "$.content[?(@.inject_id == '" + injectId + "')]";
  }

  private static String exportById(String injectId) {
    return asJsonString(Map.of("injects", List.of(Map.of("inject_id", injectId))));
  }

  private static String exportFromSearch(String injectId) {
    return asJsonString(Map.of("inject_ids_to_process", List.of(injectId)));
  }

  /**
   * The non-prefixed route naming the default tenant and B. The ambient tenant is cleared first:
   * fixtures leave one on the test thread, and the request must see production's fallback to the
   * default tenant.
   */
  private MockHttpServletRequestBuilder inBothTenants(MockHttpServletRequestBuilder request) {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return request
        .header(TENANT_IDS_HEADER, Tenant.DEFAULT_TENANT_UUID + "," + tenantB)
        .contentType(MediaType.APPLICATION_JSON)
        .with(csrf());
  }

  /** B's tenant route. */
  private MockHttpServletRequestBuilder inB(MockHttpServletRequestBuilder request) {
    TenantContext.clearCurrentTenant();
    return request.contentType(MediaType.APPLICATION_JSON).with(csrf());
  }

  private void grantInB(Capability... capabilities) {
    tenantHelper.grantCapabilitiesInTenant(tenantB, Set.of(capabilities));
  }

  private String largeSearch() {
    return asJsonString(PaginationFixture.getDefault().size(100).textSearch("").build());
  }
}
