package io.openaev.rest.search;

import static io.openaev.search.FullTextSearchApi.GLOBAL_SEARCH_URI;
import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.search.FullTextSearchApi;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PaginationFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.TenantGroupFixture;
import io.openaev.utils.fixtures.TenantRoleFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.TenantGroupComposer;
import io.openaev.utils.fixtures.composers.TenantRoleComposer;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The full text search on the non-prefixed route, under the production list of isolated tables. The
 * caller holds the scenario capability through a role of the default tenant and is also a member of
 * a second tenant. With {@code scenarios} isolated, the scenarios a search reads are those of the
 * tenants of the request's scope: the header naming the default tenant reads the default tenant's
 * scenario, the header naming the second tenant never does.
 */
@Transactional
@TestInstance(PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Full text search on the non-prefixed route, scenarios isolated")
class FullTextSearchHeaderRouteTest extends ProductionTablesIntegrationTest {

  private static final String TENANT_HEADER = "X-Tenant-Ids";
  // A word no other fixture uses: the search matches by prefix on each term.
  private static final String SEARCH_TERM = "Quillwortheader";

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private UserComposer userComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private TenantRoleComposer tenantRoleComposer;
  @Autowired private TenantIsolationTestHelper tenantIsolationTestHelper;

  private Scenario defaultTenantScenario;
  private String otherTenantId;
  private User caller;

  @BeforeEach
  void setUp() throws Exception {
    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
    scenario.setName(SEARCH_TERM + " crisis");
    scenario.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    defaultTenantScenario = scenarioRepository.save(scenario);
    otherTenantId = tenantIsolationTestHelper.createTenant("search-header").getId();
    caller = defaultTenantMemberAlsoIn(otherTenantId);
  }

  @AfterEach
  void tearDown() {
    userComposer.reset();
    tenantGroupComposer.reset();
    tenantRoleComposer.reset();
  }

  /**
   * A caller holding the scenario capability through a role of the default tenant, a member of that
   * tenant and of {@code otherTenantId}.
   */
  private User defaultTenantMemberAlsoIn(String otherTenantId) {
    TenantGroupComposer.Composer group =
        tenantGroupComposer
            .forGroup(TenantGroupFixture.getGroup())
            .withRole(
                tenantRoleComposer.forRole(
                    TenantRoleFixture.getRole(
                        new HashSet<>(Set.of(Capability.ACCESS_ASSESSMENT)))));
    User user =
        userComposer
            .forUser(
                UserFixture.getUser(
                    "Firstname", "Lastname", UUID.randomUUID() + "@unittests.invalid"))
            .withGroup(group)
            .persist()
            .get();
    tenantIsolationTestHelper.attachUserToTenant(user.getId(), Tenant.DEFAULT_TENANT_UUID);
    tenantIsolationTestHelper.attachUserToTenant(user.getId(), otherTenantId);
    return user;
  }

  private ResultActions count(String headerTenantId) throws Exception {
    FullTextSearchApi.SearchTerm term = new FullTextSearchApi.SearchTerm();
    term.setSearchTerm(SEARCH_TERM);
    return mvc.perform(
            post(GLOBAL_SEARCH_URI)
                .header(TENANT_HEADER, headerTenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(term))
                .with(authentication(buildAuthenticationToken(caller)))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  private ResultActions results(String headerTenantId) throws Exception {
    SearchPaginationInput input = PaginationFixture.getDefault().textSearch(SEARCH_TERM).build();
    return mvc.perform(
            post(GLOBAL_SEARCH_URI + "/" + Scenario.class.getName())
                .header(TENANT_HEADER, headerTenantId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(asJsonString(input))
                .with(authentication(buildAuthenticationToken(caller)))
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  @Nested
  @DisplayName("Header naming the default tenant")
  class HeaderNamingTheDefaultTenant {

    @Test
    @DisplayName("given the capability in the default tenant, should count its scenario")
    void given_theCapabilityInTheDefaultTenant_should_countItsScenario() throws Exception {
      // Act
      ResultActions result = count(Tenant.DEFAULT_TENANT_UUID);

      // Assert
      result.andExpect(jsonPath("$['" + Scenario.class.getName() + "'].count").value(1));
    }

    @Test
    @DisplayName("given the capability in the default tenant, should return its scenario")
    void given_theCapabilityInTheDefaultTenant_should_returnItsScenario() throws Exception {
      // Act
      ResultActions result = results(Tenant.DEFAULT_TENANT_UUID);

      // Assert
      result.andExpect(jsonPath("$.content[*].id", hasItem(defaultTenantScenario.getId())));
    }
  }

  @Nested
  @DisplayName("Header naming another tenant")
  class HeaderNamingAnotherTenant {

    @Test
    @DisplayName("given a scope without the default tenant, should not count its scenario")
    void given_aScopeWithoutTheDefaultTenant_should_notCountItsScenario() throws Exception {
      // Act
      ResultActions result = count(otherTenantId);

      // Assert
      result.andExpect(jsonPath("$['" + Scenario.class.getName() + "'].count").value(0));
    }

    @Test
    @DisplayName("given a scope without the default tenant, should not return its scenario")
    void given_aScopeWithoutTheDefaultTenant_should_notReturnItsScenario() throws Exception {
      // Act
      ResultActions result = results(otherTenantId);

      // Assert
      result.andExpect(jsonPath("$.content[*].id", not(hasItem(defaultTenantScenario.getId()))));
    }
  }
}
