package io.openaev.rest;

import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pins that a simulation or scenario create never writes a row in a tenant where the caller lacks
 * the create capability, whichever route names that tenant: the path, or the {@code X-Tenant-Ids}
 * header of the non-prefixed route. The caller is not an admin and holds the create capability in
 * one tenant and only read access in the other.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser
@DisplayName("Simulation and scenario creates are authorized in the tenant they write in")
class NonPrefixedCreateCapabilityTenantTest extends IntegrationTest {

  private static final String DEFAULT_TENANT = Tenant.DEFAULT_TENANT_UUID;
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String NAME = "capability-tenant-create";
  private static final String WITH_INJECTOR_CONTRACTS = "/with-injector-contracts";
  // Matches no contract: the create from injector contracts then writes the scenario alone.
  private static final String UNKNOWN_CONTRACT_ID = "00000000-0000-0000-0000-000000000000";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  enum Create {
    SIMULATION(EXERCISE_URI, TENANT_EXERCISE_URI, "exercises", "exercise_name", false),
    SCENARIO(SCENARIO_URI, TENANT_SCENARIO_URI, "scenarios", "scenario_name", false),
    SCENARIO_WITH_INJECTOR_CONTRACTS(
        SCENARIO_URI + WITH_INJECTOR_CONTRACTS,
        TENANT_SCENARIO_URI + WITH_INJECTOR_CONTRACTS,
        "scenarios",
        "scenario_name",
        true);

    final String uri;
    final String tenantUri;
    final String table;
    final String nameField;
    final boolean withInjectorContracts;

    Create(
        String uri,
        String tenantUri,
        String table,
        String nameField,
        boolean withInjectorContracts) {
      this.uri = uri;
      this.tenantUri = tenantUri;
      this.table = table;
      this.nameField = nameField;
      this.withInjectorContracts = withInjectorContracts;
    }

    String body() {
      String input = "{\"" + nameField + "\":\"" + NAME + "\"}";
      if (!withInjectorContracts) {
        return input;
      }
      return "{\"locale\":\"en\",\"scenario_input\":"
          + input
          + ",\"injector_contract_search_pagination_input\":"
          + "{\"injector_contract_ids_to_process\":[\""
          + UNKNOWN_CONTRACT_ID
          + "\"]}}";
    }
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Create capability in the default tenant, read only in tenant B")
  class CreateCapabilityInDefaultTenantOnly {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("On the non-prefixed route with a header naming B, the create is refused")
    void given_headerNamingTenantWithoutCreateCapability_should_refuseCreate(Create create)
        throws Exception {
      // Arrange
      String tenantB = createTenantWithReadOnly(DEFAULT_TENANT);

      // Act
      int status = perform(nonPrefixed(create).header(TENANT_IDS_HEADER, tenantB));

      // Assert
      assertRefusedWithoutRow(create, status);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("On the prefixed route of B, the create is refused")
    void given_pathNamingTenantWithoutCreateCapability_should_refuseCreate(Create create)
        throws Exception {
      // Arrange
      String tenantB = createTenantWithReadOnly(DEFAULT_TENANT);

      // Act
      int status = perform(prefixed(create, tenantB));

      // Assert
      assertRefusedWithoutRow(create, status);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("On the non-prefixed route with a header naming the default tenant, it is written")
    void given_headerNamingTenantWithCreateCapability_should_writeInThatTenant(Create create)
        throws Exception {
      // Arrange
      createTenantWithReadOnly(DEFAULT_TENANT);

      // Act
      int status = perform(nonPrefixed(create).header(TENANT_IDS_HEADER, DEFAULT_TENANT));

      // Assert
      assertWrittenIn(create, status, DEFAULT_TENANT);
    }
  }

  @Nested
  @DisplayName("Create capability in tenant B, read only in the default tenant")
  class CreateCapabilityInOtherTenantOnly {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("On the non-prefixed route with a header naming B, no row is written outside B")
    void given_headerNamingTenantWithCreateCapability_should_notWriteOutsideThatTenant(
        Create create) throws Exception {
      // Arrange
      String tenantB = createTenantWithCreateCapability();

      // Act
      perform(nonPrefixed(create).header(TENANT_IDS_HEADER, tenantB));

      // Assert
      assertThat(rowTenants(create)).as("the row's tenant").isSubsetOf(tenantB);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("On the prefixed route of B, the row is written in B")
    void given_pathNamingTenantWithCreateCapability_should_writeInThatTenant(Create create)
        throws Exception {
      // Arrange
      String tenantB = createTenantWithCreateCapability();

      // Act
      int status = perform(prefixed(create, tenantB));

      // Assert
      assertWrittenIn(create, status, tenantB);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName("On the non-prefixed route with a header naming the default tenant, it is refused")
    void given_headerNamingTenantWithoutCreateCapability_should_refuseCreate(Create create)
        throws Exception {
      // Arrange
      createTenantWithCreateCapability();

      // Act
      int status = perform(nonPrefixed(create).header(TENANT_IDS_HEADER, DEFAULT_TENANT));

      // Assert
      assertRefusedWithoutRow(create, status);
    }
  }

  @Nested
  @DisplayName("Create capability in both the default tenant and tenant B")
  class CreateCapabilityInBothTenants {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Create.class)
    @DisplayName(
        "On the non-prefixed route with a header naming B, it is written in the ambient tenant")
    void given_headerNamingOtherTenantWithCreateCapability_should_writeInAmbientTenant(
        Create create) throws Exception {
      // Arrange
      String tenantB = createTenantWithCreateCapabilityInBoth();

      // Act
      int status = perform(nonPrefixed(create).header(TENANT_IDS_HEADER, tenantB));

      // Assert
      // These creates stamp their row from the ambient tenant, which the non-prefixed route
      // leaves to the default one whatever the header names.
      assertWrittenIn(create, status, DEFAULT_TENANT);
    }
  }

  /** The caller creates in {@code createTenant} and only reads in a new tenant B. */
  private String createTenantWithReadOnly(String createTenant) throws Exception {
    tenantHelper.grantCapabilitiesInTenant(createTenant, Set.of(Capability.MANAGE_ASSESSMENT));
    return tenantHelper
        .createTenantWithCapabilities("capability-b", Set.of(Capability.ACCESS_ASSESSMENT))
        .getId();
  }

  /** The caller creates in a new tenant B and only reads in the default tenant. */
  private String createTenantWithCreateCapability() throws Exception {
    tenantHelper.grantCapabilitiesInTenant(DEFAULT_TENANT, Set.of(Capability.ACCESS_ASSESSMENT));
    return tenantHelper
        .createTenantWithCapabilities("capability-b", Set.of(Capability.MANAGE_ASSESSMENT))
        .getId();
  }

  /** The caller creates in both the default tenant and a new tenant B. */
  private String createTenantWithCreateCapabilityInBoth() throws Exception {
    tenantHelper.grantCapabilitiesInTenant(DEFAULT_TENANT, Set.of(Capability.MANAGE_ASSESSMENT));
    return tenantHelper
        .createTenantWithCapabilities("capability-b", Set.of(Capability.MANAGE_ASSESSMENT))
        .getId();
  }

  private MockHttpServletRequestBuilder nonPrefixed(Create create) {
    // The non-prefixed route never sets the ambient tenant: reproduce production's empty
    // thread-local instead of inheriting what the fixtures left on the test thread.
    TenantContext.clearCurrentTenant();
    assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");
    return post(create.uri)
        .contentType(MediaType.APPLICATION_JSON)
        .content(create.body())
        .with(csrf());
  }

  private MockHttpServletRequestBuilder prefixed(Create create, String tenantId) {
    TenantContext.clearCurrentTenant();
    return post(create.tenantUri, tenantId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(create.body())
        .with(csrf());
  }

  private int perform(MockHttpServletRequestBuilder request) throws Exception {
    int status = mvc.perform(request).andReturn().getResponse().getStatus();
    entityManager.flush();
    return status;
  }

  private void assertRefusedWithoutRow(Create create, int status) {
    assertAll(
        () -> assertEquals(HttpStatus.FORBIDDEN.value(), status, "the create is refused"),
        () -> assertEquals(List.of(), rowTenants(create), "a refused create writes no row"));
  }

  private void assertWrittenIn(Create create, int status, String tenantId) {
    assertAll(
        () -> assertEquals(HttpStatus.OK.value(), status, "the create is accepted"),
        () -> assertEquals(List.of(tenantId), rowTenants(create), "the row's tenant"));
  }

  /**
   * Reads on the current connection, past the statement inspector, so that rows of every tenant are
   * seen whatever scope the request left on the test transaction.
   */
  private List<String> rowTenants(Create create) {
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (var stmt =
                  connection.prepareStatement(
                      "SELECT tenant_id FROM "
                          + create.table
                          + " WHERE "
                          + create.nameField
                          + " = ?")) {
                stmt.setString(1, NAME);
                List<String> tenants = new ArrayList<>();
                try (var rows = stmt.executeQuery()) {
                  while (rows.next()) {
                    tenants.add(rows.getString(1));
                  }
                }
                return tenants;
              }
            });
  }
}
