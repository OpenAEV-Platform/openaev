package io.openaev.service.marking_definition;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.repository.MarkingDefinitionRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proof that the activation of {@code marking_definitions} isolates the table on its own, through
 * the repository read the API actually uses.
 *
 * <p>{@code MarkingDefinitionService} scopes its list and search with a tenant specification it
 * builds itself, and its by-id path loads the row with an unscoped {@code findById} before
 * comparing the row's tenant with the caller's. Those application-level predicates isolate whether
 * or not the table is active, so every assertion written against the HTTP endpoints holds with the
 * table removed from the activation list and proves nothing about the activation. This class reads
 * through the repository instead, with nothing but the transaction scope in play, so it fails as
 * soon as the table leaves the list.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=marking_definitions")
@WithMockUser(isAdmin = true)
@DisplayName("marking_definitions isolation through the scope alone")
class MarkingDefinitionTenantScopeTest extends IntegrationTest {

  @Autowired private MarkingDefinitionRepository repository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  private String tenantA;
  private String rowA;
  private String rowB;

  @BeforeEach
  void seedOneDefinitionPerTenant() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("marking-scope-a").getId();
    String tenantB = tenantHelper.createTenantWithCurrentUser("marking-scope-b").getId();
    rowA = seedDefinition(tenantA, "SCOPE-A");
    rowB = seedDefinition(tenantB, "SCOPE-B");
  }

  @Nested
  @DisplayName("under a single tenant's scope")
  class UnderOneTenantScope {

    @Test
    @DisplayName("given_tenantAScope_should_notReadTenantBRowById")
    void given_tenantAScope_should_notReadTenantBRowById() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act & Assert
      assertTrue(repository.findById(rowA).isPresent(), "A's own definition must be readable");
      assertTrue(
          repository.findById(rowB).isEmpty(),
          "B's definition must not be reachable by id under A's scope: the service checks the"
              + " tenant only after loading the row, so the scope is what has to hide it");
    }

    @Test
    @DisplayName("given_tenantAScope_should_notListTenantBRow")
    void given_tenantAScope_should_notListTenantBRow() {
      // Arrange
      tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));

      // Act
      List<String> ids = new ArrayList<>();
      repository.findAll().forEach(definition -> ids.add(definition.getId()));

      // Assert
      assertTrue(ids.contains(rowA), "A's definition must be listed");
      assertFalse(ids.contains(rowB), "B's definition must not be listed under A's scope");
    }
  }

  @Nested
  @DisplayName("with no scope at all")
  class WithoutScope {

    @Test
    @DisplayName("given_noScope_should_failClosedEvenForOwnRow")
    void given_noScope_should_failClosedEvenForOwnRow() {
      // Act & Assert
      // Second assertion line, on a path the one above does not exercise: it shows the read
      // succeeds because a scope is set, not because the row happens to be the caller's.
      assertTrue(
          repository.findById(rowA).isEmpty(),
          "a read of an active table with no scope on the transaction must fail closed");
    }
  }

  private String seedDefinition(String tenantId, String definition) {
    String id = UUID.randomUUID().toString();
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try (PreparedStatement statement =
                  connection.prepareStatement(
                      "INSERT INTO marking_definitions (marking_definition_id,"
                          + " marking_definition_type, marking_definition_definition,"
                          + " marking_definition_color, marking_definition_order,"
                          + " marking_definition_protected, marking_definition_created_at,"
                          + " marking_definition_updated_at, tenant_id)"
                          + " VALUES (?, 'TLP', ?, '#112233', 7, false, now(), now(), ?)")) {
                statement.setString(1, id);
                statement.setString(2, definition);
                statement.setString(3, tenantId);
                statement.executeUpdate();
              }
            });
    return id;
  }
}
