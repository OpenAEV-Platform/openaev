package io.openaev.migration;

import java.util.List;

/**
 * Shared constants for Flyway migrations that need to operate on tenant-scoped tables.
 *
 * <p>When adding a new tenant-scoped entity, add its table name here so that all migrations
 * (tenant_id column, RLS policies, etc.) pick it up automatically.
 */
public final class TenantScopedTables {

  private TenantScopedTables() {}

  /**
   * All tables that have a {@code tenant_id} column (entities extending {@code TenantBase}). Keep
   * this list sorted alphabetically and in sync when adding new tenant-scoped entities.
   */
  public static final List<String> TABLES =
      List.of(
          "agents",
          "asset_agent_jobs",
          "asset_groups",
          "assets",
          "attack_patterns",
          "challenges",
          "channels",
          "collector_types",
          "collectors",
          "connector_instances",
          "custom_dashboards",
          "cwes",
          "datapacks",
          "documents",
          "domains",
          "executors",
          "exercises",
          "findings",
          "import_mappers",
          "injectors",
          "injectors_contracts",
          "injects",
          "kill_chain_phases",
          "lessons_templates",
          "mitigations",
          "notification_rules",
          "organizations",
          "payloads",
          "scenarios",
          "tag_rules",
          "tags",
          "teams",
          "tenant_xtmhub_registrations",
          "vulnerabilities");
}
