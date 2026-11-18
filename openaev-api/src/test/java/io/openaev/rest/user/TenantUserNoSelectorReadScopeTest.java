package io.openaev.rest.user;

import static io.openaev.rest.user.TenantUserApi.USER_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.api.users.dto.UserInput;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Organization;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.OrganizationRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.OrganizationFixture;
import io.openaev.utils.fixtures.UserFixture;
import io.openaev.utils.fixtures.composers.UserComposer;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The five handlers of {@code TenantUserApi} that read or update users carry
 * {@code @RequireTenantSelector}: find by id, find by ids, list, search and update. On the route
 * with no tenant in the URL and no {@code X-Tenant-Ids} header the annotation lets {@code
 * TxCtxArgumentResolver} pick the tenant from the caller's memberships, and which tenant's users
 * and organization come back then depends on who calls. {@link
 * TenantMembershipNoSelectorWriteScopeTest} pins the same four caller shapes for the writers of a
 * membership row; this class pins them for these handlers:
 *
 * <ul>
 *   <li>a member of one tenant, not the default one, is answered for that tenant;
 *   <li>a member of the default tenant and of another is answered for the default tenant;
 *   <li>a member of two tenants without the default one is refused with {@code
 *       TENANT_SELECTOR_REQUIRED}, nothing changed;
 *   <li>a caller with no membership reads the ambient (default) tenant, as before the scope
 *       existed, and cannot update: {@code TENANT_WRITE_SCOPE}, nothing changed.
 * </ul>
 *
 * <p>The target user belongs to every tenant of the scenario and holds an organization in each, so
 * an answer built for the wrong tenant shows as another organization or another list of users.
 * Dropping the annotation from one handler turns the second and third shapes of that handler into a
 * different outcome, so each handler is pinned on its own.
 */
@TestInstance(PER_CLASS)
@Transactional
@DisplayName("User reads and update with no tenant selector")
public class TenantUserNoSelectorReadScopeTest extends IntegrationTest {

  /** Production's active tables: the handlers read tables the inspector gates. */
  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private UserComposer userComposer;
  @Autowired private UserRepository userRepository;
  @Autowired private OrganizationRepository organizationRepository;

  /** What a scenario seeds, for the tenant the request is expected to answer for. */
  private record Scenario(
      String expectedTenant,
      User target,
      Map<String, Organization> organizationOfTenant,
      Organization replacementInExpectedTenant,
      User memberOnlyOfExpectedTenant,
      User memberOnlyElsewhere,
      boolean organizationVisible) {

    /**
     * The organization an answer shows: the one the target holds in the expected tenant. A caller
     * with no membership has an empty scope, where the gated organizations table reads nothing, so
     * the answer lists the users of the ambient tenant and shows no organization.
     */
    Matcher<Object> expectedOrganization() {
      return organizationVisible
          ? equalTo(organizationOfTenant.get(expectedTenant).getId())
          : nullValue();
    }

    /** The same, for a filter path, which yields a list of one element. */
    Matcher<Iterable<? extends Object>> expectedOrganizationInList() {
      return organizationVisible
          ? contains(organizationOfTenant.get(expectedTenant).getId())
          : contains(nullValue());
    }
  }

  /** The five handlers, each with the request it receives and what an answer must show. */
  private enum Handler {
    FIND_BY_ID {
      @Override
      MockHttpServletRequestBuilder request(Scenario s) {
        return get(USER_URI + "/" + s.target().getId()).accept(MediaType.APPLICATION_JSON);
      }

      @Override
      void expectAnswerFor(ResultActions result, Scenario s) throws Exception {
        result
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user_organization_id").value(s.expectedOrganization()));
      }
    },
    FIND {
      @Override
      MockHttpServletRequestBuilder request(Scenario s) {
        return post(USER_URI + "/find")
            .content(asJsonString(List.of(s.target().getId())))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf());
      }

