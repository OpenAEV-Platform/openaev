package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Isolation of {@code autonomous_events} and {@code autonomous_directives} on their own rows,
 * through the real {@link AutonomousRunApi} endpoints on the tenant path.
 *
 * <p>Every event and directive hangs off a run, and a run of another tenant is already refused on
 * {@code autonomous_runs}, so a case built on another tenant's run cannot tell which table refused.
 * Here the other tenant's event and directive are attached to the caller's own run: the parent is
 * visible and only the child table's own scope stands between the caller and the row. The seed is
 * raw JDBC with an explicit tenant, which the statement inspector never rewrites and which no
 * endpoint would produce.
 *
 * <p>Each test stays on tenant A's path, so the per-request scope is the same for every request.
 */
@CoversTenantIsolation({"autonomous_events", "autonomous_directives"})
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("autonomous_events and autonomous_directives refuse another tenant's row on a run")
class AutonomousRunChildRowHttpIsolationTest extends IntegrationTest {

  private static final String RUN_BY_ID =
      TENANT_PREFIX + AutonomousRunApi.AUTONOMOUS_URI.substring("/api".length()) + "/{runId}";

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  // The endpoints are EE-gated; the mock's license checks default to "active" (Mockito false).
  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String tenantB;
  private String runA;

  @BeforeEach
  void seedTwoTenantsAndOneRunInA() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("autonomous-child-iso-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("autonomous-child-iso-b").getId();
    runA = UUID.randomUUID().toString();
    execute(
        "INSERT INTO autonomous_runs (autonomous_run_id, tenant_id, autonomous_run_objective,"
            + " autonomous_run_status) VALUES (?, ?, 'Own the domain', 'COMPLETED')",
        runA,
        tenantA);
  }

  @Nested
  @DisplayName("autonomous_events")
  class Events {

    @Test
    @DisplayName("given its run's timeline, should list its own event and not the other's")
    void given_runTimeline_should_listOwnAndNotOtherTenants() throws Exception {
      // Arrange
      String ownEvent = seedEvent(tenantA, 1);
      String foreignEvent = seedEvent(tenantB, 2);

      // Act
      String response =
          mvc.perform(get(RUN_BY_ID + "/timeline", tenantA, runA))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertTrue(response.contains(ownEvent), "A's event must be listed");
      assertFalse(response.contains(foreignEvent), "B's event must not be listed");
    }
  }

  @Nested
  @DisplayName("autonomous_directives")
  class Directives {

    private String ownDirective;
    private String foreignDirective;

    @BeforeEach
    void seedOnePendingDirectivePerTenantOnTheSameRun() {
      ownDirective = seedDirective(tenantA);
      foreignDirective = seedDirective(tenantB);
    }

    @Test
    @DisplayName("given its run's directives, should list its own and not the other's")
    void given_runDirectives_should_listOwnAndNotOtherTenants() throws Exception {
      // Act
      String response =
          mvc.perform(get(RUN_BY_ID + "/directives", tenantA, runA))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertTrue(response.contains(ownDirective), "A's directive must be listed");
      assertFalse(response.contains(foreignDirective), "B's directive must not be listed");
    }

    @Test
    @DisplayName("given its run's pending directives, should consume its own and not the other's")
    void given_pendingDirectives_should_consumeOwnAndNotOtherTenants() throws Exception {
      // Act
      mvc.perform(post(RUN_BY_ID + "/directives/consume", tenantA, runA).with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertEquals("CONSUMED", rawDirectiveStatus(ownDirective), "A's directive is consumed");
      assertEquals(
          "PENDING", rawDirectiveStatus(foreignDirective), "B's directive must be untouched");
    }
  }

  // -- FIXTURES --

  private String seedEvent(String tenantId, int sequence) {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO autonomous_events (autonomous_event_id, tenant_id, autonomous_event_run_id,"
            + " autonomous_event_sequence, autonomous_event_type, autonomous_event_title)"
            + " VALUES (?, ?, ?, "
            + sequence
            + ", 'STATUS', 'Run created')",
        id,
        tenantId,
        runA);
    return id;
  }

  private String seedDirective(String tenantId) {
    String id = UUID.randomUUID().toString();
    execute(
        "INSERT INTO autonomous_directives (autonomous_directive_id, tenant_id,"
            + " autonomous_directive_run_id, autonomous_directive_content,"
            + " autonomous_directive_status) VALUES (?, ?, ?, 'Focus on the domain controller',"
            + " 'PENDING')",
        id,
        tenantId,
        runA);
    return id;
  }

  // Ground truth, scope-free: raw JDBC on the test's own connection sees the uncommitted rows and
  // the inspector never rewrites a statement it did not generate.
  private String rawDirectiveStatus(String directiveId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT autonomous_directive_status FROM autonomous_directives"
                          + " WHERE autonomous_directive_id = ?")) {
                statement.setString(1, directiveId);
                try (ResultSet rows = statement.executeQuery()) {
                  return rows.next() ? rows.getString(1) : null;
                }
              }
            });
  }

  private void execute(String sql, String... parameters) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < parameters.length; i++) {
                  statement.setString(i + 1, parameters[i]);
                }
                statement.executeUpdate();
              }
            });
  }
}
