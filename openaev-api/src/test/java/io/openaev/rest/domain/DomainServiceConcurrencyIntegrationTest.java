package io.openaev.rest.domain;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Domain;
import io.openaev.database.model.Tenant;
import io.openaev.rest.injector_contract.form.InjectorContractDomainDTO;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Two independent transactions creating the same domain in a tenant, against the real database: the
 * second one waits on the creation lock while the first one is open, then finds the first one's
 * domain once it commits. Rows are committed (each transaction has its own connection), in a tenant
 * seeded for the test and removed after it.
 */
@DisplayName("Domain creation by concurrent transactions")
class DomainServiceConcurrencyIntegrationTest extends IntegrationTest {

  @Autowired private DomainService domainService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;

  @BeforeEach
  void seedTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "domain-concurrency-" + tenantId);
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM domains WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
  }

  @ParameterizedTest(name = "\"{0}\" then \"{1}\" (batch: {2})")
  @CsvSource({
    "Space-Ops, Space-Ops, false",
    "Space-Ops, space ops, false",
    "Space-Ops, space ops, true"
  })
  @DisplayName("given_twoTransactionsCreatingTheSameName_should_createASingleDomain")
  void given_twoTransactionsCreatingTheSameName_should_createASingleDomain(
      String firstSpelling, String secondSpelling, boolean secondInBatch) throws Exception {
    // Arrange
    String suffix = UUID.randomUUID().toString().replace("-", "");
    String firstName = firstSpelling + " " + suffix;
    String secondName = secondSpelling + " " + suffix;
    CountDownLatch firstCreated = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // Act
      Future<String> first =
          pool.submit(
              () ->
                  tenantTx.execute(
                      TxCtx.forTenant(tenantId),
                      () -> {
                        String id =
                            domainService
                                .upsert(firstName, "#123456", new Tenant(tenantId))
                                .getId();
                        firstCreated.countDown();
                        await(releaseFirst);
                        return id;
                      }));
      assertThat(firstCreated.await(30, SECONDS)).as("the first domain is created").isTrue();
      Future<String> second =
          pool.submit(
              () ->
                  tenantTx.execute(
                      TxCtx.forTenant(tenantId), () -> upsertOnce(secondName, secondInBatch)));
      awaitWaitingOnCreationLock(DomainService.creationLockKey(tenantId, secondName));
      assertThat(second).as("the second creation waits for the first one").isNotDone();
      releaseFirst.countDown();

      // Assert
      assertThat(second.get(30, SECONDS)).isEqualTo(first.get(30, SECONDS));
    } finally {
      releaseFirst.countDown();
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForList(
                "SELECT domain_name FROM domains WHERE tenant_id = ?", String.class, tenantId))
        .containsExactly(firstName);
  }

  private String upsertOnce(String name, boolean batch) {
    if (!batch) {
      return domainService.upsert(name, "#654321", new Tenant(tenantId)).getId();
    }
    InjectorContractDomainDTO dto = new InjectorContractDomainDTO();
    dto.setName(name);
    dto.setColor("#654321");
    Set<Domain> resolved = domainService.upserts(Set.of(dto), tenantId);
    assertThat(resolved).hasSize(1);
    return resolved.iterator().next().getId();
  }

  /** Polls {@code pg_locks} until a session waits on the advisory lock of this key. */
  private void awaitWaitingOnCreationLock(long key) throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_locks WHERE locktype = 'advisory' AND NOT granted"
                  + " AND classid::bigint = ? AND objid::bigint = ?",
              Long.class,
              key >>> 32,
              key & 0xFFFF_FFFFL);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    fail("the second transaction never waited on the creation lock");
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(30, SECONDS)) {
        throw new IllegalStateException("the first transaction was never released");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
