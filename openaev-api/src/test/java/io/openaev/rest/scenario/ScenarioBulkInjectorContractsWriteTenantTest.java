package io.openaev.rest.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Capability;
import io.openaev.database.model.InjectorContract;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.rest.injector_contract.input.InjectorContractSearchPaginationInput;
import io.openaev.rest.scenario.form.ScenarioIdsAndInjectorContractsInputs;
import io.openaev.rest.scenario.form.ScenarioSimple;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectorContractFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The bulk "scenarios with injector contracts" write acts in exactly one tenant, the tenant the
 * request writes to: the tenant of the route, or the single tenant the header names. A scope that
 * names several tenants cannot designate it, so the request is refused with 400 and nothing is
 * written. The scenarios named in the body resolve in that tenant: a scenario of another tenant is
 * treated like an unknown id.
 *
 * <p>The production active tables are armed and the rows are committed, as a real request sees
 * them; the caller is a member of two tenants and holds the scenario write right in both. The
 * caller is an administrator, as in the create posture test: on the header route the access check
 * reads the ambient tenant, the default one, where a member of A and B only is refused with 403
 * before the write tenant is resolved. The rows are read back through plain JDBC, which the tenant
 * inspector never rewrites.
 */
@TestInstance(PER_CLASS)
@WithMockUser(isAdmin = true)
@DisplayName("Bulk injector contracts write acts in the request's write tenant")
class ScenarioBulkInjectorContractsWriteTenantTest extends IntegrationTest {

  private static final String WITH_INJECTOR_CONTRACTS = "/with-injector-contracts";
  private static final String TENANT_IDS_HEADER = "X-Tenant-Ids";
  private static final String WRITE_SCOPE_ERROR = "TENANT_WRITE_SCOPE";
  private static final String LOCALE = "en";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private ScenarioService scenarioService;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private final List<String> tenantIds = new ArrayList<>();
  private final List<String> scenarioIds = new ArrayList<>();
  private final List<String> contractIds = new ArrayList<>();
  private final List<String> injectorIds = new ArrayList<>();

  private String tenantA;
  private String tenantB;

  @AfterEach
  void removeCommittedRows() {
    scenarioIds.forEach(
        id -> {
          jdbc().update("DELETE FROM injects WHERE inject_scenario = ?", id);
          jdbc().update("DELETE FROM scenarios WHERE scenario_id = ?", id);
        });
    contractIds.forEach(
        id -> jdbc().update("DELETE FROM injectors_contracts WHERE injector_contract_id = ?", id));
    injectorIds.forEach(id -> jdbc().update("DELETE FROM injectors WHERE injector_id = ?", id));
    tenantHelper.deleteCommittedTenants(tenantIds.toArray(new String[0]));
    scenarioIds.clear();
    contractIds.clear();
    injectorIds.clear();
    tenantIds.clear();
  }

  /** Two tenants where the caller holds the scenario write right. */
  private void joinTwoTenants() throws Exception {
    tenantA =
        tenantHelper
            .createTenantWithCapabilities("bulk-contracts-a", Set.of(Capability.MANAGE_ASSESSMENT))
            .getId();
    tenantB =
        tenantHelper
            .createTenantWithCapabilities("bulk-contracts-b", Set.of(Capability.MANAGE_ASSESSMENT))
            .getId();
    tenantIds.add(tenantA);
    tenantIds.add(tenantB);
  }

