package io.openaev.api.threat_arsenal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Command;
import io.openaev.database.model.Tenant;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.PayloadFixture;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that the approval decisions shown in an action's History (read from {@code
 * payload_approvals}) are isolated by the tenant scope set from the URL path. Each tenant has an
 * action and a rejection on it; tenant B also owns a "planted" rejection that points at tenant A's
 * payload. No API flow writes such a row (an approval copies its payload's tenant); it proves the
 * {@code payload_approvals} scope on its own. Everything is seeded natively, and ground truth is
 * read in raw JDBC, which the statement inspector never rewrites.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Approval decisions in the History are isolated per tenant")
class ThreatArsenalHistoryIsolationTest extends IntegrationTest {

  // The production list, as other round-trip scope tests arm it: one shared cached context.
  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String TENANT_HISTORY =
      "/api/tenants/{tenantId}/threat_arsenals/{actionId}/history";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private Seeded actionA;
  private Seeded actionB;
  private String plantedDecisionB;

  /** A seeded action: its contract id and payload id. */
  private record Seeded(String actionId, String payloadId) {}

  @BeforeEach
  void seedTwoTenantsWithOneRejectedActionEach() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("history-iso-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("history-iso-b").getId();
    actionA = seedAction(tenantA, "history-iso-action-a-" + UUID.randomUUID());
    actionB = seedAction(tenantB, "history-iso-action-b-" + UUID.randomUUID());
    seedRejection(tenantA, actionA.payloadId(), "Reason of a");
    seedRejection(tenantB, actionB.payloadId(), "Reason of b");
    plantedDecisionB = seedRejection(tenantB, actionA.payloadId(), "Planted by b");
    // Evict the seed from the session: the reads below must issue real, rewritten SELECTs.
    entityManager.clear();
  }

  @Nested
  @DisplayName("Reading the History")
  class Reading {

    @Test
    @DisplayName("given A's action under A's path, should show A's decision and never B's")
    void given_ownActionUnderOwnPath_should_showOnlyOwnDecisions() throws Exception {
      // Act
      String response =
          mvc.perform(get(TENANT_HISTORY, tenantA, actionA.actionId()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> reasons =
          JsonPath.read(
              response,
              "$[?(@.content_history_action == 'REJECT')].content_history_details.reason");
      assertThat(reasons).containsExactly("Reason of a");
      assertThat(response).doesNotContain(plantedDecisionB, "Planted by b", "Reason of b");
      // Ground truth: B's decision on A's payload exists, the scope hid it.
      assertThat(rawCount(plantedDecisionB)).isEqualTo(1L);
    }

    @Test
    @DisplayName("given B's action under A's path, should be not found")
    void given_otherTenantActionUnderOwnPath_should_beNotFound() throws Exception {
      // Act
      String response =
          mvc.perform(get(TENANT_HISTORY, tenantA, actionB.actionId()))
              .andExpect(status().isNotFound())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(response).doesNotContain("Reason of b");
    }
  }

  private Seeded seedAction(String tenantId, String name) {
    Command command = (Command) PayloadFixture.createCommand("bash", "echo hello", List.of(), null);
    command.setName(name);
    command.setTenant(new Tenant(tenantId));
    entityManager.persist(command);
    entityManager.flush();
    String contractId = UUID.randomUUID().toString();
    jdbcUpdate(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " injector_contract_payload, tenant_id) VALUES (?, '{}', ?, ?)",
        contractId,
        command.getId(),
        tenantId);
    return new Seeded(contractId, command.getId());
  }

  private String seedRejection(String tenantId, String payloadId, String reason) {
    String id = UUID.randomUUID().toString();
    jdbcUpdate(
        "INSERT INTO payload_approvals (payload_approval_id, tenant_id, payload_id,"
            + " payload_approval_status, payload_approval_origin, payload_approval_automatic,"
            + " payload_approval_actor_name, payload_approval_comment,"
            + " payload_approval_created_at) VALUES (?, ?, ?, 'REJECTED', 'REJECT', false,"
            + " 'Approver', ?, now())",
        id,
        tenantId,
        payloadId,
        reason);
    return id;
  }

  private void jdbcUpdate(String sql, String... parameters) {
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

  // Ground truth: raw JDBC on the test's own connection, never rewritten by the inspector.
  private long rawCount(String approvalId) {
    entityManager.flush();
    return entityManager
        .unwrap(Session.class)
        .doReturningWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "SELECT count(*) FROM payload_approvals WHERE payload_approval_id = ?")) {
                statement.setString(1, approvalId);
                try (ResultSet rows = statement.executeQuery()) {
                  rows.next();
                  return rows.getLong(1);
                }
              }
            });
  }
}
