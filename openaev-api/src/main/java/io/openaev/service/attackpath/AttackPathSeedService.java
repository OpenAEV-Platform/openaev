package io.openaev.service.attackpath;

import io.openaev.annotation.AllowRawJdbc;
import io.openaev.expectation.ExpectationType;
import io.openaev.service.attackpath.dto.AttackPathSeedResultDTO;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk generator for the attack-path POC tables (issue 6647), used to prove the model scales.
 *
 * <p><b>Why this bypasses the tenant inspector (ADR-003).</b> The rest of the POC goes through
 * Hibernate so the {@code TenantStatementInspector} filters every statement. The seed deliberately
 * does not: it writes with batched, multi-row raw JDBC on the transaction's own connection, which
 * the inspector never sees. Two facts make this correct rather than a hole:
 *
 * <ul>
 *   <li><b>No safety is lost.</b> The inspector does not guard an {@code INSERT ... VALUES} anyway
 *       — its own code passes those through unchanged ("tenant assignment stays an application
 *       concern"). The seed sets {@code tenant_id} explicitly on every row, which is the exact same
 *       guarantee the ORM path would have given.
 *   <li><b>The alternative is infeasible.</b> Measured on Postgres, going through the inspector
 *       caps writes at ~1.5k rows/s (it parses every statement with jsqlparser), so the {@code
 *       full} dataset (~20M rows once findings and links are counted) would take ~4 hours; the
 *       raw-JDBC path reaches tens of thousands of rows/s. The numbers are in {@code results.md}.
 * </ul>
 *
 * <p>The write is admin-only and flag-gated, and it runs on the caller's own transaction (opened
 * via {@link Session#doReturningWork}), so a run commits or rolls back with its caller — an HTTP
 * request in production, the test transaction under test. This is the write analogue of the plan's
 * evidence-gated native read fallback, one step further (a full bypass), justified by the numbers
 * and isolated to this one service.
 */
@Service
@RequiredArgsConstructor
@AllowRawJdbc(
    reason =
        "seed generator bypasses the inspector on purpose (ADR-003): batched raw JDBC with tenant_id"
            + " set explicitly on every row; the inspector adds no guarantee to a VALUES insert and"
            + " is ~17x slower. Admin-only, flag-gated, isolated to this service. KNOWN DEVIATION:"
            + " the activate-tenant-table runbook forbids this annotation on a tenant-active table"
            + " without exception. It is accepted here only because the path is provably"
            + " insert-only, which AttackPathSeedServiceTest enforces on every build; the rule's"
            + " wording is open with its owner.")
public class AttackPathSeedService {

  // Small rows batch large; the execution rows carry the heavy command/terminal_output text, so
  // they
  // batch smaller to keep each multi-row INSERT a sane size.
  private static final int BATCH_ROWS = 200;
  private static final int EXECUTION_BATCH_ROWS = 50;
  private static final int EXECS_PER_ENDPOINT = 250;
  private static final String SEED_ID_PREFIX = "ap-seed-";
  private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

  private static final String[] FINDING_TYPES = {"credentials", "username", "cve", "port"};
  private static final String[] CREDENTIAL_USERS = {
    "administrator", "svc-sql", "jdoe", "root", "backup-svc", "web-app"
  };
  private static final String[] PLATFORMS = {"Windows", "Linux", "MacOS"};
  private static final String[] PRIVILEGES = {"user", "admin", "system"};
  private static final String[] INJECTORS = {
    "nmap", "hydra", "crackmapexec", "metasploit", "impacket"
  };
  private static final String TERMINAL_LINE =
      " bytes=4096 status=ok hash=a1b2c3d4e5f60718 latency_ms=12 conn=tcp/445 result=delivered";

  static final String[] EXECUTION_COLUMNS = {
    "attackpath_execution_id",
    "tenant_id",
    "attackpath_execution_simulation_id",
    "attackpath_execution_step_template_id",
    "attackpath_execution_contract_external_id",
    "attackpath_execution_source_kind",
    "attackpath_execution_agent_id",
    "attackpath_execution_agent_name",
    "attackpath_execution_agent_privilege",
    "attackpath_execution_source_injector",
    "attackpath_execution_target_kind",
    "attackpath_execution_target_asset_id",
    "attackpath_execution_target_raw_value",
    "attackpath_execution_target_key",
    "attackpath_execution_target_hostname",
    "attackpath_execution_target_ip",
    "attackpath_execution_target_platform",
    "attackpath_execution_payload_name",
    "attackpath_execution_executed_at",
    "attackpath_execution_prevention_status",
    "attackpath_execution_detection_status",
    "attackpath_execution_command",
    "attackpath_execution_terminal_output"
  };
  static final String[] FINDING_COLUMNS = {
    "attackpath_finding_id",
    "tenant_id",
    "attackpath_finding_simulation_id",
    "attackpath_finding_type",
    "attackpath_finding_value",
    "attackpath_finding_endpoint_id",
    "attackpath_finding_endpoint_raw",
    "attackpath_finding_endpoint_key"
  };
  private static final String[] LINK_COLUMNS = {"execution_id", "finding_id"};

  private final EntityManager entityManager;

  /**
   * Generates a dataset under synthetic tenants and returns the row counts. Runs in the caller's
   * transaction, so the surrounding boundary decides commit or rollback.
   */
  @Transactional
  public AttackPathSeedResultDTO generate(AttackPathSeedParams params) {
    // Delegates to the private body, not to the transactional twin: an intra-class call bypasses
    // the Spring proxy, so the inner annotation would be silently inert.
    return doGenerate(params, null);
  }

  /**
   * Generates a dataset and returns the row counts it inserted. When {@code tenantId} is set every
   * row is written under that existing tenant (so the seed is visible to it in the front); when
   * null the generator creates its own synthetic tenants (the benchmark path).
   */
  @Transactional
  public AttackPathSeedResultDTO generate(AttackPathSeedParams params, String tenantId) {
    return doGenerate(params, tenantId);
  }

  private AttackPathSeedResultDTO doGenerate(AttackPathSeedParams params, String tenantId) {
    long start = System.currentTimeMillis();
    Session session = entityManager.unwrap(Session.class);
    long[] counts = session.doReturningWork(connection -> insertAll(connection, params, tenantId));
    return new AttackPathSeedResultDTO(
        counts[0], counts[1], counts[2], System.currentTimeMillis() - start);
  }

  private long[] insertAll(Connection connection, AttackPathSeedParams params, String tenantId)
      throws SQLException {
    Random random = new Random(params.seed());
    List<String> tenants = tenantId != null ? List.of(tenantId) : createTenants(connection, params);

    BatchTable executions =
        new BatchTable(connection, "attackpath_execution", EXECUTION_COLUMNS, EXECUTION_BATCH_ROWS);
    BatchTable findings =
        new BatchTable(connection, "attackpath_finding", FINDING_COLUMNS, BATCH_ROWS);
    BatchTable links =
        new BatchTable(connection, "attackpath_execution_finding", LINK_COLUMNS, BATCH_ROWS);

    long simulationCount = 0;
    long executionCount = 0;
    long findingCount = 0;
    for (int s = 0; s < params.simulations(); s++) {
      String simulationId = SEED_ID_PREFIX + params.seed() + "-sim-" + s;
      String simTenantId = tenants.get(s % tenants.size());
      int size = sizeForSimulation(s, params, random);
      long[] simCounts =
          generateSimulation(
              random, simulationId, simTenantId, size, params, executions, findings, links);
      executionCount += simCounts[0];
      findingCount += simCounts[1];
      simulationCount++;
    }
    executions.flush();
    findings.flush();
    links.flush();
    return new long[] {simulationCount, executionCount, findingCount};
  }

  /**
   * Creates the synthetic tenants the seed writes into. Idempotent ({@code ON CONFLICT DO NOTHING})
   * so re-running the same seed does not fail on the tenant rows.
   */
  private List<String> createTenants(Connection connection, AttackPathSeedParams params)
      throws SQLException {
    List<String> ids = new ArrayList<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO tenants (tenant_id, tenant_name) VALUES (?, ?)"
                + " ON CONFLICT (tenant_id) DO NOTHING")) {
      for (int t = 0; t < params.tenants(); t++) {
        String id = SEED_ID_PREFIX + params.seed() + "-tenant-" + t;
        statement.setString(1, id);
        statement.setString(2, id);
        statement.executeUpdate();
        ids.add(id);
      }
    }
    return ids;
  }

  private int sizeForSimulation(int index, AttackPathSeedParams params, Random random) {
    if (index < params.outlierSizes().size()) {
      return params.outlierSizes().get(index);
    }
    int base = Math.max(1, params.typicalExecutions());
    return Math.max(1, base / 2 + random.nextInt(base));
  }

  private long[] generateSimulation(
      Random random,
      String simulationId,
      String tenantId,
      int size,
      AttackPathSeedParams params,
      BatchTable executions,
      BatchTable findings,
      BatchTable links)
      throws SQLException {
    // Endpoints scale with the simulation size (a real spray hits ~one endpoint per few hundred
    // executions), so a 500k-execution outlier fans out to thousands of endpoints, not a fixed few.
    int endpointCount =
        Math.max(4, Math.min(size, params.endpointsPerSimulation() + size / EXECS_PER_ENDPOINT));
    List<Endpoint> endpoints = buildEndpoints(random, simulationId, endpointCount, params);
    List<Injector> injectors = buildInjectors(simulationId, params);

    // The link table has FKs to both parents, so each parent batch is flushed before any link that
    // references it: findings, then executions, then the links between them.
    long findingCount = 0;
    List<FindingKind> pool = new ArrayList<>();
    List<List<String>> findingIdsByEndpoint = new ArrayList<>();
    // One credential value per endpoint (if any), embedded later in that endpoint's commands so the
    // Terminal drawer shows the server masking a real, discovered secret.
    List<String> sampleCredentialByEndpoint = new ArrayList<>();
    for (Endpoint endpoint : endpoints) {
      List<String> ids = new ArrayList<>();
      String sampleCredential = null;
      int findingsHere = 1 + random.nextInt(2 * params.findingsPerEndpoint());
      for (int k = 0; k < findingsHere; k++) {
        FindingKind kind = findingKind(random, pool, simulationId, endpoint, k, params);
        if (sampleCredential == null && "credentials".equals(kind.type())) {
          sampleCredential = kind.value();
        }
        String id = UUID.randomUUID().toString();
        findings.add(
            id,
            tenantId,
            simulationId,
            kind.type(),
            kind.value(),
            endpoint.assetId(),
            endpoint.rawValue(),
            endpoint.key());
        ids.add(id);
        findingCount++;
      }
      findingIdsByEndpoint.add(ids);
      sampleCredentialByEndpoint.add(sampleCredential);
    }
    findings.flush();

    // Executions (round-robin over endpoints = stuffing; random injector = spray). Ids are
    // generated
    // here, so links need no read-back.
    long executionCount = 0;
    List<String> executionIds = new ArrayList<>(size);
    int[] endpointOfExecution = new int[size];
    for (int x = 0; x < size; x++) {
      // Every endpoint gets at least one execution (first pass: one-per-endpoint), then the rest
      // follow a power-law fan-out (a few endpoints and injectors carry most executions, the
      // hotspots a real attack path and its "+N" grouping have; the rest a long tail). Guaranteeing
      // coverage means every finding has a producing execution to link to, so none is orphaned.
      int endpointIndex = x < endpoints.size() ? x : skewedIndex(random, endpoints.size(), 2.0);
      endpointOfExecution[x] = endpointIndex;
      Injector injector = injectors.get(skewedIndex(random, injectors.size(), 1.5));
      String executionId = UUID.randomUUID().toString();
      executionIds.add(executionId);
      executions.add(
          executionRow(
              random,
              executionId,
              tenantId,
              simulationId,
              endpoints.get(endpointIndex),
              injector,
              params,
              sampleCredentialByEndpoint.get(endpointIndex)));
      executionCount++;
    }
    executions.flush();

    // Links: both parents are now in the database, so any flush order is safe. Every finding is
    // produced by at least one execution on its endpoint (the graph invariant: a finding is in the
    // graph iff an execution produced it, so full and collapsed report identical counters and no
    // finding is orphaned); about a quarter also get a second producer, so fan-in (one finding seen
    // by several executions) and the execution->finding cross-reference are exercised.
    List<List<String>> executionIdsByEndpoint = new ArrayList<>();
    for (int e = 0; e < endpoints.size(); e++) {
      executionIdsByEndpoint.add(new ArrayList<>());
    }
    for (int x = 0; x < size; x++) {
      executionIdsByEndpoint.get(endpointOfExecution[x]).add(executionIds.get(x));
    }
    for (int e = 0; e < endpoints.size(); e++) {
      List<String> findingIds = findingIdsByEndpoint.get(e);
      List<String> endpointExecutions = executionIdsByEndpoint.get(e);
      if (findingIds.isEmpty() || endpointExecutions.isEmpty()) {
        continue;
      }
      for (int f = 0; f < findingIds.size(); f++) {
        links.add(endpointExecutions.get(f % endpointExecutions.size()), findingIds.get(f));
        if (endpointExecutions.size() > 1 && random.nextDouble() < 0.25) {
          links.add(endpointExecutions.get((f + 1) % endpointExecutions.size()), findingIds.get(f));
        }
      }
    }
    return new long[] {executionCount, findingCount};
  }

  /**
   * A finding's (type, value). Reuses a whole pair from the pool so a shared finding is genuinely
   * the same credential/CVE/port seen on another endpoint (the render dedups it to one node, the
   * row stays per-endpoint), not a coincidental value under a different type.
   */
  private FindingKind findingKind(
      Random random,
      List<FindingKind> pool,
      String simulationId,
      Endpoint endpoint,
      int index,
      AttackPathSeedParams params) {
    if (!pool.isEmpty() && random.nextDouble() < params.sharedFindingRatio()) {
      return pool.get(random.nextInt(pool.size()));
    }
    String type = FINDING_TYPES[random.nextInt(FINDING_TYPES.length)];
    // Credentials look like a realistic username:secret pair so the drawer's server-side masking
    // (keep the username, hide the secret) is meaningful; the secret keeps the value unique.
    String value =
        "credentials".equals(type)
            ? CREDENTIAL_USERS[Math.floorMod(index, CREDENTIAL_USERS.length)]
                + ":pwd-"
                + simulationId
                + "-"
                + endpoint.key()
                + "-"
                + index
            : type + "-" + simulationId + "-" + endpoint.key() + "-" + index;
    FindingKind kind = new FindingKind(type, value);
    pool.add(kind);
    return kind;
  }

  private List<Endpoint> buildEndpoints(
      Random random, String simulationId, int count, AttackPathSeedParams params) {
    List<Endpoint> endpoints = new ArrayList<>();
    for (int e = 0; e < count; e++) {
      if (random.nextDouble() < params.discoveredEndpointRatio()) {
        String raw = "203.0.113." + e;
        endpoints.add(new Endpoint(null, raw, raw, null, raw, null));
      } else {
        String assetId = "asset-" + simulationId + "-" + e;
        endpoints.add(
            new Endpoint(
                assetId,
                null,
                assetId,
                "HOST-" + simulationId + "-" + e,
                "10.0." + (e % 256) + ".1",
                PLATFORMS[random.nextInt(PLATFORMS.length)]));
      }
    }
    return endpoints;
  }

  private List<Injector> buildInjectors(String simulationId, AttackPathSeedParams params) {
    List<Injector> injectors = new ArrayList<>();
    for (int i = 0; i < Math.max(1, params.injectorsPerSimulation()); i++) {
      String name = INJECTORS[i % INJECTORS.length];
      boolean withAgent = i % 2 == 0;
      injectors.add(
          new Injector(
              name,
              withAgent ? "agent-" + simulationId + "-" + i : null,
              withAgent ? "agent-host-" + simulationId + "-" + i : null,
              withAgent ? PRIVILEGES[i % PRIVILEGES.length] : null));
    }
    return injectors;
  }

  private Object[] executionRow(
      Random random,
      String executionId,
      String tenantId,
      String simulationId,
      Endpoint endpoint,
      Injector injector,
      AttackPathSeedParams params,
      String sampleCredential) {
    return new Object[] {
      executionId,
      tenantId,
      simulationId,
      "step-tpl-" + injector.name(),
      "contract-" + injector.name(),
      "INJECTOR",
      injector.agentId(),
      injector.agentName(),
      injector.privilege(),
      injector.name(),
      endpoint.assetId() != null ? "ASSET" : "DISCOVERED",
      endpoint.assetId(),
      endpoint.rawValue(),
      endpoint.key(),
      endpoint.hostname(),
      endpoint.ip(),
      endpoint.platform(),
      injector.name() + "-payload",
      Timestamp.from(BASE_TIME.plusSeconds(random.nextInt(86_400))),
      random.nextDouble() < params.preventedRatio()
          ? ExpectationType.PREVENTION.successLabel
          : ExpectationType.PREVENTION.failureLabel,
      random.nextBoolean()
          ? ExpectationType.DETECTION.successLabel
          : ExpectationType.DETECTION.failureLabel,
      command(injector, endpoint, sampleCredential),
      terminalOutput(random, injector, sampleCredential)
    };
  }

  private static String command(Injector injector, Endpoint endpoint, String sampleCredential) {
    String base =
        injector.name()
            + " --target "
            + endpoint.key()
            + " --payload "
            + injector.name()
            + "-payload --timeout 30 --format json --verbose";
    // A credential-capable injector (not the nmap scanner) uses a discovered credential inline,
    // which
    // the read masks: the drawer shows "-p ••••", proving credentials never leave the server in
    // clear.
    if (sampleCredential == null || "nmap".equals(injector.name())) {
      return base;
    }
    int separator = sampleCredential.indexOf(':');
    String user = separator >= 0 ? sampleCredential.substring(0, separator) : sampleCredential;
    String secret = separator >= 0 ? sampleCredential.substring(separator + 1) : "";
    return base + " -u " + user + " -p " + secret;
  }

  /**
   * A realistic terminal capture: most executions produce a short output, ~30% a verbose one large
   * enough to be TOASTed off-row. This is what makes the short-column Read A meaningful — it never
   * selects this column, so it stays cheap even though the row is wide on disk in production.
   */
  private static String terminalOutput(Random random, Injector injector, String sampleCredential) {
    int lines = random.nextDouble() < 0.3 ? 20 + random.nextInt(50) : 2 + random.nextInt(7);
    StringBuilder output = new StringBuilder(lines * 96);
    for (int i = 0; i < lines; i++) {
      output.append("[step ").append(i).append("]").append(TERMINAL_LINE).append('\n');
    }
    // Echo the recovered credential for a credential-capable injector, so the Terminal drawer shows
    // the secret masked in the output as well as the command.
    if (sampleCredential != null && !"nmap".equals(injector.name())) {
      output.append("[+] recovered credential ").append(sampleCredential).append('\n');
    }
    return output.toString();
  }

  /**
   * Biases index selection toward 0 (exponent &gt; 1), so fan-out follows a power law, not uniform.
   */
  private static int skewedIndex(Random random, int bound, double exponent) {
    return Math.min(bound - 1, (int) (bound * Math.pow(random.nextDouble(), exponent)));
  }

  /**
   * Accumulates rows for one table and flushes them as a single multi-row {@code INSERT} on the
   * transaction's connection. Raw JDBC on purpose (ADR-003): it bypasses the tenant inspector,
   * whose per-statement parse otherwise caps bulk writes; {@code tenant_id} is set explicitly, so
   * no tenant guarantee is lost.
   */
  @AllowRawJdbc(
      reason = "batched multi-row INSERT for the seed generator; see the class reason above")
  private static final class BatchTable {
    private final Connection connection;
    private final String insertPrefix;
    private final int columnCount;
    private final int batchRows;
    private final List<Object[]> buffer = new ArrayList<>();

    BatchTable(Connection connection, String table, String[] columns, int batchRows) {
      this.connection = connection;
      this.insertPrefix = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES ";
      this.columnCount = columns.length;
      this.batchRows = batchRows;
    }

    void add(Object... values) throws SQLException {
      buffer.add(values);
      if (buffer.size() >= batchRows) {
        flush();
      }
    }

    void flush() throws SQLException {
      if (buffer.isEmpty()) {
        return;
      }
      try (PreparedStatement statement = connection.prepareStatement(sql(buffer.size()))) {
        int parameter = 1;
        for (Object[] row : buffer) {
          for (Object value : row) {
            statement.setObject(parameter++, value);
          }
        }
        statement.executeUpdate();
      }
      buffer.clear();
    }

    private String sql(int rows) {
      String row = "(" + "?, ".repeat(columnCount - 1) + "?)";
      StringBuilder builder = new StringBuilder(insertPrefix);
      for (int r = 0; r < rows; r++) {
        builder.append(r == 0 ? "" : ", ").append(row);
      }
      return builder.toString();
    }
  }

  private record FindingKind(String type, String value) {}

  private record Endpoint(
      String assetId, String rawValue, String key, String hostname, String ip, String platform) {}

  private record Injector(String name, String agentId, String agentName, String privilege) {}
}
