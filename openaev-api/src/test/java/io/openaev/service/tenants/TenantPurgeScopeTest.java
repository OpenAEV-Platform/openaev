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
 * the tenant being purged is visible through the ORM. One expired tenant and one bystander tenant,
 * each with a marking of their own, so the recorded scope distinguishes "the tenant being purged"
 * from both "no scope" and "every tenant".
 *
 * <p>NOT {@code @Transactional}: the purge is one unit of work owned by {@link TenantService}, and
 * a surrounding test transaction would also keep the tenant rows it deletes alive for the
 * assertions.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=marking_definitions")
@Import(TenantPurgeScopeTest.ScopeProbeConfiguration.class)
@DisplayName("Tenant purge tenant scope (marking_definitions armed)")
class TenantPurgeScopeTest extends IntegrationTest {

  /**
   * A {@link DependenciesManager} that only observes. {@link TenantService} injects every manager
   * bean in the context, so this is the real production loop, not a simulation of it.
   */
  static class ScopeProbe implements DependenciesManager {

    private final EntityManager entityManager;
    final Map<String, String> scopeSeenPerTenant = new LinkedHashMap<>();
    final Map<String, List<String>> markingsSeenPerTenant = new LinkedHashMap<>();

    ScopeProbe(EntityManager entityManager) {
      this.entityManager = entityManager;
    }

    @Override
    public void createDependencyForTenant(Tenant tenant) {
      // Nothing to provision: this manager only observes the delete side.
    }

    @Override
    public void deleteDependencyForTenant(String tenantId) {
      scopeSeenPerTenant.put(
          tenantId,
          (String)
              entityManager
                  .createNativeQuery("SELECT current_setting('app.current_tenants', true)")
                  .getSingleResult());
      markingsSeenPerTenant.put(
          tenantId,
          new ArrayList<>(
              entityManager
                  .createQuery("SELECT m.id FROM MarkingDefinition m ORDER BY m.id", String.class)
                  .getResultList()));
    }

    void reset() {
      scopeSeenPerTenant.clear();
      markingsSeenPerTenant.clear();
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
  private String expiredTenantId;
  private String bystanderTenantId;
  private String expiredTenantMarkingId;
  private String bystanderTenantMarkingId;

  @BeforeEach
  void seedOneExpiredTenantAndOneBystander() {
    jdbc = new JdbcTemplate(dataSource);
    scopeProbe.reset();
    expiredTenantId = UUID.randomUUID().toString();
    bystanderTenantId = UUID.randomUUID().toString();
    expiredTenantMarkingId = UUID.randomUUID().toString();
    bystanderTenantMarkingId = UUID.randomUUID().toString();

    // Past the grace period, so the purge picks it up.
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at,"
            + " tenant_deleted_at) VALUES (?, ?, now(), now(), now() - interval '"
            + (TenantService.SOFT_DELETE_RETENTION_DAYS + 10)
            + " days')",
        expiredTenantId,
        "purge-scope-expired");
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        bystanderTenantId,
        "purge-scope-bystander");
    seedMarking(expiredTenantMarkingId, expiredTenantId, "purge-expired");
    seedMarking(bystanderTenantMarkingId, bystanderTenantId, "purge-bystander");
  }

  @AfterEach
  void cleanup() {
    jdbc.update(
        "DELETE FROM marking_definitions WHERE marking_definition_id IN (?, ?)",
        expiredTenantMarkingId,
        bystanderTenantMarkingId);
    jdbc.update(
        "DELETE FROM tenants WHERE tenant_id IN (?, ?)", expiredTenantId, bystanderTenantId);
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
        "given an expired tenant, should run its dependency cleanup under that tenant's scope only")
    void given_anExpiredTenant_should_runItsCleanupUnderThatTenantsScopeOnly() {
      // Act
      tenantService.purgeExpiredTenants();

      // Assert
      assertThat(scopeProbe.scopeSeenPerTenant)
          .as("the cleanup of the purged tenant must carry exactly that tenant's scope")
          .containsEntry(expiredTenantId, expiredTenantId);
    }

    @Test
    @DisplayName(
        "given an expired tenant with a marking, should let its cleanup read that tenant's rows and"
            + " no other tenant's")
    void given_anExpiredTenantWithAMarking_should_letItsCleanupReadThatTenantsRowsOnly() {
      // Act
      tenantService.purgeExpiredTenants();

      // Assert
      assertThat(scopeProbe.markingsSeenPerTenant.get(expiredTenantId))
          .as(
              "unscoped the cleanup reads nothing; widened to every tenant it also reads the"
                  + " bystander tenant's marking")
          .containsExactly(expiredTenantMarkingId);
    }

    @Test
    @DisplayName("given an expired tenant, should actually remove its tenant row")
    void given_anExpiredTenant_should_actuallyRemoveItsTenantRow() {
      // Act
      int purged = tenantService.purgeExpiredTenants();

      // Assert - the batch delete spans the whole purged set and must not be narrowed by whichever
      // tenant the loop ended on.
      assertThat(purged).isPositive();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM tenants WHERE tenant_id = ?",
                  Integer.class,
                  expiredTenantId))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM tenants WHERE tenant_id = ?",
                  Integer.class,
                  bystanderTenantId))
          .isEqualTo(1);
    }
  }
}
