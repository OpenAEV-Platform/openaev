package io.openaev.rest.finding;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The dangerous shape of this activation, and the one the spec refused to leave at "probably safe".
 *
 * <p>{@code Inject.findings} is a {@code @OneToMany(cascade = ALL, orphanRemoval = true)}. If an
 * unscoped read initialises that collection, {@code can_access_tenant} makes it come back EMPTY,
 * and a later save of the same Inject could interpret the emptiness as "every finding was removed"
 * and orphan-delete real rows. That is silent data loss, not a degraded read.
 *
 * <p>The spec probed this and got {@code before=2, after=2}, but with the collection never
 * initialised, which is exactly the case Hibernate leaves alone. This test initialises it on
 * purpose, which is the only arrangement where the question is actually asked.
 *
 * <p>Ground truth is raw JDBC: a scoped read could not see the rows either way, so it would report
 * success whether they survived or not.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=findings")
@WithMockUser(isAdmin = true)
@DisplayName("an unscoped read of Inject.findings must not orphan-delete the real findings")
class FindingOrphanRemovalTest extends IntegrationTest {

  @Autowired private InjectRepository injectRepository;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private EntityManager entityManager;
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String injectId;

  @BeforeEach
  void seedAnInjectWithTwoFindings() {
    jdbc = new JdbcTemplate(dataSource);
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              try {
                tenantId =
                    tenantHelper.createTenantWithCurrentUser("orph-" + UUID.randomUUID()).getId();
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
              Inject inject = InjectFixture.getDefaultInject();
              inject.setTenant(new Tenant(tenantId));
              entityManager.persist(inject);
              entityManager.flush();
              injectId = inject.getId();
              for (int i = 0; i < 2; i++) {
                jdbc.update(
                    "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
                        + " finding_name, finding_inject_id, finding_created_at,"
                        + " finding_updated_at, tenant_id)"
                        + " VALUES (?, 'hostname', 'Text', ?, 'orphan probe', ?, now(), now(), ?)",
                    UUID.randomUUID().toString(),
                    "orphan-" + i + "-" + UUID.randomUUID(),
                    injectId,
                    tenantId);
              }
            });
  }

  @AfterEach
  void sweep() {
    jdbc.update("DELETE FROM findings WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenantId);
    // Committed by createTenantWithCurrentUser and never rolled back, this class not being
    // transactional. Own rows first, as deleteCommittedTenants documents, then the tenant (#7873).
    tenantHelper.deleteCommittedTenants(tenantId);
  }

  @Test
  @DisplayName("initialising the collection unscoped, then saving, leaves both findings in place")
  void unscopedInitialisationThenSaveDoesNotDeleteFindings() {
    long before = rawCount();
    assertEquals(2, before, "the seed must be visible to raw JDBC");

    // No TxCtx anywhere: the collection loads through can_access_tenant with an empty scope.
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              Inject inject = injectRepository.findById(injectId).orElseThrow();
              // Touch it: this is what the spec's probe never did.
              int seenUnscoped = inject.getFindings().size();
              assertEquals(
                  0,
                  seenUnscoped,
                  "an unscoped read is expected to see none of them; if it now sees two, the scope"
                      + " handling changed and this test's premise must be revisited");
              inject.setUpdatedAt(java.time.Instant.now());
              injectRepository.save(inject);
            });

    assertEquals(
        2,
        rawCount(),
        "both findings must survive: an empty collection loaded through fail-closed must never be"
            + " read as 'they were all removed' and orphan-deleted");
  }

  private long rawCount() {
    Long n =
        jdbc.queryForObject(
            "SELECT count(*) FROM findings WHERE finding_inject_id = ?", Long.class, injectId);
    return n == null ? 0 : n;
  }
}
