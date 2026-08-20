package io.openaev.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.database.model.ImportMapper;
import io.openaev.database.repository.ImportMapperRepository;
import io.openaev.rest.exception.TenantWriteScopeException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proves the {@code allTenants()} intention end to end: it can never reach the scope channel by
 * itself (misuse fails loudly, including through the HTTP-style aspect path), write attribution
 * refuses it, and the primitive resolves it into an explicit list of active tenants, re-resolved at
 * every transaction (freshness). Also measures the GUC mechanics under a large explicit list.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=import_mappers")
@Import(TenantScopeAllTenantsIntegrationTest.ScopedServiceFixture.class)
@DisplayName("TxCtx.allTenants(): an intention resolved into an explicit list, never a wildcard")
class TenantScopeAllTenantsIntegrationTest extends IntegrationTest {

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private ImportMapperRepository importMapperRepository;
  @Autowired private TenantWriteScopeResolver writeScopeResolver;
  @Autowired private DataSource dataSource;
  @Autowired private ScopedService scopedService;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private String tenantA;
  private String tenantB;
  private String tenantC;
  private String mapperA;
  private String mapperB;
  private String mapperC;

  @BeforeEach
  void seedThreeTenantsWithOneMapperEach() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("all-a-" + UUID.randomUUID());
    tenantB = seedTenant("all-b-" + UUID.randomUUID());
    tenantC = seedTenant("all-c-" + UUID.randomUUID());
    mapperA = seedMapper(tenantA, "mapper-a");
    mapperB = seedMapper(tenantB, "mapper-b");
    mapperC = seedMapper(tenantC, "mapper-c");
  }

  @AfterEach
  void cleanup() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM import_mappers WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Test
  @DisplayName("allTenants() cannot serialize itself: toGuc() throws, on and off the aspect path")
  void unresolvedIntentionFailsLoudly() {
    IllegalStateException direct =
        assertThrows(IllegalStateException.class, () -> TxCtx.allTenants().toGuc());
    assertTrue(
        direct.getMessage().contains("unresolved intention"),
        "the refusal must name the reason: " + direct.getMessage());

    // HTTP-style misuse: a @Transactional method carrying the intention as its TxCtx argument.
    // The aspect finds it and tries to serialize it: it must fail loudly, not widen access.
    assertThrows(IllegalStateException.class, () -> scopedService.countMappers(TxCtx.allTenants()));
  }

  @Test
  @DisplayName("write attribution refuses allTenants(): a row belongs to exactly one tenant")
  void writeAttributionRefusesAllTenants() {
    assertThrows(
        TenantWriteScopeException.class,
        () -> writeScopeResolver.tenantForWrite(TxCtx.allTenants(), null));
  }

  @Test
  @DisplayName("a job under allTenants() sees the rows of several real tenants")
  void allTenantsSeesEveryTenantRow() {
    Boolean allVisible =
        tenantTx.execute(
            TxCtx.allTenants(),
            () ->
                importMapperRepository.findById(UUID.fromString(mapperA)).isPresent()
                    && importMapperRepository.findById(UUID.fromString(mapperB)).isPresent()
                    && importMapperRepository.findById(UUID.fromString(mapperC)).isPresent());
    assertTrue(allVisible, "the resolved intention must cover every active tenant's rows");
  }

  @Test
  @DisplayName("freshness: a tenant created between two transactions is visible to the next one")
  void freshnessAcrossTransactionsOfTheSameJob() {
    String lateMapperId = UUID.randomUUID().toString();

    Boolean visibleBeforeCreation =
        tenantTx.execute(
            TxCtx.allTenants(),
            () -> importMapperRepository.findById(UUID.fromString(lateMapperId)).isPresent());
    assertFalse(visibleBeforeCreation, "the row does not exist yet");

    // A new tenant (and its row) appears between two short transactions of the same job.
    String lateTenant = seedTenant("all-late-" + UUID.randomUUID());
    seedMapper(lateTenant, "mapper-late", lateMapperId);

    Boolean visibleAfterCreation =
        tenantTx.execute(
            TxCtx.allTenants(),
            () -> importMapperRepository.findById(UUID.fromString(lateMapperId)).isPresent());
    assertTrue(
        visibleAfterCreation,
        "the intention is re-resolved at every transaction: the new tenant must be visible");
  }

  @Test
  @DisplayName("narrowing inside a resolved allTenants scope trips the aspect's nesting guard")
  void narrowingInsideResolvedScopeTripsTheNestingGuard() {
    // The G1 conversion trap: a joined @Transactional service carrying a NARROWER TxCtx tries to
    // redefine the transaction's scope. The aspect refuses, and the trip POISONS the joined
    // transaction (rollback-only): catching the exception and carrying on in the same transaction
    // is not possible, the whole background unit dies at commit. Narrowing must be a new
    // transaction from the start.
    IllegalStateException refusal =
        assertThrows(
            IllegalStateException.class,
            () ->
                tenantTx.execute(
                    TxCtx.allTenants(),
                    () -> scopedService.countMappers(TxCtx.forTenant(tenantA))));
    assertTrue(
        refusal.getMessage().contains("must not redefine the scope"),
        "the aspect must name the rule: " + refusal.getMessage());

    // The working idiom: executeNew with the narrower ctx, a deliberate scope boundary.
    Long narrowed =
        tenantTx.execute(
            TxCtx.allTenants(),
            () ->
                tenantTx.executeNew(
                    TxCtx.forTenant(tenantA), () -> scopedService.countMappers(null)));
    assertEquals(1L, narrowed, "executeNew with the narrower ctx is the working idiom");
  }

  @Test
  @DisplayName("poisoning: catching a joined service's refusal and carrying on dies at commit")
  void catchingAJoinedRefusalStillDiesAtCommit() {
    // The general rule, beyond the nesting guard: any runtime exception from a joined
    // @Transactional service marks the whole background transaction rollback-only. Catching it
    // and carrying on works for nothing: the unit dies at commit. Recovery belongs around an
    // executeNew boundary, never inside the same transaction.
    assertThrows(
        UnexpectedRollbackException.class,
        () ->
            tenantTx.execute(
                TxCtx.allTenants(),
                () -> {
                  try {
                    scopedService.countMappers(TxCtx.forTenant(tenantA));
                  } catch (IllegalStateException expected) {
                    // Swallowed on purpose: the transaction is already poisoned anyway.
                  }
                  return "worked for nothing";
                }));
  }

  @Test
  @DisplayName("forEachTenant runs the work once per active tenant, each scoped to that tenant")
  void forEachTenantRunsPerTenantScoped() {
    Map<String, Long> seenCountByTenant = new HashMap<>();
    tenantTx.forEachTenant(
        tenantId -> {
          seenCountByTenant.put(tenantId, importMapperRepository.count());
        });
    // Each of our three seeded tenants was visited and, scoped to itself, saw exactly its own row.
    assertEquals(1L, seenCountByTenant.get(tenantA), "tenant A scoped to its own single row");
    assertEquals(1L, seenCountByTenant.get(tenantB), "tenant B scoped to its own single row");
    assertEquals(1L, seenCountByTenant.get(tenantC), "tenant C scoped to its own single row");
  }

  @Test
  @DisplayName(
      "forEachTenant rolls back only the failing tenant: its write is gone, the others committed")
  void forEachTenantRollsBackOnlyTheFailingTenant() {
    Map<String, String> mapperByTenant =
        Map.of(tenantA, mapperA, tenantB, mapperB, tenantC, mapperC);
    Set<String> ran = new HashSet<>();

    // Each tenant renames ITS OWN mapper inside its own scoped transaction; B throws AFTER writing.
    // Because each tenant runs in its own top-level transaction, B's write is rolled back on its
    // own while A and C commit: the flat-loop answer to the nesting-guard poisoning. A single
    // shared transaction would lose A and C too (poisoned at commit).
    RuntimeException aggregate =
        assertThrows(
            RuntimeException.class,
            () ->
                tenantTx.forEachTenant(
                    id -> {
                      // The loop covers every active tenant in the registry; act only on the three
                      // this test seeded, so the sole engineered failure is tenant B.
                      if (!mapperByTenant.containsKey(id)) {
                        return;
                      }
                      ran.add(id);
                      ImportMapper mapper =
                          importMapperRepository
                              .findById(UUID.fromString(mapperByTenant.get(id)))
                              .orElseThrow();
                      mapper.setName("renamed-by-loop");
                      importMapperRepository.save(mapper);
                      if (id.equals(tenantB)) {
                        throw new IllegalStateException("boom for tenant B after writing");
                      }
                    }));

    assertTrue(
        ran.contains(tenantA) && ran.contains(tenantC),
        "A and C must still have run despite B failing");
    // The failure is not swallowed: it survives as the aggregate's suppressed cause.
    assertEquals(1, aggregate.getSuppressed().length, "one tenant failed, one suppressed cause");
    assertTrue(
        aggregate.getSuppressed()[0].getMessage().contains("boom for tenant B"),
        "the original failure must be preserved: " + aggregate.getSuppressed()[0].getMessage());

    // Real rollback, read back through auto-committed JDBC: A and C committed, B was rolled back.
    assertEquals("renamed-by-loop", mapperNameOf(mapperA), "A committed its rename");
    assertEquals("renamed-by-loop", mapperNameOf(mapperC), "C committed its rename");
    assertEquals(
        "mapper-b", mapperNameOf(mapperB), "B's write was rolled back with its transaction");
  }

  private String mapperNameOf(String mapperId) {
    return jdbc.queryForObject(
        "SELECT mapper_name FROM import_mappers WHERE mapper_id = CAST(? AS uuid)",
        String.class,
        mapperId);
  }

  @Test
  @DisplayName("GUC mechanics hold under a large explicit list (measured)")
  void largeExplicitScopeStillResolvesRows() {
    List<String> ids = new ArrayList<>();
    for (int i = 0; i < 999; i++) {
      ids.add(UUID.randomUUID().toString());
    }
    ids.add(tenantA);

    long start = System.nanoTime();
    Boolean visible =
        tenantTx.execute(
            TxCtx.forTenants(ids),
            () -> importMapperRepository.findById(UUID.fromString(mapperA)).isPresent());
    long elapsedMs = (System.nanoTime() - start) / 1_000_000;

    assertTrue(visible, "a 1000-id scope must still match the tenant's row");
    // Generous ceiling to stay non-flaky; the measured value is the PR deliverable.
    assertTrue(elapsedMs < 10_000, "1000-id scope took " + elapsedMs + " ms");
    System.out.println("[measure] 1000-id GUC scope round-trip: " + elapsedMs + " ms");
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    seededTenants.add(id);
    return id;
  }

  private String seedMapper(String tenantId, String name) {
    return seedMapper(tenantId, name, UUID.randomUUID().toString());
  }

  private String seedMapper(String tenantId, String name, String id) {
    jdbc.update(
        "INSERT INTO import_mappers (mapper_id, mapper_name, mapper_inject_type_column, tenant_id)"
            + " VALUES (CAST(? AS uuid), ?, ?, ?)",
        id,
        name,
        "inject_type",
        tenantId);
    return id;
  }

  /** A @Transactional method carrying a TxCtx argument: the aspect reads and serializes it. */
  public static class ScopedService {
    private final ImportMapperRepository repository;

    public ScopedService(ImportMapperRepository repository) {
      this.repository = repository;
    }

    @Transactional
    public long countMappers(TxCtx ctx) {
      return repository.count();
    }
  }

  @TestConfiguration
  static class ScopedServiceFixture {
    @Bean
    ScopedService scopedService(ImportMapperRepository repository) {
      return new ScopedService(repository);
    }
  }
}
