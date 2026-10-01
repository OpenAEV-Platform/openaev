package io.openaev.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.openaev.IntegrationTest;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.Session;
import org.hibernate.internal.FilterImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The bridge aligns the ambient tenant and the v1 {@code tenantFilter} on the write tenant for the
 * duration of a call, then puts back exactly what it found: the filter armed on the same tenant, or
 * no filter at all when the surrounding transaction had none. The probe is a v1-filtered entity
 * ({@code Team}) read through JPQL: which rows come back is what the filter state means for the
 * rest of the transaction. It must still carry {@code @Filter("tenantFilter")}: once its table is
 * activated on v2 the filter is gone and the probe sees every row, so move it to another v1 entity
 * (it was {@code Organization} until organizations went v2).
 *
 * <p>Not {@code @Transactional}: one case runs inside the background primitive, which refuses an
 * active transaction. Rows are seeded and removed in auto-committed JDBC. The active-tables list is
 * pinned so the statement inspector never scopes {@code teams} here, whatever a shadow run arms:
 * the filter must be the only thing deciding what the probe sees.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@DisplayName("AmbientTenantBridge restores the filter state it found")
class AmbientTenantBridgeTest extends IntegrationTest {

  private static final String TENANT_FILTER = "tenantFilter";

  @Autowired private AmbientTenantBridge bridge;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String teamA;
  private String teamB;