      @Override
      void expectAnswerFor(ResultActions result, Scenario s) throws Exception {
        result
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].user_id").value(s.target().getId()))
            .andExpect(jsonPath("$[0].user_organization_id").value(s.expectedOrganization()));
      }
    },
    USERS {
      @Override
      MockHttpServletRequestBuilder request(Scenario s) {
        return get(USER_URI).accept(MediaType.APPLICATION_JSON);
      }

      @Override
      void expectAnswerFor(ResultActions result, Scenario s) throws Exception {
        result
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.user_id=='" + s.target().getId() + "')]").isNotEmpty())
            .andExpect(
                jsonPath("$[?(@.user_id=='" + s.memberOnlyOfExpectedTenant().getId() + "')]")
                    .isNotEmpty())
            .andExpect(
                jsonPath("$[?(@.user_id=='" + s.memberOnlyElsewhere().getId() + "')]").isEmpty());
      }
    },
    SEARCH {
      @Override
      MockHttpServletRequestBuilder request(Scenario s) {
        SearchPaginationInput search = new SearchPaginationInput();
        search.setSize(100);
        return post(USER_URI + "/search")
            .content(asJsonString(search))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf());
      }

      @Override
      void expectAnswerFor(ResultActions result, Scenario s) throws Exception {
        result
            .andExpect(status().isOk())
            .andExpect(
                jsonPath(
                        "$.content[?(@.user_id=='"
                            + s.target().getId()
                            + "')].user_organization_id")
                    .value(s.expectedOrganizationInList()))
            .andExpect(
                jsonPath(
                        "$.content[?(@.user_id=='" + s.memberOnlyOfExpectedTenant().getId() + "')]")
                    .isNotEmpty())
            .andExpect(
                jsonPath("$.content[?(@.user_id=='" + s.memberOnlyElsewhere().getId() + "')]")
                    .isEmpty());
      }
    },
    UPDATE {
      @Override
      MockHttpServletRequestBuilder request(Scenario s) {
        return put(USER_URI + "/" + s.target().getId())
            .content(
                asJsonString(
                    new UserInput(
                        s.target().getEmail(),
                        "NoSelector",
                        "Read",
                        UserFixture.RAW_PASSWORD,
                        null,
                        null,
                        null,
                        s.replacementInExpectedTenant() == null
                            ? null
                            : s.replacementInExpectedTenant().getId(),
                        List.of(),
                        false,
                        List.of())))
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .with(csrf());
      }

      @Override
      void expectAnswerFor(ResultActions result, Scenario s) throws Exception {
        result
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.user_organization_id").value(s.replacementInExpectedTenant().getId()));
      }
    };

    abstract MockHttpServletRequestBuilder request(Scenario s);

    abstract void expectAnswerFor(ResultActions result, Scenario s) throws Exception;
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  /**
   * Seeds the users and organizations of a scenario in every tenant it names, then reaches the
   * handler the way production does, with no thread-local tenant (the fixtures leave one behind).
   *
   * @param expectedTenant the tenant a request is expected to answer for
   * @param tenants every tenant of the scenario, the default one included
   * @param otherTenantForElsewhere a tenant different from the expected one, holding the user that
   *     must not show
   * @param organizationVisible whether the caller's scope lets the organization of the expected
   *     tenant be read
   */
  private Scenario seed(
      String expectedTenant,
      List<String> tenants,
      String otherTenantForElsewhere,
      boolean organizationVisible)
      throws Exception {
    Map<String, Organization> organizations = new LinkedHashMap<>();
    for (String tenantId : tenants) {
      organizations.put(tenantId, organizationIn(tenantId));
    }
    Organization replacement = expectedTenant == null ? null : organizationIn(expectedTenant);
    User target = memberOf("target", tenants);
    for (String tenantId : tenants) {
      User managed = userRepository.findById(target.getId()).orElseThrow();
      userRepository.assignOrganization(managed, tenantId, organizations.get(tenantId));
    }
    User here = memberOf("here", List.of(expectedTenant));
    User elsewhere = memberOf("elsewhere", List.of(otherTenantForElsewhere));
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();
    assertEquals(
        DEFAULT_TENANT,
        TenantContext.getCurrentTenant(),
        "The ambient tenant must be the default one for a no-selector request to prove anything");
    return new Scenario(
        expectedTenant, target, organizations, replacement, here, elsewhere, organizationVisible);
  }

  /**
   * A tenant the caller is not a member of: creating one attaches its creator, so it is detached.
   */
  private String tenantOfOthers(String name) throws Exception {
    String tenantId = tenantIsolationHelper.createTenant(name).getId();
    tenantIsolationHelper.detachCurrentUserFromTenant(tenantId);
    return tenantId;
  }

  private Organization organizationIn(String tenantId) {
    Organization organization = OrganizationFixture.createDefaultOrganisation();
    organization.setTenant(new Tenant(tenantId));
    return organizationRepository.save(organization);
  }

  private User memberOf(String label, List<String> tenantIds) {
    User user =
        userComposer
            .forUser(
                UserFixture.getUser(
                    label, "NoSelector", label + "-" + UUID.randomUUID() + "@filigran.io"))
            .persist()
            .get();
    tenantIds.forEach(tenantId -> tenantIsolationHelper.attachUserToTenant(user.getId(), tenantId));
    return user;
  }

  private List<String> organizationsOf(String userId) {
    entityManager.flush();
    @SuppressWarnings("unchecked")
    List<String> organizations =
        entityManager
            .createNativeQuery(
                "SELECT organization_id FROM users_organizations WHERE user_id = :userId"
                    + " ORDER BY organization_id")
            .setParameter("userId", userId)
            .getResultList();
    return organizations;
  }

  private static List<String> sortedIds(List<Organization> organizations) {
    return organizations.stream().map(Organization::getId).sorted().toList();
  }

  /** Organizations of every tenant but the expected one, plus the replacement for the expected. */
  private List<String> expectedOrganizationsAfterUpdate(Scenario s) {
    List<Organization> expected = new ArrayList<>();
    s.organizationOfTenant()
        .forEach(
            (tenantId, organization) -> {
              if (!tenantId.equals(s.expectedTenant())) {
                expected.add(organization);
              }
            });
    expected.add(s.replacementInExpectedTenant());
    return sortedIds(expected);
  }

  private void expectRefused(ResultActions result, String message) throws Exception {
    result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(message));
  }

  @Nested
  @DisplayName("Caller member of one tenant, not the default one")
  class SingleNonDefaultTenant {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Handler.class)
    @WithMockUser(isAdmin = true)
    @DisplayName("The answer is the one of the caller's own tenant")
    void given_singleTenantCaller_should_answerForItsOwnTenant(Handler handler) throws Exception {
      // -- PREPARE --
      String tenant = tenantIsolationHelper.createTenantWithCurrentUser("read-solo").getId();
      String extra = tenantOfOthers("read-extra");
      Scenario scenario =
          seed(tenant, List.of(DEFAULT_TENANT, tenant, extra), DEFAULT_TENANT, true);

      // -- EXECUTE --
      ResultActions result = mvc.perform(handler.request(scenario));

      // -- ASSERT --
      handler.expectAnswerFor(result, scenario);
      if (handler == Handler.UPDATE) {
        assertEquals(
            expectedOrganizationsAfterUpdate(scenario), organizationsOf(scenario.target().getId()));
      }
    }
  }

  @Nested
  @DisplayName("Caller member of the default tenant and of another")
  class DefaultAndAnotherTenant {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Handler.class)
    @WithMockUser(isAdmin = true)
    @DisplayName("The answer is the one of the default tenant")
    void given_callerOfDefaultAndB_should_answerForTheDefaultTenant(Handler handler)
        throws Exception {
      // -- PREPARE --
      tenantIsolationHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
      String tenantB = tenantIsolationHelper.createTenantWithCurrentUser("read-default-b").getId();
      Scenario scenario = seed(DEFAULT_TENANT, List.of(DEFAULT_TENANT, tenantB), tenantB, true);

      // -- EXECUTE --
      ResultActions result = mvc.perform(handler.request(scenario));

      // -- ASSERT --
      handler.expectAnswerFor(result, scenario);
      if (handler == Handler.UPDATE) {
        assertEquals(
            expectedOrganizationsAfterUpdate(scenario), organizationsOf(scenario.target().getId()));
      }
    }
  }

  @Nested
  @DisplayName("Caller member of two tenants, neither of them the default one")
  class TwoTenantsWithoutDefault {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Handler.class)
    @WithMockUser(isAdmin = true)
    @DisplayName("The request is refused with TENANT_SELECTOR_REQUIRED and nothing changes")
    void given_callerOfBAndC_should_refuseTheRequest(Handler handler) throws Exception {
      // -- PREPARE --
      String tenantB = tenantIsolationHelper.createTenantWithCurrentUser("read-b").getId();
      String tenantC = tenantIsolationHelper.createTenantWithCurrentUser("read-c").getId();
      Scenario scenario = seed(tenantB, List.of(DEFAULT_TENANT, tenantB, tenantC), tenantC, true);
      List<String> before = organizationsOf(scenario.target().getId());

      // -- EXECUTE --
      ResultActions result = mvc.perform(handler.request(scenario));

      // -- ASSERT --
      expectRefused(result, "TENANT_SELECTOR_REQUIRED");
      assertEquals(before, organizationsOf(scenario.target().getId()));
    }
  }

  @Nested
  @DisplayName("Caller with no tenant membership")
  class NoMembership {

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = Handler.class,
        names = {"FIND_BY_ID", "FIND", "USERS", "SEARCH"})
    @WithMockUser(isAdmin = true)
    @DisplayName("A read is answered for the ambient default tenant")
    void given_callerWithoutMembership_should_readTheDefaultTenant(Handler handler)
        throws Exception {
      // -- PREPARE --
      String extra = tenantOfOthers("read-extra");
      Scenario scenario = seed(DEFAULT_TENANT, List.of(DEFAULT_TENANT, extra), extra, false);

      // -- EXECUTE --
      ResultActions result = mvc.perform(handler.request(scenario));

      // -- ASSERT --
      handler.expectAnswerFor(result, scenario);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = Handler.class,
        names = {"UPDATE"})
    @WithMockUser(isAdmin = true)
    @DisplayName("An update is refused with TENANT_WRITE_SCOPE and nothing changes")
    void given_callerWithoutMembership_should_refuseTheUpdate(Handler handler) throws Exception {
      // -- PREPARE --
      String extra = tenantOfOthers("read-extra");
      Scenario scenario = seed(DEFAULT_TENANT, List.of(DEFAULT_TENANT, extra), extra, false);
      List<String> before = organizationsOf(scenario.target().getId());

      // -- EXECUTE --
      ResultActions result = mvc.perform(handler.request(scenario));

      // -- ASSERT --
      expectRefused(result, "TENANT_WRITE_SCOPE");
      assertEquals(before, organizationsOf(scenario.target().getId()));
    }
  }
}
