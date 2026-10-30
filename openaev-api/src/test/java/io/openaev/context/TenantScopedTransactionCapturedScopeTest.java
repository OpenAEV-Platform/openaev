package io.openaev.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.TagRepository;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link TenantScopedTransaction#captureScopeOfCurrentTransaction()}: a caller that narrows its own
 * transaction to one tenant for part of its work gets its tenant scope and its marking clearance
 * back once that part is done, and what the narrowed part changed is written under the narrowed
 * scope.
 *
 * <p>The class runs with the production list of active tables, which holds {@code tags}, so that an
 * update of a tag is filtered by the tenant scope it runs under. Deliberately NOT
 * {@code @Transactional}: each test owns its transaction, and seeds and cleans through
 * auto-committed JDBC.
 */
@DisplayName("TenantScopedTransaction: capturing the scope of the current transaction")
class TenantScopedTransactionCapturedScopeTest extends IntegrationTest {

  private static final String RENAMED = "captured-scope-renamed";

  @DynamicPropertySource
  static void productionActiveTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private TagRepository tagRepository;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String markingOfA;
  private String tagOfA;
  private String tagOfB;

  @BeforeEach
  void seedTwoTenants() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("captured-scope-a-" + UUID.randomUUID());
    tenantB = seedTenant("captured-scope-b-" + UUID.randomUUID());
    markingOfA = seedMarking(tenantA);
    tagOfA = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tenant_id) VALUES (?, ?, ?)",
        tagOfA,
        "captured-scope-tag",
        tenantA);
    tagOfB = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tags (tag_id, tag_name, tenant_id) VALUES (?, ?, ?)",
        tagOfB,
        "captured-scope-tag",
        tenantB);
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM tags WHERE tag_id IN (?, ?)", tagOfA, tagOfB);
    jdbc.update("DELETE FROM marking_definitions WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  @Nested
  @DisplayName("closing the captured scope")
  class ClosingTheCapturedScope {

    @Test
    @DisplayName("given a transaction narrowed to one tenant, should put both scopes back")
    void given_transactionNarrowedToOneTenant_should_putBothScopesBack() {
      // -- ARRANGE --
      String[] narrowed = new String[2];
      String[] after = new String[2];

      // -- ACT --
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                setCallerScope(tenantA + "," + tenantB);
                try (TenantScopedTransaction.CapturedScope callerScope =
                    tenantTx.captureScopeOfCurrentTransaction()) {
                  tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
                  narrowed[0] = currentSetting("app.current_tenants");
                  narrowed[1] = currentSetting("app.current_markings");
                }
                after[0] = currentSetting("app.current_tenants");
                after[1] = currentSetting("app.current_markings");
              });

      // -- ASSERT --
      assertThat(narrowed[0]).as("the narrowed part runs in one tenant").isEqualTo(tenantA);
      assertThat(narrowed[1])
          .as("the narrowed part runs at system clearance")
          .isEqualTo(markingOfA);
      assertThat(after[0]).isEqualTo(tenantA + "," + tenantB);
      assertThat(after[1]).as("the caller's clearance is back").isEmpty();
    }

    @Test
    @DisplayName(
        "given a change made in the narrowed part, should write it under the narrowed scope")
    void given_changeMadeInTheNarrowedPart_should_writeItUnderTheNarrowedScope() {
      // -- ACT --
      // The caller's own scope does not hold the tag's tenant: only the narrowed part does.
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                setCallerScope(tenantB);
                try (TenantScopedTransaction.CapturedScope callerScope =
                    tenantTx.captureScopeOfCurrentTransaction()) {
                  tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
                  Tag tag = tagRepository.findById(tagOfA).orElseThrow();
                  tag.setName(RENAMED);
                }
              });

      // -- ASSERT --
      assertThat(
              jdbc.queryForObject(
                  "SELECT tag_name FROM tags WHERE tag_id = ?", String.class, tagOfA))
          .isEqualTo(RENAMED);
    }
  }

  @Nested
  @DisplayName("capturing the scope")
  class CapturingTheScope {

    @Test
    @DisplayName(
        "given a change made under the caller's scope, should write it under the caller's scope")
    void given_changeMadeUnderTheCallersScope_should_writeItUnderTheCallersScope() {
      // -- ACT --
      // The change is pending when the scope moves to A, which does not hold the tag's tenant.
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                setCallerScope(tenantA + "," + tenantB);
                Tag tag = tagRepository.findById(tagOfB).orElseThrow();
                tag.setName(RENAMED);
                try (TenantScopedTransaction.CapturedScope callerScope =
                    tenantTx.captureScopeOfCurrentTransaction()) {
                  tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(tenantA));
                }
              });

      // -- ASSERT --
      assertThat(
              jdbc.queryForObject(
                  "SELECT tag_name FROM tags WHERE tag_id = ?", String.class, tagOfB))
          .isEqualTo(RENAMED);
    }
  }

  @Nested
  @DisplayName("outside a transaction")
  class OutsideATransaction {

    @Test
    @DisplayName("given no active transaction, should refuse to capture")
    void given_noActiveTransaction_should_refuseToCapture() {
      // -- ACT / ASSERT --
      assertThatThrownBy(() -> tenantTx.captureScopeOfCurrentTransaction())
          .isInstanceOf(IllegalStateException.class);
    }
  }

  // -- HELPERS --

  /** What the transaction aspect leaves on a request transaction whose caller holds no marking. */
  private void setCallerScope(String tenants) {
    jdbc.queryForObject("SELECT set_config('app.current_tenants', ?, true)", String.class, tenants);
    jdbc.queryForObject("SELECT set_config('app.current_markings', '', true)", String.class);
  }

  private String currentSetting(String key) {
    return jdbc.queryForObject(
        "SELECT coalesce(current_setting('" + key + "', true), '')", String.class);
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  private String seedMarking(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO marking_definitions (marking_definition_id, marking_definition_type,"
            + " marking_definition_definition, marking_definition_order, marking_definition_color,"
            + " marking_definition_created_at, marking_definition_updated_at, tenant_id)"
            + " VALUES (?, 'TLP', 'TLP:RED', 50, '#000000', now(), now(), ?)",
        id,
        tenantId);
    return id;
  }
}
