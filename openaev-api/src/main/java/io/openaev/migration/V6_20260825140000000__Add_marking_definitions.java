package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Marking definitions — the vocabulary a clearance is expressed in (Task 2, step 2.1 of the marking
 * design).
 *
 * <p>Two tables, deliberately different shapes:
 *
 * <ul>
 *   <li>{@code marking_definitions} — tenant-scoped catalogue of markings. Every tenant gets its
 *       own TLP and PAP scales, so the uniqueness constraint is composite on {@code (name,
 *       tenant_id)}.
 *   <li>{@code groups_markings} — the clearance <b>grant</b>: which markings the members of a group
 *       are allowed to see. It answers "what can this group see?", not "who may see this group",
 *       which is why it stays a join table and never becomes a marked table itself.
 * </ul>
 *
 * <p>{@code marking_definitions} carries no {@code marking_ids} column: marking the marking
 * catalogue would make clearance resolution depend on a clearance, which fails closed to "nobody
 * sees anything". Tenant isolation is v2 (statement inspector), so there is no Hibernate
 * {@code @Filter} on the entity.
 */
@Component
public class V6_20260825140000000__Add_marking_definitions extends BaseJavaMigration {

  /** Ordinals leave room to insert levels later without renumbering. */
  private static final String[][] DEFAULT_MARKINGS = {
    // type, name, order, color
    {"TLP", "TLP:CLEAR", "10", "#ffffff"},
    {"TLP", "TLP:GREEN", "20", "#2e7d32"},
    {"TLP", "TLP:AMBER", "30", "#d84315"},
    {"TLP", "TLP:AMBER+STRICT", "40", "#d84315"},
    {"TLP", "TLP:RED", "50", "#c62828"},
    {"PAP", "PAP:CLEAR", "10", "#ffffff"},
    {"PAP", "PAP:GREEN", "20", "#2e7d32"},
    {"PAP", "PAP:AMBER", "30", "#d84315"},
    {"PAP", "PAP:RED", "50", "#c62828"},
  };

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS marking_definitions (
            marking_id          VARCHAR(255) NOT NULL CONSTRAINT marking_definitions_pkey PRIMARY KEY,
            marking_type        VARCHAR(255) NOT NULL,
            marking_name        VARCHAR(255) NOT NULL,
            marking_order       INT          NOT NULL,
            marking_color       VARCHAR(255),
            marking_created_at  TIMESTAMP    NOT NULL DEFAULT now(),
            marking_updated_at  TIMESTAMP    NOT NULL DEFAULT now(),
            tenant_id           VARCHAR(255) NOT NULL
              CONSTRAINT fk_marking_definitions_tenant_id
              REFERENCES tenants (tenant_id) ON DELETE CASCADE
          );
          """);

      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_marking_definitions_tenant_id ON marking_definitions (tenant_id);");
      // Composite on tenant_id: two tenants may each define "TLP:RED" independently.
      statement.execute(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS idx_marking_definitions_name_tenant_unique
            ON marking_definitions (marking_name, tenant_id);
          """);
      // Serves the resolver's "highest order held per type" read.
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_marking_definitions_tenant_type_order
            ON marking_definitions (tenant_id, marking_type, marking_order);
          """);

      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS groups_markings (
            group_id   VARCHAR(255) NOT NULL
              CONSTRAINT fk_groups_markings_group_id
              REFERENCES groups (group_id) ON DELETE CASCADE,
            marking_id VARCHAR(255) NOT NULL
              CONSTRAINT fk_groups_markings_marking_id
              REFERENCES marking_definitions (marking_id) ON DELETE CASCADE,
            CONSTRAINT groups_markings_pkey PRIMARY KEY (group_id, marking_id)
          );
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_groups_markings_marking_id ON groups_markings (marking_id);");

      seedDefaults(statement);
    }
  }

  /**
   * Seeds the TLP and PAP scales for every existing tenant. New tenants are seeded by {@code
   * MarkingDefinitionDependenciesManager} at creation time; this covers the ones that already
   * exist.
   *
   * <p>Idempotent through {@code ON CONFLICT DO NOTHING} against the composite unique index, so a
   * tenant that somehow already defined "TLP:RED" keeps its own row.
   */
  private void seedDefaults(Statement statement) throws Exception {
    for (String[] marking : DEFAULT_MARKINGS) {
      statement.execute(
          """
          INSERT INTO marking_definitions
            (marking_id, marking_type, marking_name, marking_order, marking_color, tenant_id)
          SELECT gen_random_uuid()::text, '%s', '%s', %s, '%s', t.tenant_id
          FROM tenants t
          ON CONFLICT (marking_name, tenant_id) DO NOTHING;
          """
              .formatted(marking[0], marking[1], marking[2], marking[3]));
    }
  }
}
