package io.openaev.api.autonomous;

import static io.openaev.api.autonomous.AutonomousRunApi.AUTONOMOUS_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.security.token.XtmJwksExtractor;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityNotFoundException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A step authored on a run is linked to the event it reuses only when the event belongs to the
 * tenant of the step. On the non-prefixed route with {@code X-Tenant-Ids} naming two tenants, an
 * event of another tenant is an unknown id (404 "Condition root not found") and no {@code
 * conditions_steps} row names it. The verified service identity links an event of the run's tenant.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("a step is linked to the events of its own tenant")
class AutonomousStepEventTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String STEPS = AUTONOMOUS_URI + "/{runId}/attack-path/steps";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  private String tenantA;
  private String tenantB;
  private String contractOfA;
  private String runOfA;
  private String workflowOfA;
  private String eventOfA;
  private String eventOfB;

  @BeforeEach
  void seedTwoTenantsARunOfAAndOneEventEachOnTheWorkflowOfA() throws Exception {
    // Arrange
    tenantA = tenantHelper.createTenantWithCurrentUser("step-event-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("step-event-b").getId();
    assertThat(tenantA).isNotEqualTo(Tenant.DEFAULT_TENANT_UUID);
    assertThat(tenantB).isNotEqualTo(Tenant.DEFAULT_TENANT_UUID);
    contractOfA = seedContract(tenantA);
    String simulationOfA = seedSimulation(tenantA);
    workflowOfA = seedTemplateWorkflow(tenantA, simulationOfA);
    runOfA = seedActiveRun(tenantA, simulationOfA);
    eventOfA = seedEvent(tenantA, workflowOfA);
    // eventOfB sits on the workflow of A with the tenant of B
    eventOfB = seedEvent(tenantB, workflowOfA);
    entityManager.flush();
    entityManager.clear();
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.getCurrentTenant()).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
    assertThat(rawTenantOf("workflows", "workflow_id", workflowOfA)).isEqualTo(tenantA);
    assertThat(rawTenantOf("autonomous_runs", "autonomous_run_id", runOfA)).isEqualTo(tenantA);
    assertThat(rawTenantOf("conditions", "condition_id", eventOfA)).isEqualTo(tenantA);
    assertThat(rawTenantOf("conditions", "condition_id", eventOfB)).isEqualTo(tenantB);
  }

  private static MockHttpServletRequestBuilder asVerifiedServiceIdentity(
      MockHttpServletRequestBuilder request) {
    return request.requestAttr(XtmJwksExtractor.CROSS_PLATFORM_ATTRIBUTE, Boolean.TRUE);
  }

  private MockHttpServletRequestBuilder appendStepFiringOn(String eventId) {
    return post(STEPS, runOfA)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"inject\":{\"inject_title\":\"step-event\",\"inject_injector_contract\":\""
                + contractOfA
                + "\"},\"trigger\":{\"event_id\":\""
                + eventId
                + "\"}}")
        .with(csrf());
  }

  @Nested
  @DisplayName("X-Tenant-Ids header route")
  class HeaderRoute {

    @Test
    @DisplayName(
        "given_headerNamingTwoTenantsAndAnEventOfTheOther_should_answerNotFoundAndLinkNothing")
    void given_headerNamingTwoTenantsAndAnEventOfTheOther_should_answerNotFoundAndLinkNothing()
        throws Exception {
      // Arrange
      assertThat(linksOf(eventOfB)).isZero();

      // Act
      Exception resolved =
          mvc.perform(appendStepFiringOn(eventOfB).header("X-Tenant-Ids", tenantA + "," + tenantB))
              .andExpect(status().isNotFound())
              .andReturn()
              .getResolvedException();

      // Assert
      assertThat(resolved).isInstanceOf(EntityNotFoundException.class);
      assertThat(resolved.getMessage()).isEqualTo("Condition root not found: " + eventOfB);
      assertThat(linksOf(eventOfB)).isZero();
    }

    @Test
    @DisplayName("given_headerNamingTwoTenantsAndAnEventOfTheRun_should_linkTheStepToIt")
    void given_headerNamingTwoTenantsAndAnEventOfTheRun_should_linkTheStepToIt() throws Exception {
      // Arrange
      assertThat(linksOf(eventOfA)).isZero();

      // Act
      mvc.perform(appendStepFiringOn(eventOfA).header("X-Tenant-Ids", tenantA + "," + tenantB))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.step_template_id").isNotEmpty());

      // Assert
      assertThat(linksOf(eventOfA)).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("verified service identity")
  class ServiceIdentity {

    @Test
    @DisplayName("given_serviceIdentityAndAnEventOfTheRun_should_linkTheStepToIt")
    void given_serviceIdentityAndAnEventOfTheRun_should_linkTheStepToIt() throws Exception {
      // Arrange
      assertThat(linksOf(eventOfA)).isZero();

      // Act
      mvc.perform(asVerifiedServiceIdentity(appendStepFiringOn(eventOfA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.step_template_id").isNotEmpty());

      // Assert
      assertThat(linksOf(eventOfA)).isEqualTo(1L);
    }
  }

  // Native seed: an explicit tenant_id lands the rows without a request scope.
  private String seedContract(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
                + " tenant_id) VALUES (:id, '{\"fields\":[]}', :tenant)")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private String seedSimulation(String tenantId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
                + " VALUES (:id, 'step-event', 'RUNNING', :tenant)")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private String seedTemplateWorkflow(String tenantId, String simulationId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO workflows (workflow_id, workflow_status, workflow_version,"
                + " workflow_simulation_id, tenant_id)"
                + " VALUES (:id, 'TEMPLATE', 0, :simulation, :tenant)")
        .setParameter("id", id)
        .setParameter("simulation", simulationId)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  private String seedActiveRun(String tenantId, String simulationId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
                + " autonomous_run_status, autonomous_run_simulation_id)"
                + " VALUES (:id, :tenant, 'Own the domain', 'RUNNING', :simulation)")
        .setParameter("id", id)
        .setParameter("tenant", tenantId)
        .setParameter("simulation", simulationId)
        .executeUpdate();
    return id;
  }

  private String seedEvent(String tenantId, String workflowId) {
    String id = UUID.randomUUID().toString();
    entityManager
        .createNativeQuery(
            "INSERT INTO conditions (condition_id, condition_workflow_id, condition_type,"
                + " tenant_id) VALUES (:id, :workflow, 'AND', :tenant)")
        .setParameter("id", id)
        .setParameter("workflow", workflowId)
        .setParameter("tenant", tenantId)
        .executeUpdate();
    return id;
  }

  // Ground truth over plain JDBC on the test's own connection: sees the uncommitted rows and the
  // inspector does not rewrite a statement it never generated.
  private String rawTenantOf(String table, String idColumn, String id) {
    return rawQuery(
        "SELECT tenant_id FROM " + table + " WHERE " + idColumn + " = ?", id, String.class);
  }

  private long linksOf(String conditionId) {
    return rawQuery(
        "SELECT count(*) FROM conditions_steps WHERE condition_id = ?", conditionId, Long.class);
  }

  private <T> T rawQuery(String sql, String parameter, Class<T> type) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, parameter);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getObject(1, type) : null;
                }
              }
            });
  }
}
