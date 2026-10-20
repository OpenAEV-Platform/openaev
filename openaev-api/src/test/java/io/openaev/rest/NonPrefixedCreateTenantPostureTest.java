package io.openaev.rest;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CustomDashboard;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CustomDashboardRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.CustomDashboardFixture;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins the tenant of a simulation or scenario created without a tenant in the path, through the
 * three creates: simulation, scenario, and scenario from injector contracts. The row takes the
 * single tenant of the request scope, whether or not the body carries a custom dashboard, and a
 * scope that does not pin exactly one tenant is refused before anything is written. The injects
 * created with a scenario from injector contracts follow their scenario. The prefixed route is the
 * control.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Simulation and scenario creates take their tenant from the request scope")
class NonPrefixedCreateTenantPostureTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String NAME = "posture-create";
  private static final String WITH_INJECTOR_CONTRACTS = "/with-injector-contracts";
  // Matches no contract: the create from injector contracts then writes the scenario alone.
  private static final String UNKNOWN_CONTRACT_ID = "00000000-0000-0000-0000-000000000000";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private CustomDashboardRepository customDashboardRepository;
  @Autowired private InjectorContractComposer injectorContractComposer;

  enum Create {
    SIMULATION(
        EXERCISE_URI,
        TENANT_EXERCISE_URI,
        EXERCISE_URI,
        TENANT_EXERCISE_URI,
        "exercises",
        "exercise_id",
        "exercise_name",
        "exercise_custom_dashboard",
        false),
    SCENARIO(
        SCENARIO_URI,
        TENANT_SCENARIO_URI,
        SCENARIO_URI,
        TENANT_SCENARIO_URI,
        "scenarios",
        "scenario_id",
        "scenario_name",
        "scenario_custom_dashboard",
        false),
    SCENARIO_WITH_INJECTOR_CONTRACTS(
        SCENARIO_URI + WITH_INJECTOR_CONTRACTS,
        TENANT_SCENARIO_URI + WITH_INJECTOR_CONTRACTS,
        SCENARIO_URI,
        TENANT_SCENARIO_URI,
        "scenarios",
        "scenario_id",
        "scenario_name",
        "scenario_custom_dashboard",
        true);

    final String uri;
    final String tenantUri;
    final String readUri;
    final String tenantReadUri;
    final String table;
    final String idColumn;
    final String nameField;
    final String dashboardField;
    final boolean withInjectorContracts;

    Create(
        String uri,
        String tenantUri,
        String readUri,
        String tenantReadUri,
        String table,
        String idColumn,
        String nameField,
        String dashboardField,
        boolean withInjectorContracts) {
      this.uri = uri;
      this.tenantUri = tenantUri;
      this.readUri = readUri;
      this.tenantReadUri = tenantReadUri;
      this.table = table;
      this.idColumn = idColumn;
      this.nameField = nameField;
      this.dashboardField = dashboardField;
      this.withInjectorContracts = withInjectorContracts;
    }

    String body() {
      return wrap("{\"" + nameField + "\":\"" + NAME + "\"}", UNKNOWN_CONTRACT_ID);
    }

    String bodyWithDashboard(String dashboardId) {
      return wrap(
          "{\""
              + nameField
              + "\":\""
              + NAME
              + "\",\""
              + dashboardField
              + "\":\""
              + dashboardId
              + "\"}",
          UNKNOWN_CONTRACT_ID);
    }

    /** The create from injector contracts nests the scenario input next to the contract search. */
    String wrap(String input, String contractId) {
      if (!withInjectorContracts) {
        return input;
      }
      return "{\"locale\":\"en\",\"scenario_input\":"
          + input
          + ",\"injector_contract_search_pagination_input\":"
          + "{\"injector_contract_ids_to_process\":[\""
          + contractId
          + "\"]}}";
    }
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Caller whose only tenant is the default one")
  class DefaultTenantOnlyCaller {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("Without a custom dashboard, the row is written in the default tenant")
    void given_defaultTenantOnlyCaller_should_writeInDefaultTenant(Create create) throws Exception {
      // Arrange
      tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);

      // Act
      String id = createdId(create, nonPrefixed(create, create.body()));

      // Assert
      assertEquals(DEFAULT_TENANT, rowTenant(create, id));
    }
  }

  @Nested
  @DisplayName("Caller whose only tenant is not the default one")
  class SingleNonDefaultTenantCaller {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("Without a custom dashboard, the row is written in the caller's tenant")
    void given_singleNonDefaultTenantCaller_should_writeInCallerTenant(Create create)
        throws Exception {
      // Arrange
      String tenantB = tenantHelper.createTenantWithCurrentUser("posture-only-b").getId();

      // Act
      String id = createdId(create, nonPrefixed(create, create.body()));

      // Assert
      assertEquals(tenantB, rowTenant(create, id));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("With a custom dashboard, the row is written in the caller's tenant")
    void given_singleNonDefaultTenantCallerWithDashboard_should_writeInCallerTenant(Create create)
        throws Exception {
      // Arrange
      String tenantB = tenantHelper.createTenantWithCurrentUser("posture-only-b").getId();
      String dashboardId = dashboardIn(tenantB);

      // Act
      String id = createdId(create, nonPrefixed(create, create.bodyWithDashboard(dashboardId)));

      // Assert
      assertEquals(tenantB, rowTenant(create, id));
    }
  }

  @Nested
  @DisplayName("Caller in the default tenant and one other, header naming the other")
  class HeaderNamingOneTenant {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("Without a custom dashboard, the row is written in the tenant the header names")
    void given_headerNamingOneTenant_should_writeInThatTenant(Create create) throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();

      // Act
      String id =
          createdId(create, nonPrefixed(create, create.body()).header(TENANT_IDS_HEADER, tenantB));

      // Assert
      assertEquals(tenantB, rowTenant(create, id));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("With a custom dashboard, the row is written in the tenant the header names")
    void given_headerNamingOneTenantWithDashboard_should_writeInThatTenant(Create create)
        throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();
      String dashboardId = dashboardIn(tenantB);

      // Act
      String id =
          createdId(
              create,
              nonPrefixed(create, create.bodyWithDashboard(dashboardId))
                  .header(TENANT_IDS_HEADER, tenantB));

      // Assert
      assertEquals(tenantB, rowTenant(create, id));
    }
  }

  @Nested
  @DisplayName("Caller in the default tenant and one other, scope not narrowed to one")
  class MultiTenantCaller {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("Without a header or a custom dashboard, the create is refused")
    void given_multiTenantCallerWithoutDashboard_should_refuseCreate(Create create)
        throws Exception {
      // Arrange
      joinDefaultAndOtherTenant();

      // Act & Assert
      assertRefused(create, nonPrefixed(create, create.body()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("Without a header but with a custom dashboard, the create is refused")
    void given_multiTenantCallerWithDashboard_should_refuseCreate(Create create) throws Exception {
      // Arrange
      joinDefaultAndOtherTenant();
      String dashboardId = dashboardIn(DEFAULT_TENANT);

      // Act & Assert
      assertRefused(create, nonPrefixed(create, create.bodyWithDashboard(dashboardId)));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("With a header naming both tenants, the create is refused")
    void given_headerNamingTwoTenants_should_refuseCreate(Create create) throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();

      // Act & Assert
      assertRefused(
          create,
          nonPrefixed(create, create.body())
              .header(TENANT_IDS_HEADER, DEFAULT_TENANT + "," + tenantB));
    }
  }

  @Nested
  @DisplayName("Caller with no tenant membership")
  class NoMembershipCaller {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("Without a custom dashboard, the create is refused")
    void given_noMembershipWithoutDashboard_should_refuseCreate(Create create) throws Exception {
      // Act & Assert
      assertRefused(create, nonPrefixed(create, create.body()));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("With a custom dashboard, the create is refused")
    void given_noMembershipWithDashboard_should_refuseCreate(Create create) throws Exception {
      // Arrange
      String dashboardId = dashboardIn(DEFAULT_TENANT);

      // Act & Assert
      assertRefused(create, nonPrefixed(create, create.bodyWithDashboard(dashboardId)));
    }
  }

  @Nested
  @DisplayName("Prefixed route, the control")
  class PrefixedRoute {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("The row is written in the tenant of the path")
    void given_prefixedRoute_should_writeInPathTenant(Create create) throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();
      TenantContext.clearCurrentTenant();

      // Act
      String id =
          createdId(
              create,
              post(create.tenantUri, tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(create.body())
                  .with(csrf()));

      // Assert
      assertEquals(tenantB, rowTenant(create, id));
    }
  }

  @Nested
  @DisplayName("Lifecycle of a row created on the non-prefixed route with a header naming B")
  class LifecycleOfHeaderRouteCreate {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("The row is readable by id on the prefixed route of its tenant")
    void given_headerRouteCreate_should_beReadableOnItsTenantRoute(Create create) throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();
      String id = createInHeaderRoute(create, tenantB);

      // Act & Assert
      mvc.perform(get(create.tenantReadUri + "/{id}", tenantB, id)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("The row is not readable by id from the default tenant")
    void given_headerRouteCreate_should_notBeReadableFromDefaultTenant(Create create)
        throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();
      String id = createInHeaderRoute(create, tenantB);

      // Act & Assert
      mvc.perform(get(create.tenantReadUri + "/{id}", DEFAULT_TENANT, id))
          .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName(
        "Until the table is activated, the row is not readable by id back on the header route")
    void given_headerRouteCreate_should_notBeReadableBackOnHeaderRoute(Create create)
        throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();
      String id = createInHeaderRoute(create, tenantB);
      TenantContext.clearCurrentTenant();

      // Act & Assert
      mvc.perform(get(create.readUri + "/{id}", id).header(TENANT_IDS_HEADER, tenantB))
          .andExpect(status().isNotFound());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("Until the table is activated, the row is not deletable on the header route")
    void given_headerRouteCreate_should_notBeDeletableOnHeaderRoute(Create create)
        throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();
      String id = createInHeaderRoute(create, tenantB);
      TenantContext.clearCurrentTenant();

      // Act
      mvc.perform(
              delete(create.readUri + "/{id}", id).header(TENANT_IDS_HEADER, tenantB).with(csrf()))
          .andExpect(status().isNotFound());

      // Assert
      assertEquals(tenantB, rowTenant(create, id), "the row is still there, in B");
    }

    private String createInHeaderRoute(Create create, String tenantB) throws Exception {
      String id =
          createdId(create, nonPrefixed(create, create.body()).header(TENANT_IDS_HEADER, tenantB));
      entityManager.flush();
      TenantContext.clearCurrentTenant();
      return id;
    }
  }

  @Nested
  @DisplayName("Injects created with a scenario from injector contracts")
  class InjectsOfScenarioFromInjectorContracts {

    private final Create create = Create.SCENARIO_WITH_INJECTOR_CONTRACTS;

    @Test
    @DisplayName("On the header route, the injects are written in the tenant of their scenario")
    void given_headerNamingOneTenant_should_writeInjectsInScenarioTenant() throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();
      String contractId = contractIn(tenantB);

      // Act
      String id =
          createdId(
              create,
              nonPrefixed(create, create.wrap(scenarioInput(), contractId))
                  .header(TENANT_IDS_HEADER, tenantB));

      // Assert
      assertEquals(tenantB, rowTenant(create, id));
      assertEquals(List.of(tenantB), injectTenants(id), "every inject is in the scenario tenant");
    }

    @Test
    @DisplayName("After a header-route create, the injects are listed on the route of their tenant")
    void given_headerRouteCreate_should_listInjectsOnItsTenantRoute() throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();
      String contractId = contractIn(tenantB);
      String id =
          createdId(
              create,
              nonPrefixed(create, create.wrap(scenarioInput(), contractId))
                  .header(TENANT_IDS_HEADER, tenantB));
      entityManager.flush();
      entityManager.clear();
      TenantContext.clearCurrentTenant();

      // Act & Assert
      mvc.perform(get(create.tenantReadUri + "/{id}/injects/simple", tenantB, id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("On the header route, a contract of a tenant outside the scope yields no inject")
    void given_headerNamingOneTenantAndContractOutsideScope_should_writeNoInject()
        throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();
      String contractId = contractIn(DEFAULT_TENANT);

      // Act
      String id =
          createdId(
              create,
              nonPrefixed(create, create.wrap(scenarioInput(), contractId))
                  .header(TENANT_IDS_HEADER, tenantB));

      // Assert
      assertEquals(tenantB, rowTenant(create, id));
      assertEquals(List.of(), injectTenants(id), "the default tenant's contract is not in scope");
    }

    @Test
    @DisplayName("On the prefixed route, the injects are written in the tenant of the path")
    void given_prefixedRoute_should_writeInjectsInPathTenant() throws Exception {
      // Arrange
      String tenantB = joinDefaultAndOtherTenant();
      String contractId = contractIn(tenantB);
      TenantContext.clearCurrentTenant();

      // Act
      String id =
          createdId(
              create,
              post(create.tenantUri, tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(create.wrap(scenarioInput(), contractId))
                  .with(csrf()));

      // Assert
      assertEquals(tenantB, rowTenant(create, id));
      assertEquals(List.of(tenantB), injectTenants(id), "every inject is in the path tenant");
    }

    private String scenarioInput() {
      return "{\"" + create.nameField + "\":\"" + NAME + "\"}";
    }

    private List<String> injectTenants(String scenarioId) {
      return entityManager
          .createNativeQuery(
              "SELECT DISTINCT tenant_id FROM injects WHERE inject_scenario = ?1", String.class)
          .setParameter(1, scenarioId)
          .getResultList();
    }
  }

  private String contractIn(String tenantId) {
    // The contract and its injector take the ambient tenant at build and persist time.
    TenantContext.setCurrentTenant(tenantId);
    try {
      InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
      contract.setLabels(Map.of("en", "posture contract"));
      String id = injectorContractComposer.forInjectorContract(contract).persist().get().getId();
      // Written here, not inside the request: the fixture's link rows belong to the fixture.
      entityManager.flush();
      return id;
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private String joinDefaultAndOtherTenant() throws Exception {
    String tenantB = tenantHelper.createTenantWithCurrentUser("posture-b").getId();
    tenantHelper.attachCurrentUserToTenant(DEFAULT_TENANT);
    return tenantB;
  }

  private String dashboardIn(String tenantId) {
    CustomDashboard dashboard = CustomDashboardFixture.createDefaultCustomDashboard();
    dashboard.setTenant(new Tenant(tenantId));
    return customDashboardRepository.save(dashboard).getId();
  }

  private MockHttpServletRequestBuilder nonPrefixed(Create create, String body) {
    // The non-prefixed route never sets the ambient tenant: reproduce production's empty
    // thread-local instead of inheriting what the fixtures left on the test thread.
    TenantContext.clearCurrentTenant();
    assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");
    return post(create.uri).contentType(MediaType.APPLICATION_JSON).content(body).with(csrf());
  }

  private String createdId(Create create, MockHttpServletRequestBuilder request) throws Exception {
    String response =
        mvc.perform(request)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$." + create.idColumn);
  }

  private void assertRefused(Create create, MockHttpServletRequestBuilder request)
      throws Exception {
    mvc.perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("TENANT_WRITE_SCOPE"));
    assertEquals(0L, rowCount(create), "a refused create writes no row");
  }

  private String rowTenant(Create create, String id) {
    return (String)
        entityManager
            .createNativeQuery(
                "SELECT tenant_id FROM " + create.table + " WHERE " + create.idColumn + " = ?1")
            .setParameter(1, id)
            .getSingleResult();
  }

  private long rowCount(Create create) {
    return ((Number)
            entityManager
                .createNativeQuery(
                    "SELECT count(*) FROM " + create.table + " WHERE " + create.nameField + " = ?1")
                .setParameter(1, NAME)
                .getSingleResult())
        .longValue();
  }
}
