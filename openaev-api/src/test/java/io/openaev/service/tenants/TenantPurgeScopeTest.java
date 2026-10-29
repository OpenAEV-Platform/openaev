package io.openaev.service.tenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.multitenancy.DependenciesManager;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Pins the tenant scope of the scheduled tenant purge, with {@code marking_definitions} armed.
 *
 * <p>{@code TenantPurgeJob} runs {@link TenantService#purgeExpiredTenants()}, which walks every
 * {@link DependenciesManager} for each expired tenant. Before this scope was set the whole walk ran
 * with no scope, so any read a manager makes on a tenant-scoped table was fail-closed to nothing:
 * deleting a tenant's default injector contracts hydrates their documents, domains, payloads, tags
 * and vulnerabilities to cascade them, and hydrated nothing instead.
 *
 * <p>The probe below stands in that walk as a real {@link DependenciesManager} and records what the
 * cleanup transaction actually carries: the scope, and whether a row of an armed table belonging to
 * the tenant being purged is visible through the ORM.
 *
 * <p><b>Two</b> expired tenants, each with a marking of its own, plus a bystander tenant that is
 * not expired. One expired tenant cannot tell the three candidate behaviours apart: a scope set
 * once before the loop, a scope narrowed per iteration, and a scope widened to every tenant all
 * look identical on a single iteration. With two, a scope that is not redefined between iterations
 * leaves the second tenant's cleanup reading the first tenant's rows, which the assertions below
 * refuse.
 *
 * <p>The scope the tenant row delete runs under is observed from a transaction synchronization,
 * because it runs after the cleanup and the GUC is transaction-local, so nothing outside the purge
 * transaction can read it. That assertion pins a stated commitment of the method, not a row
 * difference: {@code tenants} is not an active table, so the {@code DELETE} is not rewritten by the
 * scope today and the tenant row is removed whatever the scope says. What it refuses is the delete
 * running under another tenant's scope, or under none.
 *
 * <p>NOT {@code @Transactional}: the purge opens one transaction per tenant, and a surrounding test
 * transaction would also keep the tenant rows it deletes alive for the assertions.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=marking_definitions")
@Import(TenantPurgeScopeTest.ScopeProbeConfiguration.class)
@DisplayName("Tenant purge tenant scope (marking_definitions armed)")
class TenantPurgeScopeTest extends IntegrationTest {

  private static final String SCOPE_CHANNEL = "app.current_tenants";

  /**
   * A {@link DependenciesManager} that only observes. {@link TenantService} injects every manager
   * bean in the context, so this is the real production loop, not a simulation of it.
   */
  static class ScopeProbe implements DependenciesManager {

    private final EntityManager entityManager;
    final Map<String, String> scopeSeenPerTenant = new LinkedHashMap<>();
    final Map<String, List<String>> markingsSeenPerTenant = new LinkedHashMap<>();
    final Map<String, String> scopeSeenAtCommitPerTenant = new LinkedHashMap<>();

    ScopeProbe(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    @Override
    public void createDependencyForTenant(Tenant tenant) {
      // Nothing to provision: this manager only observes the delete side.
    }

    @Override
    public void deleteDependencyForTenant(String tenantId) {
      registerCommitProbe(tenantId);
      scopeSeenPerTenant.put(tenantId, currentScope());
      markingsSeenPerTenant.put(
          tenantId,
          new ArrayList<>(
              entityManager
                  .createQuery("SELECT m.id FROM MarkingDefinition m ORDER BY m.id", String.class)
                  .getResultList()));
    }

    /**
     * Reads the scope the tenant row delete runs under. {@code beforeCommit} fires after that
     * delete, while the transaction and its connection are still this tenant's own.
     */
    private void registerCommitProbe(String tenantId) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
              scopeSeenAtCommitPerTenant.put(tenantId, currentScope());
            }
          });
    }

    private String currentScope() {
      return (String)
          entityManager
              .createNativeQuery("SELECT current_setting('" + SCOPE_CHANNEL + "', true)")
              .getSingleResult();
    }

    void reset() {
      scopeSeenPerTenant.clear();
      markingsSeenPerTenant.clear();
      scopeSeenAtCommitPerTenant.clear();
    }
  }

  @TestConfiguration
  static class ScopeProbeConfiguration {
    @Bean
    ScopeProbe scopeProbe(EntityManager entityManager) {
      return new ScopeProbe(entityManager);
    }
  }

  @Autowired private TenantService tenantService;
  @Autowired private ScopeProbe scopeProbe;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String firstExpiredTenantId;
  private String secondExpiredTenantId;
  private String bystanderTenantId;
  private String firstExpiredTenantMarkingId;
  private String secondExpiredTenantMarkingId;
  private String bystanderTenantMarkingId;

  @BeforeEach
  void seedTwoExpiredTenantsAndOneBystander() {
    jdbc = new JdbcTemplate(dataSource);
    scopeProbe.reset();
    firstExpiredTenantId = UUID.randomUUID().toString();
    secondExpiredTenantId = UUID.randomUUID().toString();
    bystanderTenantId = UUID.randomUUID().toString();
    firstExpiredTenantMarkingId = UUID.randomUUID().toString();
    secondExpiredTenantMarkingId = UUID.randomUUID().toString();
    bystanderTenantMarkingId = UUID.randomUUID().toString();

    // Past the grace period, so the purge picks them up. Two of them, so the loop iterates twice
    // and the scope has to be redefined between iterations.
    seedExpiredTenant(firstExpiredTenantId, "purge-scope-expired-first");
    seedExpiredTenant(secondExpiredTenantId, "purge-scope-expired-second");
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        bystanderTenantId,
        "purge-scope-bystander");
    seedMarking(firstExpiredTenantMarkingId, firstExpiredTenantId, "purge-expired-first");
    seedMarking(secondExpiredTenantMarkingId, secondExpiredTenantId, "purge-expired-second");
    seedMarking(bystanderTenantMarkingId, bystanderTenantId, "purge-bystander");
  }

  @AfterEach
  void cleanup() {
    jdbc.update(
        "DELETE FROM marking_definitions WHERE marking_definition_id IN (?, ?, ?)",
        firstExpiredTenantMarkingId,
        secondExpiredTenantMarkingId,
        bystanderTenantMarkingId);
    jdbc.update(
        "DELETE FROM tenants WHERE tenant_id IN (?, ?, ?)",
        firstExpiredTenantId,
        secondExpiredTenantId,
        bystanderTenantId);
  }

  private void seedExpiredTenant(String tenantId, String name) {
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at,"
            + " tenant_deleted_at) VALUES (?, ?, now(), now(), now() - interval '"
            + (TenantService.SOFT_DELETE_RETENTION_DAYS + 10)
            + " days')",
        tenantId,
        name);
  }

  /** Raw JDBC on purpose: it never reaches the statement inspector, so the seed is not filtered. */
  private void seedMarking(String markingId, String tenantId, String definition) {
    jdbc.update(
        "INSERT INTO marking_definitions (marking_definition_id, marking_definition_type,"
            + " marking_definition_definition, marking_definition_color,"
            + " marking_definition_order, tenant_id) VALUES (?, 'TLP', ?, '#ffffff', 0, ?)",
        markingId,
        definition,
        tenantId);
  }

  @Nested
  @DisplayName("each tenant's dependency cleanup runs under that tenant's scope")
  class PerTenantScope {

    @Test
    @DisplayName(
        "given two expired tenants, should run each dependency cleanup under that tenant's scope"
            + " only")
    void given_twoExpiredTenants_should_runEachCleanupUnderThatTenantsScopeOnly() {
      // Act
      tenantService.purgeExpiredTenants();

      // Assert - a scope set once before the loop leaves the second iteration on the first tenant.
      assertThat(scopeProbe.scopeSeenPerTenant)
          .as("each purged tenant's cleanup must carry exactly that tenant's scope")
          .containsEntry(firstExpiredTenantId, firstExpiredTenantId)
          .containsEntry(secondExpiredTenantId, secondExpiredTenantId);
    }

    @Test
    @DisplayName(
        "given two expired tenants with a marking each, should let every cleanup read its own"
            + " tenant's rows and no other tenant's")
    void given_twoExpiredTenantsWithAMarkingEach_should_letEachCleanupReadItsOwnRowsOnly() {
      // Act
      tenantService.purgeExpiredTenants();

      // Assert
      assertThat(scopeProbe.markingsSeenPerTenant.get(firstExpiredTenantId))
          .as(
              "unscoped the cleanup reads nothing; widened to every tenant it also reads the other"
                  + " tenants' markings")
          .containsExactly(firstExpiredTenantMarkingId);
      assertThat(scopeProbe.markingsSeenPerTenant.get(secondExpiredTenantId))
          .as("the second iteration must not still be reading the first purged tenant's rows")
          .containsExactly(secondExpiredTenantMarkingId);
    }
  }

  @Nested
  @DisplayName("each tenant row is deleted under that tenant's scope")
  class DeleteScope {

    @Test
    @DisplayName(
        "given two expired tenants, should delete each tenant row under that tenant's scope only")
    void given_twoExpiredTenants_should_deleteEachTenantRowUnderThatTenantsScopeOnly() {
      // Act
      tenantService.purgeExpiredTenants();

      // Assert - each delete carries its own tenant, not the other purged one nor the bystander.
      assertThat(scopeProbe.scopeSeenAtCommitPerTenant)
          .as("each tenant's purge transaction must still carry that tenant's scope at commit")
          .containsEntry(firstExpiredTenantId, firstExpiredTenantId)
          .containsEntry(secondExpiredTenantId, secondExpiredTenantId);
    }

    @Test
    @DisplayName("given two expired tenants, should actually remove both tenant rows")
    void given_twoExpiredTenants_should_actuallyRemoveBothTenantRows() {
      // Act
      int purged = tenantService.purgeExpiredTenants();

      // Assert
      assertThat(purged).isGreaterThanOrEqualTo(2);
      assertThat(countTenants(firstExpiredTenantId)).isZero();
      assertThat(countTenants(secondExpiredTenantId)).isZero();
      assertThat(countTenants(bystanderTenantId)).isEqualTo(1);
    }

    private int countTenants(String tenantId) {
      return jdbc.queryForObject(
          "SELECT count(*) FROM tenants WHERE tenant_id = ?", Integer.class, tenantId);
    }
  }
}