  private String commitScenario(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      String id =
          new TransactionTemplate(transactionManager)
              .execute(
                  status -> {
                    Scenario scenario = ScenarioFixture.createDefaultCrisisScenario();
                    scenario.setTenant(new Tenant(tenantId));
                    return scenarioRepository.save(scenario).getId();
                  });
      scenarioIds.add(id);
      return id;
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private String commitContract(String tenantId) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return new TransactionTemplate(transactionManager)
          .execute(
              status -> {
                InjectorContract contract = InjectorContractFixture.createDefaultInjectorContract();
                contract.setLabels(Map.of("en", "bulk contracts " + UUID.randomUUID()));
                InjectorContract saved =
                    injectorContractComposer.forInjectorContract(contract).persist().get();
                contractIds.add(saved.getId());
                saved.getInjectors().forEach(injector -> injectorIds.add(injector.getId()));
                return saved.getId();
              });
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private static InjectorContractSearchPaginationInput selectionOf(String contractId) {
    InjectorContractSearchPaginationInput pagination = new InjectorContractSearchPaginationInput();
    pagination.setIncludeFullDetails(true);
    pagination.setInjectorContractIdsToProcess(List.of(contractId));
    return pagination;
  }

  private MockHttpServletRequestBuilder update(String url, String contractId, String... idsInBody) {
    ScenarioIdsAndInjectorContractsInputs input = new ScenarioIdsAndInjectorContractsInputs();
    input.setLocale(LOCALE);
    input.setScenarioIds(List.of(idsInBody));
    input.setInjectorContractSearchPaginationInput(selectionOf(contractId));
    return put(url + WITH_INJECTOR_CONTRACTS)
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .content(asJsonString(input));
  }

  private static String routeOf(String tenantId) {
    return TENANT_SCENARIO_URI.replace("{tenantId}", tenantId);
  }

  /** Sends from the request thread's empty ambient tenant, as production does. */
  private MockHttpServletResponse send(MockHttpServletRequestBuilder request) throws Exception {
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    return mvc.perform(request.with(csrf())).andReturn().getResponse();
  }

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource);
  }

  private int injectsOf(String scenarioId) {
    return jdbc()
        .queryForObject(
            "SELECT count(*) FROM injects WHERE inject_scenario = ?", Integer.class, scenarioId);
  }

  private List<String> injectTenantsOf(String scenarioId) {
    return jdbc()
        .queryForList(
            "SELECT DISTINCT tenant_id FROM injects WHERE inject_scenario = ?",
            String.class,
            scenarioId);
  }

  @Nested
  @DisplayName("A request on the header route")
  class HeaderRoute {

    @Test
    @DisplayName("given a header naming two tenants should refuse and write no inject")
    void given_aHeaderNamingTwoTenants_should_refuseAndWriteNoInject() throws Exception {
      // Arrange
      joinTwoTenants();
      String contractInA = commitContract(tenantA);
      String scenarioInA = commitScenario(tenantA);
      String scenarioInB = commitScenario(tenantB);

      // Act
      MockHttpServletResponse response =
          send(
              update(SCENARIO_URI, contractInA, scenarioInA, scenarioInB)
                  .header(TENANT_IDS_HEADER, tenantA + "," + tenantB));

      // Assert
      assertThat(response.getStatus()).as("the update is refused").isEqualTo(400);
      assertThat(JsonPath.<String>read(response.getContentAsString(), "$.message"))
          .as("the refusal names the write scope")
          .isEqualTo(WRITE_SCOPE_ERROR);
      assertThat(injectsOf(scenarioInA)).as("no inject in A's scenario").isZero();
      assertThat(injectsOf(scenarioInB)).as("no inject in B's scenario").isZero();
    }

    @Test
    @DisplayName("given a header naming one tenant should write the injects in that tenant")
    void given_aHeaderNamingOneTenant_should_writeTheInjectsInThatTenant() throws Exception {
      // Arrange
      joinTwoTenants();
      String contractInB = commitContract(tenantB);
      String scenarioInA = commitScenario(tenantA);
      String scenarioInB = commitScenario(tenantB);

      // Act
      MockHttpServletResponse response =
          send(
              update(SCENARIO_URI, contractInB, scenarioInA, scenarioInB)
                  .header(TENANT_IDS_HEADER, tenantB));

      // Assert
      assertThat(response.getStatus()).as("the update succeeds").isEqualTo(200);
      assertThat(injectsOf(scenarioInB)).as("injects in B's scenario").isGreaterThan(0);
      assertThat(injectTenantsOf(scenarioInB)).containsExactly(tenantB);
      assertThat(injectsOf(scenarioInA)).as("no inject in A's scenario").isZero();
    }
  }

  @Nested
  @DisplayName("A request on the route of a tenant")
  class PrefixedRoute {

    @Test
    @DisplayName("given a body naming a scenario of another tenant should write only in the path's")
    void given_aBodyNamingAScenarioOfAnotherTenant_should_writeOnlyInThePathTenant()
        throws Exception {
      // Arrange
      joinTwoTenants();
      String contractInA = commitContract(tenantA);
      String scenarioInA = commitScenario(tenantA);
      String scenarioInB = commitScenario(tenantB);

      // Act
      MockHttpServletResponse response =
          send(update(routeOf(tenantA), contractInA, scenarioInA, scenarioInB));

      // Assert
      assertThat(response.getStatus()).as("the update succeeds").isEqualTo(200);
      assertThat(injectsOf(scenarioInA)).as("injects in A's scenario").isGreaterThan(0);
      assertThat(injectTenantsOf(scenarioInA)).containsExactly(tenantA);
      assertThat(injectsOf(scenarioInB)).as("B's scenario is unchanged").isZero();
    }

    @Test
    @DisplayName("given a body naming a scenario of the path's tenant should write the injects")
    void given_aBodyNamingAScenarioOfThePathTenant_should_writeTheInjects() throws Exception {
      // Arrange
      joinTwoTenants();
      String contractInA = commitContract(tenantA);
      String scenarioInA = commitScenario(tenantA);

      // Act
      MockHttpServletResponse response = send(update(routeOf(tenantA), contractInA, scenarioInA));

      // Assert
      assertThat(response.getStatus()).as("the update succeeds").isEqualTo(200);
      assertThat(injectsOf(scenarioInA)).as("injects in A's scenario").isGreaterThan(0);
    }
  }

  @Nested
  @DisplayName("A service call under a scope holding two tenants")
  class ServiceUnderTwoTenantScope {

    @Test
    @DisplayName("given a scenario of another tenant in the list should write only in the tenant")
    void given_aScenarioOfAnotherTenantInTheList_should_writeOnlyInTheWriteTenant()
        throws Exception {
      // Arrange
      joinTwoTenants();
      String contractInA = commitContract(tenantA);
      String scenarioInA = commitScenario(tenantA);
      String scenarioInB = commitScenario(tenantB);
      TxCtx bothTenants = TxCtx.forTenants(List.of(tenantA, tenantB));

      // Act
      List<String> updated;
      TenantContext.setCurrentTenant(tenantA);
      try {
        updated =
            scenarioService
                .updateScenariosWithInjectorContracts(
                    bothTenants,
                    tenantA,
                    List.of(scenarioInA, scenarioInB),
                    selectionOf(contractInA),
                    LOCALE)
                .stream()
                .map(ScenarioSimple::getId)
                .toList();
      } finally {
        TenantContext.clearCurrentTenant();
      }

      // Assert
      assertThat(updated).as("the scenarios updated").containsExactly(scenarioInA);
      assertThat(injectsOf(scenarioInA)).as("injects in A's scenario").isGreaterThan(0);
      assertThat(injectsOf(scenarioInB)).as("B's scenario is unchanged").isZero();
    }
  }
}
