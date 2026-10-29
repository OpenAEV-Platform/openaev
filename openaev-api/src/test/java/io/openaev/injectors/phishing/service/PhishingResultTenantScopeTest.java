package io.openaev.injectors.phishing.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Inject;
import io.openaev.database.model.PhishingResult;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.fixtures.InjectFixture;
import io.openaev.utils.fixtures.composers.InjectComposer;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Read and write isolation for {@code phishing_results} under an explicit tenant scope.
 *
 * <p>Why this class exists next to {@code HostedPublicApiIsolationTest}: a tracking token is
 * globally unique and 192 bits wide, and the public routes recover the owning tenant FROM the token
 * before they do anything tenant-filtered, so a request always arrives with the scope that matches
 * the row it is about. Every assertion driven through those routes therefore holds with {@code
 * phishing_results} de-activated: they prove write attribution, not isolation.
 *
 * <p>What the activation actually buys is the other direction, pinned here: when the scope names a
 * DIFFERENT tenant than the token's, {@code findByToken} has no tenant predicate of its own, so the
 * scope is the only thing that keeps the row out of reach. That is the case a mis-scoped caller
 * produces, and it is the one a scope can protect. There is no authenticated route reading this
 * table today ({@code PhishingResultRepository#findById} and {@code #findByInjectId} have no
 * production caller), so the scoped service entry points below are the real consumers.
 *
 * <p>Deliberately NOT {@code @Transactional}, same reasoning as {@code
 * HostedPublicApiIsolationTest} and for the same methods: both entry points under test open their
 * own {@code REQUIRES_NEW} transaction, which cannot see rows still uncommitted in a test-managed
 * one. Seeding and cleanup run through auto-committed JDBC.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=phishing_results")
@DisplayName("phishing_results isolation when the scope does not match the token's tenant")
class PhishingResultTenantScopeTest extends IntegrationTest {

  @Autowired private PhishingTrackingService phishingTrackingService;
  @Autowired private DataSource dataSource;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectRepository injectRepository;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String tokenA;
  private String tokenB;
  private Inject injectA;
  private Inject injectB;

  @BeforeEach
  void seedTwoTenantsWithTheirOwnTrackedResult() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("phishing-scope-a-" + UUID.randomUUID());
    tenantB = seedTenant("phishing-scope-b-" + UUID.randomUUID());
    injectA = persistInject(tenantA);
    injectB = persistInject(tenantB);
    tokenA = "tok-scope-a-" + UUID.randomUUID();
    tokenB = "tok-scope-b-" + UUID.randomUUID();
    seedPhishingResult(tenantA, tokenA, injectA.getId());
    seedPhishingResult(tenantB, tokenB, injectB.getId());
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM phishing_results WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    injectRepository.deleteAllById(List.of(injectA.getId(), injectB.getId()));
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    TenantContext.clearCurrentTenant();
  }

  @Nested
  @DisplayName("Resolving a token")
  class Resolve {

    @Test
    @DisplayName("scoped to tenant A: tenant B's token resolves to nothing")
    void given_tenantAScope_should_notResolveTenantBToken() {
      // Arrange & Act - the positive case first: the same call under the token's own tenant.
      Optional<PhishingResult> own =
          phishingTrackingService.resolveAndBackfillByToken(TxCtx.forTenant(tenantB), tokenB);

      // Assert
      assertThat(own)
          .as("B's token must resolve under B's scope, otherwise the negative below proves nothing")
          .isPresent();
      assertThat(
              phishingTrackingService.resolveAndBackfillByToken(TxCtx.forTenant(tenantA), tokenB))
          .as(
              "tenant B's tracked result must not resolve under tenant A's scope: the token lookup"
                  + " carries no tenant predicate, so the scope is the only thing hiding it")
          .isEmpty();
    }

    @Test
    @DisplayName("with no scope at all: even the token's own tenant resolves to nothing")
    void given_noScopeSet_should_failClosedOnItsOwnToken() {
      // Act & Assert - the control for the test above, on a different line than the active-tables
      // property: the resolve succeeds BECAUSE a scope is set.
      assertThat(phishingTrackingService.resolveAndBackfillByToken(TxCtx.missing(), tokenB))
          .as("an active-table token lookup with no tenant scope must fail closed")
          .isEmpty();
    }
  }

  @Nested
  @DisplayName("Marking a token as opened")
  class MarkOpened {

    @Test
    @DisplayName("scoped to tenant A: marking tenant B's token writes nothing")
    void given_tenantAScope_should_notMarkTenantBResultAsOpened() {
      // Arrange & Act - the positive case first, under the token's own tenant.
      phishingTrackingService.markOpened(TxCtx.forTenant(tenantA), tokenA, "10.0.0.1", "agent");

      // Assert
      assertThat(openedAt(tokenA)).as("A's own row must be marked under A's scope").isNotNull();

      // Act - the same write, scoped to the wrong tenant.
      phishingTrackingService.markOpened(TxCtx.forTenant(tenantA), tokenB, "10.0.0.1", "agent");

      // Assert - ground truth through raw JDBC, never the entity manager, whose SQL the inspector
      // rewrites.
      assertThat(openedAt(tokenB))
          .as("tenant B's row must not be marked by a write scoped to tenant A")
          .isNull();
    }
  }

  // -- helpers --

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }

  private Inject persistInject(String tenantId) {
    String previous = TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      return injectComposer.forInject(InjectFixture.getDefaultInject()).persist().get();
    } finally {
      if (previous == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previous);
      }
    }
  }

  private void seedPhishingResult(String tenantId, String token, String injectId) {
    jdbc.update(
        "INSERT INTO phishing_results (phishing_result_id, tenant_id, phishing_result_token,"
            + " phishing_result_inject, phishing_result_created_at, phishing_result_updated_at)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        UUID.randomUUID().toString(),
        tenantId,
        token,
        injectId,
        Timestamp.from(Instant.now()),
        Timestamp.from(Instant.now()));
  }

  private Timestamp openedAt(String token) {
    return jdbc.queryForObject(
        "SELECT phishing_result_opened_at FROM phishing_results WHERE phishing_result_token = ?",
        Timestamp.class,
        token);
  }
}
