package io.openaev.execution;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Committed rows for the execution trace scope tests: a tenant with a simulation, a pending inject
 * and its status, an agent of an asset, a team of a player, and traces written straight into {@code
 * execution_traces}. Plain JDBC, so the rows exist whatever tenant scope the test or the code under
 * test carries, and {@link #sweep} removes exactly what was seeded.
 */
public class ExecutionTraceScopeSeed {

  /** What one seeded tenant holds. */
  public record Tenant(
      String id,
      String injectId,
      String statusId,
      String assetId,
      String agentId,
      String teamId,
      String playerId) {}

  private final JdbcTemplate jdbc;
  private final List<Tenant> tenants = new ArrayList<>();

  public ExecutionTraceScopeSeed(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** A tenant whose inject is pending since an hour, with an agent, a team and its player. */
  public Tenant seedTenant(String label) {
    String tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "trace-scope-" + label + "-" + tenantId);
    String contractId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
            + " tenant_id) VALUES (?, '{}', ?)",
        contractId,
        tenantId);
    String exerciseId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status,"
            + " exercise_start_date, tenant_id) VALUES (?, ?, 'RUNNING', ?, ?)",
        exerciseId,
        "trace-scope-" + label,
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)),
        tenantId);
    String injectId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects (inject_id, inject_title, inject_all_teams, inject_enabled,"
            + " inject_depends_duration, inject_exercise, inject_injector_contract, tenant_id)"
            + " VALUES (?, ?, false, true, 0, ?, ?, ?)",
        injectId,
        "trace-scope-" + label,
        exerciseId,
        contractId,
        tenantId);
    String statusId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injects_statuses (status_id, status_inject, status_name, tracking_sent_date)"
            + " VALUES (?, ?, 'PENDING', ?)",
        statusId,
        injectId,
        Timestamp.from(Instant.now().minus(1, ChronoUnit.HOURS)));
    String assetId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_type, asset_name, tenant_id) VALUES (?, 'Endpoint',"
            + " ?, ?)",
        assetId,
        "trace-scope-asset-" + label,
        tenantId);
    String agentId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO agents (agent_id, agent_asset, agent_privilege, agent_deployment_mode,"
            + " agent_executed_by_user, tenant_id) VALUES (?, ?, 'admin', 'service', 'root', ?)",
        agentId,
        assetId,
        tenantId);
    String teamId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO teams (team_id, team_name, tenant_id) VALUES (?, ?, ?)",
        teamId,
        "trace-scope-team-" + label,
        tenantId);
    String playerId = jdbc.queryForObject("SELECT user_id FROM users LIMIT 1", String.class);
    jdbc.update("INSERT INTO users_teams (team_id, user_id) VALUES (?, ?)", teamId, playerId);
    Tenant tenant = new Tenant(tenantId, injectId, statusId, assetId, agentId, teamId, playerId);
    tenants.add(tenant);
    return tenant;
  }

  /**
   * Inserts a trace of the tenant's inject status, in the tenant's own column, aged {@code
   * ageDays}.
   */
  public String insertTrace(
      Tenant tenant,
      String agentId,
      String action,
      String status,
      int ageDays,
      String... identifiers) {
    String id = UUID.randomUUID().toString();
    Timestamp at = Timestamp.from(Instant.now().minus(ageDays, ChronoUnit.DAYS));
    jdbc.update(
        con -> {
          var ps =
              con.prepareStatement(
                  "INSERT INTO execution_traces (execution_trace_id, execution_inject_status_id,"
                      + " execution_agent_id, execution_message, execution_action,"
                      + " execution_status, execution_time, execution_context_identifiers,"
                      + " execution_created_at, execution_updated_at, tenant_id)"
                      + " VALUES (?, ?, ?, 'trace scope test', ?, ?, ?, ?, ?, ?, ?)");
          ps.setString(1, id);
          ps.setString(2, tenant.statusId());
          ps.setString(3, agentId);
          ps.setString(4, action);
          ps.setString(5, status);
          ps.setTimestamp(6, at);
          ps.setArray(7, con.createArrayOf("text", identifiers));
          ps.setTimestamp(8, at);
          ps.setTimestamp(9, at);
          ps.setString(10, tenant.id());
          return ps;
        });
    return id;
  }

  /** The tenants of the traces matching a status, one entry per trace. */
  public List<String> traceTenantsOfStatus(String statusId) {
    return jdbc.queryForList(
        "SELECT tenant_id FROM execution_traces WHERE execution_inject_status_id = ?",
        String.class,
        statusId);
  }

  public boolean traceExists(String traceId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT count(*) > 0 FROM execution_traces WHERE execution_trace_id = ?",
            Boolean.class,
            traceId));
  }

  public void sweep() {
    for (Tenant tenant : tenants) {
      jdbc.update("DELETE FROM execution_traces WHERE tenant_id = ?", tenant.id());
      jdbc.update("DELETE FROM users_teams WHERE team_id = ?", tenant.teamId());
      jdbc.update("DELETE FROM teams WHERE tenant_id = ?", tenant.id());
      jdbc.update("DELETE FROM agents WHERE tenant_id = ?", tenant.id());
      jdbc.update("DELETE FROM assets WHERE tenant_id = ?", tenant.id());
      jdbc.update(
          "DELETE FROM injects_statuses WHERE status_inject IN"
              + " (SELECT inject_id FROM injects WHERE tenant_id = ?)",
          tenant.id());
      jdbc.update("DELETE FROM injects WHERE tenant_id = ?", tenant.id());
      jdbc.update("DELETE FROM exercises WHERE tenant_id = ?", tenant.id());
      jdbc.update("DELETE FROM injectors_contracts WHERE tenant_id = ?", tenant.id());
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenant.id());
    }
    tenants.clear();
  }
}