  @BeforeEach
  void seedOneTeamPerTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("bridge-a-" + UUID.randomUUID());
    tenantB = seedTenant("bridge-b-" + UUID.randomUUID());
    teamA = seedTeam("bridge-a-" + UUID.randomUUID(), tenantA);
    teamB = seedTeam("bridge-b-" + UUID.randomUUID(), tenantB);
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    jdbc.update("DELETE FROM teams WHERE team_id IN (?, ?)", teamA, teamB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  @Nested
  @DisplayName("Inside a transaction opened by the background primitive, which arms no v1 filter")
  class InsideAPrimitiveTransaction {

    @Test
    @DisplayName("given_noFilterArmedAtEntry_should_leaveItDisabledAfterTheCall")
    void given_noFilterArmedAtEntry_should_leaveItDisabledAfterTheCall() {
      tenantTx.execute(
          TxCtx.forTenant(tenantB),
          () -> {
            // Arrange: the primitive sets the v2 scope only, and a job thread has no ambient tenant
            Session session = entityManager.unwrap(Session.class);
            assertNull(session.getEnabledFilter(TENANT_FILTER), "precondition: no v1 filter armed");
            assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");

            // Act
            List<String> seenDuring =
                bridge.callInTenant(tenantB, AmbientTenantBridgeTest.this::visibleTeams);

            // Assert
            assertEquals(
                List.of(teamB),
                seenDuring,
                "inside the call the v1 filter follows the write tenant");
            assertNull(
                session.getEnabledFilter(TENANT_FILTER),
                "the state found at entry, no filter, is restored");
            assertEquals(
                List.of(teamA, teamB),
                visibleTeams(),
                "the rest of the transaction is not narrowed to one tenant");
            assertFalse(TenantContext.hasCurrentTenant(), "the ambient tenant is cleared again");
            return null;
          });
    }
  }

  @Nested
  @DisplayName("Inside a transaction whose v1 filter is already armed")
  class InsideAFilteredTransaction {

    @Test
    @DisplayName("given_filterArmedOnTheAmbientTenant_should_followTheWriteTenantThenRestoreIt")
    void given_filterArmedOnTheAmbientTenant_should_followTheWriteTenantThenRestoreIt() {
      rawTransaction()
          .execute(
              status -> {
                // Arrange: what a @Transactional method leaves behind: ambient A, filter on A
                TenantContext.setCurrentTenant(tenantA);
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter(TENANT_FILTER).setParameter("tenantId", tenantA);

                // Act
                List<String> seenDuring =
                    bridge.callInTenant(tenantB, AmbientTenantBridgeTest.this::visibleTeams);

                // Assert
                assertEquals(
                    List.of(teamB),
                    seenDuring,
                    "inside the call the v1 filter follows the write tenant");
                assertEquals(tenantA, TenantContext.getCurrentTenant(), "ambient tenant restored");
                assertEquals(tenantA, enabledFilterTenant(session), "the filter is back on A");
                assertEquals(
                    List.of(teamA), visibleTeams(), "the rest of the transaction reads A again");
                return null;
              });
    }

    @Test
    @DisplayName(
        "given_filterArmedOnAnotherTenantThanTheAmbientOne_should_restoreThatTenantNotTheAmbientOne")
    void
        given_filterArmedOnAnotherTenantThanTheAmbientOne_should_restoreThatTenantNotTheAmbientOne() {
      rawTransaction()
          .execute(
              status -> {
                // Arrange: no ambient tenant (the default one by fallback) and the filter armed on
                // A by hand, as a job does before reading v1 entities
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter(TENANT_FILTER).setParameter("tenantId", tenantA);
                assertFalse(TenantContext.hasCurrentTenant(), "precondition: no ambient tenant");

                // Act
                bridge.callInTenant(tenantB, () -> null);

                // Assert
                assertEquals(
                    tenantA,
                    enabledFilterTenant(session),
                    "the filter goes back to the tenant it was armed on, not to the ambient one");
                assertEquals(
                    List.of(teamA), visibleTeams(), "the rest of the transaction reads A again");
                assertFalse(
                    TenantContext.hasCurrentTenant(), "the ambient tenant is cleared again");
                return null;
              });
    }
  }

  @Nested
  @DisplayName("When the ambient tenant already is the write tenant")
  class WhenTheAmbientTenantAlreadyMatches {

    @Test
    @DisplayName("given_noFilterArmedAndAmbientAlreadyTheWriteTenant_should_stillArmTheFilter")
    void given_noFilterArmedAndAmbientAlreadyTheWriteTenant_should_stillArmTheFilter() {
      tenantTx.execute(
          TxCtx.forTenant(tenantB),
          () -> {
            // Arrange: the primitive arms no v1 filter and the job runner has already set the
            // ambient tenant to the job's tenant, which is also the write tenant here
            Session session = entityManager.unwrap(Session.class);
            assertNull(session.getEnabledFilter(TENANT_FILTER), "precondition: no v1 filter armed");
            TenantContext.setCurrentTenant(tenantB);

            // Act
            List<String> seenDuring =
                bridge.callInTenant(tenantB, AmbientTenantBridgeTest.this::visibleTeams);

            // Assert
            assertEquals(
                List.of(teamB),
                seenDuring,
                "inside the call the v1 reads are confined to the write tenant, not unfiltered");
            assertNull(
                session.getEnabledFilter(TENANT_FILTER),
                "the state found at entry, no filter, is restored");
            return null;
          });
    }

    @Test
    @DisplayName(
        "given_filterArmedOnAnotherTenantAndAmbientAlreadyTheWriteTenant_should_followTheWriteTenant")
    void
        given_filterArmedOnAnotherTenantAndAmbientAlreadyTheWriteTenant_should_followTheWriteTenant() {
      rawTransaction()
          .execute(
              status -> {
                // Arrange: the transaction aspect armed the filter on A at entry, then the caller
                // moved the ambient tenant to B before reaching the bridge with B as write tenant
                Session session = entityManager.unwrap(Session.class);
                session.enableFilter(TENANT_FILTER).setParameter("tenantId", tenantA);
                TenantContext.setCurrentTenant(tenantB);

                // Act
                List<String> seenDuring =
                    bridge.callInTenant(tenantB, AmbientTenantBridgeTest.this::visibleTeams);

                // Assert
                assertEquals(
                    List.of(teamB),
                    seenDuring,
                    "inside the call the v1 reads follow the write tenant, not the filter armed at"
                        + " entry");
                assertEquals(
                    tenantA,
                    enabledFilterTenant(session),
                    "the filter goes back to the tenant it was armed on");
                assertEquals(tenantB, TenantContext.getCurrentTenant(), "ambient tenant restored");
                return null;
              });
    }
  }

  @Nested
  @DisplayName("Outside any transaction")
  class OutsideATransaction {

    @Test
    @DisplayName("given_noTransaction_should_onlySwapAndRestoreTheAmbientTenant")
    void given_noTransaction_should_onlySwapAndRestoreTheAmbientTenant() {
      // Arrange
      assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
      TenantContext.setCurrentTenant(tenantA);

      // Act
      String seenDuring = bridge.callInTenant(tenantB, TenantContext::getCurrentTenant);

      // Assert
      assertEquals(tenantB, seenDuring, "inside the call the ambient tenant is the write tenant");
      assertEquals(tenantA, TenantContext.getCurrentTenant(), "ambient tenant restored");
    }
  }

  /** The v1-filtered rows a JPQL read sees right now: the meaning of the current filter state. */
  private List<String> visibleTeams() {
    return entityManager
        .createQuery("select t.id from Team t where t.id in :ids order by t.id", String.class)
        .setParameter("ids", List.of(teamA, teamB))
        .getResultList();
  }

  private static String enabledFilterTenant(Session session) {
    return (String) ((FilterImpl) session.getEnabledFilter(TENANT_FILTER)).getParameter("tenantId");
  }

  private TransactionTemplate rawTransaction() {
    return new TransactionTemplate(transactionManager);
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

  private String seedTeam(String id, String tenantId) {
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, ?, ?)", id, id, tenantId);
    return id;
  }
}
