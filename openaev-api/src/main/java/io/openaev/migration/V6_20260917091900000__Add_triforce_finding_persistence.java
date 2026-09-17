package io.openaev.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds stable finding groups and point-in-time occurrences without modifying legacy findings.
 *
 * <p>The stable key is tenant + immutable source namespace + output type + value. A legacy
 * multi-location row fans out to one occurrence per location under that stable identity, and every
 * resulting occurrence retains the same {@code migrated_from} reference.
 */
@Component
public class V6_20260917091900000__Add_triforce_finding_persistence extends BaseJavaMigration {

  private static final int BATCH_SIZE = 1000;
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    createSchema(connection);
    long expectedOccurrences = backfill(connection);
    aggregateStableState(connection);
    validateBackfill(connection, expectedOccurrences);
  }

  private void createSchema(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.addBatch(
          """
          CREATE TABLE IF NOT EXISTS stable_findings (
              stable_finding_id VARCHAR(255) NOT NULL CONSTRAINT stable_findings_pkey PRIMARY KEY,
              stable_finding_key VARCHAR(64) NOT NULL,
              stable_finding_source_namespace TEXT NOT NULL,
              stable_finding_contract_output_key VARCHAR(255) NOT NULL,
              stable_finding_source_injector_id VARCHAR(255),
              stable_finding_type VARCHAR(255) NOT NULL,
              stable_finding_value TEXT NOT NULL,
              stable_finding_category VARCHAR(255) NOT NULL
                  CONSTRAINT stable_finding_category_chk
                  CHECK (stable_finding_category IN ('LOCALIZED', 'INFORMATIVE')),
              stable_finding_aggregation_category VARCHAR(255) NOT NULL
                  CONSTRAINT stable_finding_aggregation_category_chk
                  CHECK (stable_finding_aggregation_category IN (
                      'SURFACE_REACHABILITY',
                      'IDENTITIES',
                      'CREDENTIAL_ACCESS',
                      'PRIVILEGE_TRUST_STRUCTURE',
                      'EXPLOITABLE_WEAKNESSES',
                      'RESOURCES',
                      'CONFIGURATION_POSTURE',
                      'INFORMATIVE'
                  )),
              stable_finding_first_seen TIMESTAMP WITH TIME ZONE NOT NULL,
              stable_finding_last_seen TIMESTAMP WITH TIME ZONE NOT NULL,
              stable_finding_lifecycle VARCHAR(255) NOT NULL
                  CONSTRAINT stable_finding_lifecycle_chk
                  CHECK (stable_finding_lifecycle IN ('ACTIVE', 'REVIEW_REQUIRED', 'MUTED')),
              stable_finding_human_updated_at TIMESTAMP WITH TIME ZONE,
              stable_finding_archived_at TIMESTAMP WITH TIME ZONE,
              stable_finding_soft_deleted_at TIMESTAMP WITH TIME ZONE,
              tenant_id VARCHAR(255) NOT NULL
                  CONSTRAINT stable_finding_tenant_id_fk
                  REFERENCES tenants(tenant_id) ON DELETE CASCADE,
              stable_finding_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
              stable_finding_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
              CONSTRAINT uk_stable_findings_key_tenant
                  UNIQUE (tenant_id, stable_finding_key),
              CONSTRAINT stable_finding_source_injector_fk
                  FOREIGN KEY (stable_finding_source_injector_id, tenant_id)
                  REFERENCES injectors(injector_id, tenant_id)
                  ON DELETE SET NULL (stable_finding_source_injector_id)
          )
          """);
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_stable_findings_tenant_id"
              + " ON stable_findings(tenant_id)");
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_stable_findings_source_injector"
              + " ON stable_findings(stable_finding_source_injector_id, tenant_id)");
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_stable_findings_type_value"
              + " ON stable_findings(tenant_id, stable_finding_type, stable_finding_value)");
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_stable_findings_aggregation_category"
              + " ON stable_findings(tenant_id, stable_finding_aggregation_category)");
      statement.addBatch(
          """
          CREATE TABLE IF NOT EXISTS stable_findings_tags (
              stable_finding_id VARCHAR(255) NOT NULL
                  CONSTRAINT stable_findings_tags_stable_finding_id_fk
                  REFERENCES stable_findings(stable_finding_id) ON DELETE CASCADE,
              tag_id VARCHAR(255) NOT NULL
                  CONSTRAINT stable_findings_tags_tag_id_fk
                  REFERENCES tags(tag_id) ON DELETE CASCADE,
              CONSTRAINT stable_findings_tags_pkey PRIMARY KEY (stable_finding_id, tag_id)
          )
          """);
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_stable_findings_tags_stable_finding_id"
              + " ON stable_findings_tags(stable_finding_id)");
      statement.addBatch(
          "CREATE INDEX IF NOT EXISTS idx_stable_findings_tags_tag_id"
              + " ON stable_findings_tags(tag_id)");
      statement.addBatch(
          """
CREATE TABLE IF NOT EXISTS finding_occurrences (
    finding_occurrence_id VARCHAR(255) NOT NULL
        CONSTRAINT finding_occurrences_pkey PRIMARY KEY,
    finding_occurrence_stable_finding_id VARCHAR(255) NOT NULL
        CONSTRAINT finding_occurrence_stable_finding_id_fk
        REFERENCES stable_findings(stable_finding_id) ON DELETE CASCADE,
    finding_occurrence_inject_id VARCHAR(255)
        CONSTRAINT finding_occurrence_inject_id_fk
        REFERENCES injects(inject_id) ON DELETE SET NULL,
    finding_occurrence_scan_id VARCHAR(255),
    finding_occurrence_observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finding_occurrence_outcome VARCHAR(255),
    finding_occurrence_evidence_detail TEXT,
    finding_occurrence_status_detail TEXT,
    finding_occurrence_raw_payload TEXT,
    finding_occurrence_observed_severity VARCHAR(255),
    finding_occurrence_observed_severity_id INTEGER,
    finding_occurrence_source_finding_uid VARCHAR(255),
    finding_occurrence_title TEXT,
    finding_occurrence_description TEXT,
    finding_occurrence_risk TEXT,
    finding_occurrence_categories TEXT[],
    finding_occurrence_attack_patterns TEXT[],
    finding_occurrence_remediation TEXT,
    finding_occurrence_compliance TEXT,
    finding_occurrence_resource TEXT,
    finding_occurrence_resource_snapshot TEXT,
    finding_occurrence_resource_provider VARCHAR(255),
    finding_occurrence_resource_account VARCHAR(255),
    finding_occurrence_resource_region VARCHAR(255),
    finding_occurrence_resource_name VARCHAR(255),
    finding_occurrence_resource_type VARCHAR(255),
    finding_occurrence_resource_service VARCHAR(255),
    finding_occurrence_location TEXT,
    finding_occurrence_location_type VARCHAR(255)
        CONSTRAINT finding_occurrence_location_type_chk
        CHECK (finding_occurrence_location_type IN ('ASSET', 'RESOURCE', 'USER', 'TEAM')),
    finding_occurrence_location_key VARCHAR(1024),
    finding_occurrence_location_asset_id VARCHAR(255)
        CONSTRAINT finding_occurrence_location_asset_id_fk
        REFERENCES assets(asset_id) ON DELETE SET NULL,
    finding_occurrence_location_user_id VARCHAR(255)
        CONSTRAINT finding_occurrence_location_user_id_fk
        REFERENCES users(user_id) ON DELETE SET NULL,
    finding_occurrence_location_team_id VARCHAR(255)
        CONSTRAINT finding_occurrence_location_team_id_fk
        REFERENCES teams(team_id) ON DELETE SET NULL,
    finding_occurrence_target_role VARCHAR(255) NOT NULL
        CONSTRAINT finding_occurrence_target_role_chk
        CHECK (finding_occurrence_target_role IN ('EXECUTOR', 'TARGET', 'UNKNOWN')),
    finding_occurrence_evidence_scope VARCHAR(255) NOT NULL
        CONSTRAINT finding_occurrence_evidence_scope_chk
        CHECK (finding_occurrence_evidence_scope IN ('INDIVIDUAL', 'GROUP')),
    finding_occurrence_migrated_from VARCHAR(255)
        CONSTRAINT finding_occurrence_migrated_from_fk
        REFERENCES findings(finding_id) ON DELETE SET NULL,
    tenant_id VARCHAR(255) NOT NULL
        CONSTRAINT finding_occurrence_tenant_id_fk
        REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    finding_occurrence_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    finding_occurrence_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
)
""");
      addIndex(
          statement,
          "idx_finding_occurrences_stable_finding_id",
          "finding_occurrences",
          "finding_occurrence_stable_finding_id");
      addIndex(
          statement,
          "idx_finding_occurrences_inject_id",
          "finding_occurrences",
          "finding_occurrence_inject_id");
      addIndex(
          statement,
          "idx_finding_occurrences_location_asset_id",
          "finding_occurrences",
          "finding_occurrence_location_asset_id");
      addIndex(
          statement,
          "idx_finding_occurrences_location_user_id",
          "finding_occurrences",
          "finding_occurrence_location_user_id");
      addIndex(
          statement,
          "idx_finding_occurrences_location_team_id",
          "finding_occurrences",
          "finding_occurrence_location_team_id");
      addIndex(
          statement,
          "idx_finding_occurrences_migrated_from",
          "finding_occurrences",
          "finding_occurrence_migrated_from");
      addIndex(statement, "idx_finding_occurrences_tenant_id", "finding_occurrences", "tenant_id");
      statement.addBatch(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS uk_finding_occurrences_migration_location
              ON finding_occurrences(
                  tenant_id,
                  finding_occurrence_migrated_from,
                  (COALESCE(finding_occurrence_location_type, 'INFORMATIVE')),
                  (COALESCE(finding_occurrence_location_key, '')))
              WHERE finding_occurrence_migrated_from IS NOT NULL
          """);
      statement.addBatch(
          """
          CREATE UNIQUE INDEX IF NOT EXISTS uk_finding_occurrences_live_callback
              ON finding_occurrences(
                  tenant_id,
                  finding_occurrence_stable_finding_id,
                  finding_occurrence_inject_id,
                  (COALESCE(finding_occurrence_location_type, 'INFORMATIVE')),
                  (COALESCE(finding_occurrence_location_key, '')))
              WHERE finding_occurrence_migrated_from IS NULL
                AND finding_occurrence_inject_id IS NOT NULL
          """);
      addOccurrenceLinkTable(
          statement, "finding_occurrences_assets", "asset_id", "assets", "asset_id");
      addOccurrenceLinkTable(statement, "finding_occurrences_users", "user_id", "users", "user_id");
      addOccurrenceLinkTable(statement, "finding_occurrences_teams", "team_id", "teams", "team_id");
      statement.executeBatch();
    }
  }

  private void addIndex(Statement statement, String index, String table, String column)
      throws Exception {
    statement.addBatch("CREATE INDEX IF NOT EXISTS %s ON %s(%s)".formatted(index, table, column));
  }

  private void addOccurrenceLinkTable(
      Statement statement,
      String table,
      String linkedColumn,
      String linkedTable,
      String linkedPrimaryKey)
      throws Exception {
    statement.addBatch(
        """
        CREATE TABLE IF NOT EXISTS %s (
            finding_occurrence_id VARCHAR(255) NOT NULL
                CONSTRAINT %s_occurrence_id_fk
                REFERENCES finding_occurrences(finding_occurrence_id) ON DELETE CASCADE,
            %s VARCHAR(255) NOT NULL
                CONSTRAINT %s_%s_fk
                REFERENCES %s(%s) ON DELETE CASCADE,
            CONSTRAINT %s_pkey PRIMARY KEY (finding_occurrence_id, %s)
        )
        """
            .formatted(
                table,
                table,
                linkedColumn,
                table,
                linkedColumn,
                linkedTable,
                linkedPrimaryKey,
                table,
                linkedColumn));
    addIndex(statement, "idx_" + table + "_occurrence_id", table, "finding_occurrence_id");
    addIndex(statement, "idx_" + table + "_" + linkedColumn, table, linkedColumn);
  }

  private long backfill(Connection connection) throws Exception {
    long expectedOccurrences = 0;
    try (PreparedStatement select = connection.prepareStatement(legacyFindingsQuery());
        PreparedStatement insertStable = connection.prepareStatement(stableFindingInsert());
        PreparedStatement selectStable =
            connection.prepareStatement(
                "SELECT stable_finding_id FROM stable_findings"
                    + " WHERE tenant_id = ? AND stable_finding_key = ?");
        PreparedStatement insertOccurrence = connection.prepareStatement(occurrenceInsert());
        PreparedStatement selectOccurrence =
            connection.prepareStatement(
                """
                SELECT finding_occurrence_id
                FROM finding_occurrences
                WHERE tenant_id = ?
                  AND finding_occurrence_migrated_from = ?
                  AND finding_occurrence_location_type IS NOT DISTINCT FROM ?
                  AND finding_occurrence_location_key IS NOT DISTINCT FROM ?
                """);
        PreparedStatement insertTag =
            connection.prepareStatement(
                "INSERT INTO stable_findings_tags(stable_finding_id, tag_id)"
                    + " VALUES (?, ?) ON CONFLICT DO NOTHING");
        PreparedStatement insertAsset =
            linkInsert(connection, "finding_occurrences_assets", "asset_id");
        PreparedStatement insertUser =
            linkInsert(connection, "finding_occurrences_users", "user_id");
        PreparedStatement insertTeam =
            linkInsert(connection, "finding_occurrences_teams", "team_id");
        ResultSet rows = select.executeQuery()) {
      int processed = 0;
      while (rows.next()) {
        LegacyFinding legacy = readLegacyFinding(rows);
        JsonNode raw = parseJson(legacy.rawPayload());
        String namespace = sourceNamespace(legacy, raw);

        List<LocationCandidate> candidates = locationCandidates(legacy, raw);
        expectedOccurrences += candidates.size();
        String key = stableKey(legacy.tenantId(), namespace, legacy.type(), legacy.value());
        String stableId = ensureStableFinding(insertStable, selectStable, legacy, namespace, key);
        // Human-owned tags from every legacy row in the group are consolidated on the stable
        // Finding rather than copied to individual observations.
        addLinks(insertTag, stableId, legacy.tagIds());
        for (LocationCandidate candidate : candidates) {
          String occurrenceId =
              ensureOccurrence(
                  insertOccurrence, selectOccurrence, legacy, raw, stableId, candidate);
          if (candidate.assetId() != null) {
            addLinks(insertAsset, occurrenceId, List.of(candidate.assetId()));
          }
          addLinks(insertUser, occurrenceId, legacy.userIds());
          addLinks(insertTeam, occurrenceId, legacy.teamIds());
        }

        processed++;
        if (processed % BATCH_SIZE == 0) {
          executeBatches(insertTag, insertAsset, insertUser, insertTeam);
        }
      }
      executeBatches(insertTag, insertAsset, insertUser, insertTeam);
    }
    return expectedOccurrences;
  }

  private String legacyFindingsQuery() {
    return """
SELECT f.finding_id, f.tenant_id, f.finding_field, f.finding_type, f.finding_value,
       f.finding_labels, f.finding_name, f.finding_inject_id, f.finding_created_at,
       f.finding_human_updated_at, f.finding_archived_at, f.finding_soft_deleted_at,
       f.finding_severity, f.finding_resource, f.finding_cloud_provider,
       f.finding_cloud_account, f.finding_cloud_region, f.finding_remediation,
       f.finding_compliance, f.finding_raw_data, f.finding_location_asset_id,
       COALESCE(
           i.inject_injector,
           (SELECT MIN(iic.injector_id)
            FROM injectors_injector_contracts iic
            WHERE iic.injector_contract_id = i.inject_injector_contract
              AND iic.tenant_id = f.tenant_id)
       ) AS source_injector_id,
       COALESCE(t.finding_triage_status::text, 'UNTRIAGED') AS triage_status,
       ARRAY(SELECT fa.asset_id FROM findings_assets fa
             WHERE fa.finding_id = f.finding_id ORDER BY fa.asset_id) AS asset_ids,
       ARRAY(SELECT COALESCE(a.asset_hostname, '') FROM findings_assets fa
             JOIN assets a ON a.asset_id = fa.asset_id
             WHERE fa.finding_id = f.finding_id ORDER BY fa.asset_id) AS asset_hostnames,
       ARRAY(SELECT COALESCE(a.asset_name, '') FROM findings_assets fa
             JOIN assets a ON a.asset_id = fa.asset_id
             WHERE fa.finding_id = f.finding_id ORDER BY fa.asset_id) AS asset_names,
       ARRAY(SELECT COALESCE(a.asset_url, '') FROM findings_assets fa
             JOIN assets a ON a.asset_id = fa.asset_id
             WHERE fa.finding_id = f.finding_id ORDER BY fa.asset_id) AS asset_urls,
       ARRAY(SELECT COALESCE(a.asset_external_reference, '') FROM findings_assets fa
             JOIN assets a ON a.asset_id = fa.asset_id
             WHERE fa.finding_id = f.finding_id ORDER BY fa.asset_id) AS asset_external_refs,
       ARRAY(SELECT fu.user_id FROM findings_users fu
             WHERE fu.finding_id = f.finding_id ORDER BY fu.user_id) AS user_ids,
       ARRAY(SELECT ft.team_id FROM findings_teams ft
             WHERE ft.finding_id = f.finding_id ORDER BY ft.team_id) AS team_ids,
       ARRAY(SELECT ft.tag_id FROM findings_tags ft
             WHERE ft.finding_id = f.finding_id ORDER BY ft.tag_id) AS tag_ids,
       ARRAY(SELECT DISTINCT ap.attack_pattern_external_id
             FROM injectors_contracts_attack_patterns ica
             JOIN attack_patterns ap
               ON ap.attack_pattern_id = ica.attack_pattern_id
              AND ap.tenant_id = f.tenant_id
             WHERE ica.injector_contract_id = i.inject_injector_contract
               AND ica.tenant_id = f.tenant_id
             ORDER BY ap.attack_pattern_external_id) AS attack_patterns
FROM findings f
JOIN injects i ON i.inject_id = f.finding_inject_id
LEFT JOIN finding_triages t ON t.finding_triage_finding_id = f.finding_id
ORDER BY f.finding_id
""";
  }

  private String stableFindingInsert() {
    return """
           INSERT INTO stable_findings(
               stable_finding_id, stable_finding_key, stable_finding_source_namespace,
               stable_finding_contract_output_key, stable_finding_source_injector_id,
               stable_finding_type, stable_finding_value,
               stable_finding_category, stable_finding_aggregation_category,
               stable_finding_first_seen, stable_finding_last_seen, stable_finding_lifecycle,
               stable_finding_human_updated_at, stable_finding_archived_at,
               stable_finding_soft_deleted_at, tenant_id,
               stable_finding_created_at, stable_finding_updated_at)
           VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?,
                   'INFORMATIVE', ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, now(), now())
           ON CONFLICT (tenant_id, stable_finding_key) DO NOTHING
           RETURNING stable_finding_id
           """;
  }

  private String occurrenceInsert() {
    return """
           INSERT INTO finding_occurrences(
               finding_occurrence_id, finding_occurrence_stable_finding_id,
               finding_occurrence_inject_id, finding_occurrence_scan_id,
               finding_occurrence_observed_at, finding_occurrence_outcome,
               finding_occurrence_evidence_detail, finding_occurrence_status_detail,
               finding_occurrence_raw_payload, finding_occurrence_observed_severity,
               finding_occurrence_observed_severity_id, finding_occurrence_source_finding_uid,
               finding_occurrence_title, finding_occurrence_description, finding_occurrence_risk,
               finding_occurrence_categories, finding_occurrence_attack_patterns,
               finding_occurrence_remediation, finding_occurrence_compliance,
               finding_occurrence_resource, finding_occurrence_resource_snapshot,
               finding_occurrence_resource_provider, finding_occurrence_resource_account,
               finding_occurrence_resource_region, finding_occurrence_resource_name,
               finding_occurrence_resource_type, finding_occurrence_resource_service,
               finding_occurrence_location, finding_occurrence_location_type,
               finding_occurrence_location_key, finding_occurrence_location_asset_id,
               finding_occurrence_location_user_id, finding_occurrence_location_team_id,
               finding_occurrence_target_role, finding_occurrence_evidence_scope,
               finding_occurrence_migrated_from, tenant_id,
               finding_occurrence_created_at, finding_occurrence_updated_at)
           VALUES (
               gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
               ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT DO NOTHING
           RETURNING finding_occurrence_id
           """;
  }

  private LegacyFinding readLegacyFinding(ResultSet rows) throws Exception {
    return new LegacyFinding(
        rows.getString("finding_id"),
        rows.getString("tenant_id"),
        rows.getString("finding_field"),
        rows.getString("finding_type"),
        rows.getString("finding_value"),
        strings(rows, "finding_labels"),
        rows.getString("finding_name"),
        rows.getString("finding_inject_id"),
        instant(rows, "finding_created_at"),
        instant(rows, "finding_human_updated_at"),
        instant(rows, "finding_archived_at"),
        instant(rows, "finding_soft_deleted_at"),
        rows.getString("finding_severity"),
        rows.getString("finding_resource"),
        rows.getString("finding_cloud_provider"),
        rows.getString("finding_cloud_account"),
        rows.getString("finding_cloud_region"),
        rows.getString("finding_remediation"),
        rows.getString("finding_compliance"),
        rows.getString("finding_raw_data"),
        rows.getString("finding_location_asset_id"),
        rows.getString("source_injector_id"),
        rows.getString("triage_status"),
        strings(rows, "asset_ids"),
        strings(rows, "asset_hostnames"),
        strings(rows, "asset_names"),
        strings(rows, "asset_urls"),
        strings(rows, "asset_external_refs"),
        strings(rows, "user_ids"),
        strings(rows, "team_ids"),
        strings(rows, "tag_ids"),
        strings(rows, "attack_patterns"));
  }

  private String ensureStableFinding(
      PreparedStatement insert,
      PreparedStatement select,
      LegacyFinding legacy,
      String namespace,
      String key)
      throws Exception {
    int index = 1;
    insert.setString(index++, key);
    insert.setString(index++, namespace);
    insert.setString(index++, legacy.field());
    insert.setString(index++, legacy.injectorId());
    insert.setString(index++, legacy.type());
    insert.setString(index++, legacy.value());
    insert.setString(index++, aggregationCategory(legacy.type()));
    insert.setTimestamp(index++, Timestamp.from(legacy.observedAt()));
    insert.setTimestamp(index++, Timestamp.from(legacy.observedAt()));
    setTimestamp(insert, index++, legacy.humanUpdatedAt());
    setTimestamp(insert, index++, legacy.archivedAt());
    setTimestamp(insert, index++, legacy.softDeletedAt());
    insert.setString(index, legacy.tenantId());
    try (ResultSet result = insert.executeQuery()) {
      if (result.next()) {
        return result.getString(1);
      }
    }
    select.setString(1, legacy.tenantId());
    select.setString(2, key);
    try (ResultSet result = select.executeQuery()) {
      if (!result.next()) {
        throw new IllegalStateException("Missing stable finding for legacy finding " + legacy.id());
      }
      return result.getString(1);
    }
  }

  private String aggregationCategory(String type) {
    return switch (type) {
      case "Port", "PortsScan", "IPv4", "IPv6", "Computer" -> "SURFACE_REACHABILITY";
      case "Sid", "Username", "AdminUsername", "Email" -> "IDENTITIES";
      case "Credentials",
              "AccountWithPasswordNotRequired",
              "AsreproastableAccount",
              "KerberoastableAccount" ->
          "CREDENTIAL_ACCESS";
      case "Group", "Delegation" -> "PRIVILEGE_TRUST_STRUCTURE";
      case "CVE", "Vulnerability" -> "EXPLOITABLE_WEAKNESSES";
      case "Share", "File" -> "RESOURCES";
      case "PasswordPolicy", "OCSF" -> "CONFIGURATION_POSTURE";
      case "Text", "Number", "ActionOutput", "ExpectationSignature", "Asset" -> "INFORMATIVE";
      default -> throw new IllegalArgumentException("Unsupported finding type: " + type);
    };
  }

  private String ensureOccurrence(
      PreparedStatement insert,
      PreparedStatement select,
      LegacyFinding legacy,
      JsonNode raw,
      String stableId,
      LocationCandidate candidate)
      throws Exception {
    int index = 1;
    insert.setString(index++, stableId);
    insert.setString(index++, legacy.injectId());
    insert.setString(index++, text(raw, "metadata", "uid"));
    insert.setTimestamp(index++, Timestamp.from(legacy.observedAt()));
    insert.setString(index++, firstText(raw, path("status_code"), path("status")));
    insert.setString(
        index++, firstText(raw, path("finding_info", "analytic", "type"), path("message")));
    insert.setString(index++, firstText(raw, path("status_detail"), path("status")));
    insert.setString(index++, legacy.rawPayload());
    insert.setString(index++, legacy.severity());
    setInteger(insert, index++, integer(raw, "severity_id"));
    insert.setString(index++, text(raw, "finding_info", "uid"));
    insert.setString(index++, firstNonBlank(text(raw, "finding_info", "title"), legacy.name()));
    insert.setString(index++, text(raw, "finding_info", "desc"));
    insert.setString(index++, nodeText(raw, "risk_details"));
    insert.setArray(index++, textArray(insert.getConnection(), categories(legacy, raw)));
    insert.setArray(index++, textArray(insert.getConnection(), attackPatterns(legacy, raw)));
    insert.setString(index++, legacy.remediation());
    insert.setString(index++, legacy.compliance());
    insert.setString(index++, firstNonBlank(candidate.resourceId(), legacy.resource()));
    insert.setString(index++, candidate.resourceSnapshot());
    insert.setString(index++, firstNonBlank(candidate.provider(), legacy.cloudProvider()));
    insert.setString(index++, firstNonBlank(candidate.account(), legacy.cloudAccount()));
    insert.setString(index++, firstNonBlank(candidate.region(), legacy.cloudRegion()));
    insert.setString(index++, candidate.resourceName());
    insert.setString(index++, candidate.resourceType());
    insert.setString(
        index++,
        firstNonBlank(
            candidate.resourceService(),
            firstText(raw, path("cloud", "service", "name"), path("service", "name"))));
    insert.setString(index++, candidate.display());
    insert.setString(index++, candidate.type());
    insert.setString(index++, candidate.key());
    insert.setString(index++, candidate.assetId());
    insert.setString(index++, candidate.userId());
    insert.setString(index++, candidate.teamId());
    insert.setString(index++, targetRole(legacy, candidate));
    insert.setString(index++, evidenceScope(legacy));
    insert.setString(index++, legacy.id());
    insert.setString(index++, legacy.tenantId());
    insert.setTimestamp(index++, Timestamp.from(legacy.observedAt()));
    insert.setTimestamp(index, Timestamp.from(legacy.observedAt()));
    try (ResultSet result = insert.executeQuery()) {
      if (result.next()) {
        return result.getString(1);
      }
    }

    select.setString(1, legacy.tenantId());
    select.setString(2, legacy.id());
    select.setString(3, candidate.type());
    select.setString(4, candidate.key());
    try (ResultSet result = select.executeQuery()) {
      if (!result.next()) {
        throw new IllegalStateException(
            "Missing occurrence for legacy finding %s at %s"
                .formatted(legacy.id(), candidate.key()));
      }
      return result.getString(1);
    }
  }

  private List<LocationCandidate> locationCandidates(LegacyFinding legacy, JsonNode raw)
      throws Exception {
    JsonNode resources = node(raw, "resources");
    if ("OCSF".equals(legacy.type())
        && resources != null
        && resources.isArray()
        && !resources.isEmpty()) {
      List<LocationCandidate> candidates = new ArrayList<>();
      Map<String, Integer> duplicateCounts = new HashMap<>();
      for (JsonNode resource : resources) {
        String resourceId =
            firstNonBlank(
                text(resource, "data", "metadata", "arn"),
                text(resource, "uid"),
                text(resource, "name"));
        String normalized =
            resourceId == null
                ? stableKey("resource", resource.toString(), "", "")
                : normalizeLocation(resourceId);
        int occurrence = duplicateCounts.merge(normalized, 1, Integer::sum);
        String key = occurrence == 1 ? normalized : normalized + "#" + occurrence;
        String assetId = matchingAssetId(legacy, resourceId);
        candidates.add(
            new LocationCandidate(
                "RESOURCE",
                key,
                firstNonBlank(resourceId, resource.toString()),
                assetId,
                null,
                null,
                resourceId,
                resource.toString(),
                text(resource, "name"),
                text(resource, "type"),
                text(resource, "cloud_partition"),
                firstNonBlank(
                    text(resource, "account", "uid"), text(raw, "cloud", "account", "uid")),
                firstNonBlank(text(resource, "region"), text(raw, "cloud", "region")),
                text(resource, "group", "name")));
      }
      return candidates;
    }
    if (!legacy.assetIds().isEmpty()) {
      List<LocationCandidate> candidates = new ArrayList<>();
      for (int index = 0; index < legacy.assetIds().size(); index++) {
        String assetId = legacy.assetIds().get(index);
        candidates.add(
            new LocationCandidate(
                "ASSET",
                normalizeLocation(assetId),
                firstNonBlank(
                    valueAt(legacy.assetHostnames(), index),
                    valueAt(legacy.assetUrls(), index),
                    valueAt(legacy.assetNames(), index),
                    assetId),
                assetId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
      }
      return candidates;
    }
    if (!isBlank(legacy.locationAssetId())) {
      return List.of(
          new LocationCandidate(
              "ASSET",
              normalizeLocation(legacy.locationAssetId()),
              legacy.locationAssetId(),
              legacy.locationAssetId(),
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              null));
    }
    if (!legacy.userIds().isEmpty()) {
      return legacy.userIds().stream()
          .map(
              userId ->
                  new LocationCandidate(
                      "USER",
                      normalizeLocation(userId),
                      userId,
                      null,
                      userId,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null))
          .toList();
    }
    if (!legacy.teamIds().isEmpty()) {
      return legacy.teamIds().stream()
          .map(
              teamId ->
                  new LocationCandidate(
                      "TEAM",
                      normalizeLocation(teamId),
                      teamId,
                      null,
                      null,
                      teamId,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null))
          .toList();
    }
    if (!isBlank(legacy.resource())) {
      return List.of(
          new LocationCandidate(
              "RESOURCE",
              normalizeLocation(legacy.resource()),
              legacy.resource(),
              null,
              null,
              null,
              legacy.resource(),
              null,
              null,
              null,
              legacy.cloudProvider(),
              legacy.cloudAccount(),
              legacy.cloudRegion(),
              null));
    }
    return List.of(LocationCandidate.informative());
  }

  private String matchingAssetId(LegacyFinding legacy, String resourceId) {
    if (resourceId == null) {
      return null;
    }
    for (int index = 0; index < legacy.assetExternalRefs().size(); index++) {
      if (resourceId.equals(legacy.assetExternalRefs().get(index))) {
        return valueAt(legacy.assetIds(), index);
      }
    }
    return null;
  }

  private void aggregateStableState(Connection connection) throws Exception {
    try (Statement statement = connection.createStatement()) {
      statement.execute(
          """
WITH occurrence_stats AS (
    SELECT fo.finding_occurrence_stable_finding_id AS stable_id,
           MIN(fo.finding_occurrence_observed_at) AS first_seen,
           MAX(fo.finding_occurrence_observed_at) AS last_seen,
           BOOL_OR(fo.finding_occurrence_location_type IS NOT NULL) AS localized,
           MAX(f.finding_human_updated_at) AS human_updated_at,
           MAX(f.finding_archived_at) AS archived_at,
           MAX(f.finding_soft_deleted_at) AS soft_deleted_at
    FROM finding_occurrences fo
    LEFT JOIN findings f ON f.finding_id = fo.finding_occurrence_migrated_from
    GROUP BY fo.finding_occurrence_stable_finding_id
),
latest_observation AS (
    SELECT DISTINCT ON (fo.finding_occurrence_stable_finding_id)
           fo.finding_occurrence_stable_finding_id AS stable_id,
           CASE
               WHEN f.finding_archived_at IS NOT NULL
                    OR t.finding_triage_status::text IN ('FALSE_POSITIVE', 'RISK_ACCEPTED')
                   THEN 'MUTED'
               WHEN UPPER(COALESCE(fo.finding_occurrence_outcome, '')) = 'MANUAL'
                   THEN 'REVIEW_REQUIRED'
               ELSE 'ACTIVE'
           END AS lifecycle
    FROM finding_occurrences fo
    LEFT JOIN findings f ON f.finding_id = fo.finding_occurrence_migrated_from
    LEFT JOIN finding_triages t ON t.finding_triage_finding_id = f.finding_id
    ORDER BY fo.finding_occurrence_stable_finding_id,
             fo.finding_occurrence_observed_at DESC,
             fo.finding_occurrence_migrated_from DESC NULLS LAST,
             fo.finding_occurrence_id DESC
)
UPDATE stable_findings sf
SET stable_finding_first_seen = stats.first_seen,
    stable_finding_last_seen = stats.last_seen,
    stable_finding_category =
        CASE WHEN stats.localized THEN 'LOCALIZED' ELSE 'INFORMATIVE' END,
    stable_finding_lifecycle = latest.lifecycle,
    stable_finding_human_updated_at = stats.human_updated_at,
    stable_finding_archived_at = stats.archived_at,
    stable_finding_soft_deleted_at = stats.soft_deleted_at,
    stable_finding_updated_at = now()
FROM occurrence_stats stats
JOIN latest_observation latest ON latest.stable_id = stats.stable_id
WHERE sf.stable_finding_id = stats.stable_id
""");
    }
  }

  private void validateBackfill(Connection connection, long expectedOccurrences) throws Exception {
    try (Statement statement = connection.createStatement();
        ResultSet result =
            statement.executeQuery(
                """
                SELECT
                    (SELECT COUNT(*) FROM findings) AS legacy_count,
                    (SELECT COUNT(DISTINCT finding_occurrence_migrated_from)
                     FROM finding_occurrences
                     WHERE finding_occurrence_migrated_from IS NOT NULL) AS migrated_count,
                    (SELECT COUNT(*) FROM finding_occurrences
                     WHERE finding_occurrence_migrated_from IS NOT NULL) AS occurrence_count
                """)) {
      result.next();
      long legacyCount = result.getLong("legacy_count");
      long migratedCount = result.getLong("migrated_count");
      long occurrenceCount = result.getLong("occurrence_count");
      if (legacyCount != migratedCount || expectedOccurrences != occurrenceCount) {
        throw new IllegalStateException(
            "Triforce backfill lost observations: legacy=%d migrated=%d expectedOccurrences=%d"
                + " actualOccurrences=%d"
                    .formatted(legacyCount, migratedCount, expectedOccurrences, occurrenceCount));
      }
    }
  }

  private PreparedStatement linkInsert(Connection connection, String table, String linkedColumn)
      throws Exception {
    return connection.prepareStatement(
        "INSERT INTO %s(finding_occurrence_id, %s) VALUES (?, ?) ON CONFLICT DO NOTHING"
            .formatted(table, linkedColumn));
  }

  private void addLinks(PreparedStatement statement, String ownerId, List<String> linkedIds)
      throws Exception {
    for (String linkedId : linkedIds) {
      statement.setString(1, ownerId);
      statement.setString(2, linkedId);
      statement.addBatch();
    }
  }

  private void executeBatches(PreparedStatement... statements) throws Exception {
    for (PreparedStatement statement : statements) {
      statement.executeBatch();
    }
  }

  private String sourceNamespace(LegacyFinding legacy, JsonNode raw) {
    String injectorId =
        isBlank(legacy.injectorId())
            ? "legacy-inject-" + legacy.injectId()
            : legacy.injectorId().trim();
    String namespace = injectorId + "/" + legacy.field();
    if ("OCSF".equals(legacy.type())) {
      namespace +=
          "/"
              + normalizeProduct(
                  firstNonBlank(
                      text(raw, "metadata", "product", "uid"),
                      text(raw, "metadata", "product", "name")));
    }
    return namespace;
  }

  private String normalizeProduct(String product) {
    return isBlank(product)
        ? "unknown-product"
        : product.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
  }

  private String normalizeLocation(String location) {
    return location.trim().toLowerCase(Locale.ROOT);
  }

  private String stableKey(String tenantId, String namespace, String type, String value)
      throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    for (String component : List.of(tenantId, namespace, type, value)) {
      byte[] bytes = component.getBytes(StandardCharsets.UTF_8);
      digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.US_ASCII));
      digest.update((byte) ':');
      digest.update(bytes);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private String targetRole(LegacyFinding legacy, LocationCandidate candidate) {
    if ("ASSET".equals(candidate.type())
        && hasHostValue(legacy)
        && hostMatchesAsset(legacy, candidate.assetId())) {
      return "TARGET";
    }
    return "EXECUTOR";
  }

  private String evidenceScope(LegacyFinding legacy) {
    // B2 is the only ambiguous legacy shape: one row contains host evidence and several linked
    // assets. The row is fanned out, but GROUP records that its evidence originally described the
    // whole set. All other branches retain individual evidence.
    return legacy.assetIds().size() > 1 && hasHostValue(legacy) ? "GROUP" : "INDIVIDUAL";
  }

  private boolean hasHostValue(LegacyFinding legacy) {
    String field = legacy.field().toLowerCase(Locale.ROOT);
    return field.contains("host")
        || field.contains("url")
        || legacy.value().matches("(?i)^https?://.*");
  }

  private boolean hostMatchesAsset(LegacyFinding legacy, String assetId) {
    String host = normalizedHost(legacy.value());
    int index = legacy.assetIds().indexOf(assetId);
    return host != null
        && index >= 0
        && (host.equals(normalizedHost(valueAt(legacy.assetHostnames(), index)))
            || host.equals(normalizedHost(valueAt(legacy.assetNames(), index)))
            || host.equals(normalizedHost(valueAt(legacy.assetUrls(), index))));
  }

  private String normalizedHost(String value) {
    if (isBlank(value)) {
      return null;
    }
    return value
        .trim()
        .toLowerCase(Locale.ROOT)
        .replaceFirst("^[a-z][a-z0-9+.-]*://", "")
        .replaceFirst("[/?#].*$", "")
        .replaceFirst(":\\d+$", "");
  }

  private List<String> categories(LegacyFinding legacy, JsonNode raw) {
    Set<String> values = new LinkedHashSet<>();
    legacy.labels().stream().filter(label -> !isBlank(label)).forEach(values::add);
    addText(values, raw, "category_name");
    addText(values, raw, "class_name");
    addText(values, raw, "activity_name");
    return new ArrayList<>(values);
  }

  private List<String> attackPatterns(LegacyFinding legacy, JsonNode raw) {
    Set<String> values = new LinkedHashSet<>();
    legacy.attackPatterns().stream().filter(value -> !isBlank(value)).forEach(values::add);
    JsonNode mitre = node(raw, "unmapped", "compliance", "MITRE-ATTACK");
    if (mitre != null && mitre.isArray()) {
      mitre.forEach(value -> values.add(value.asText()));
    }
    return new ArrayList<>(values);
  }

  private void addText(Set<String> values, JsonNode raw, String field) {
    String value = text(raw, field);
    if (!isBlank(value)) {
      values.add(value);
    }
  }

  private JsonNode parseJson(String raw) {
    if (isBlank(raw)) {
      return null;
    }
    try {
      return OBJECT_MAPPER.readTree(raw);
    } catch (Exception ignored) {
      return null;
    }
  }

  private Integer integer(JsonNode raw, String... path) {
    JsonNode value = node(raw, path);
    if (value == null || value.isNull() || !value.canConvertToInt()) {
      return null;
    }
    return value.intValue();
  }

  private String nodeText(JsonNode raw, String... path) {
    JsonNode value = node(raw, path);
    if (value == null || value.isNull() || value.isMissingNode()) {
      return null;
    }
    return value.isValueNode() ? value.asText() : value.toString();
  }

  private String text(JsonNode raw, String... path) {
    JsonNode value = node(raw, path);
    if (value == null || value.isNull() || value.isMissingNode() || !value.isValueNode()) {
      return null;
    }
    return value.asText().isBlank() ? null : value.asText();
  }

  private JsonNode node(JsonNode raw, String... path) {
    JsonNode current = raw;
    for (String part : path) {
      if (current == null) {
        return null;
      }
      current = current.get(part);
    }
    return current;
  }

  private String firstText(JsonNode raw, String[]... paths) {
    for (String[] path : paths) {
      String value = text(raw, path);
      if (!isBlank(value)) {
        return value;
      }
    }
    return null;
  }

  private String[] path(String... parts) {
    return parts;
  }

  private Array textArray(Connection connection, List<String> values) throws Exception {
    return values.isEmpty() ? null : connection.createArrayOf("text", values.toArray());
  }

  private void setTimestamp(PreparedStatement statement, int index, Instant value)
      throws Exception {
    if (value == null) {
      statement.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
    } else {
      statement.setTimestamp(index, Timestamp.from(value));
    }
  }

  private void setInteger(PreparedStatement statement, int index, Integer value) throws Exception {
    if (value == null) {
      statement.setNull(index, java.sql.Types.INTEGER);
    } else {
      statement.setInt(index, value);
    }
  }

  private Instant instant(ResultSet rows, String column) throws Exception {
    Timestamp value = rows.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private List<String> strings(ResultSet rows, String column) throws Exception {
    Array array = rows.getArray(column);
    if (array == null) {
      return List.of();
    }
    Object[] values = (Object[]) array.getArray();
    List<String> result = new ArrayList<>(values.length);
    for (Object value : values) {
      result.add(value == null ? null : String.valueOf(value));
    }
    return result;
  }

  private String valueAt(List<String> values, int index) {
    return index >= 0 && index < values.size() ? values.get(index) : null;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (!isBlank(value)) {
        return value;
      }
    }
    return null;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record LocationCandidate(
      String type,
      String key,
      String display,
      String assetId,
      String userId,
      String teamId,
      String resourceId,
      String resourceSnapshot,
      String resourceName,
      String resourceType,
      String provider,
      String account,
      String region,
      String resourceService) {

    private static LocationCandidate informative() {
      return new LocationCandidate(
          null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
  }

  private record LegacyFinding(
      String id,
      String tenantId,
      String field,
      String type,
      String value,
      List<String> labels,
      String name,
      String injectId,
      Instant observedAt,
      Instant humanUpdatedAt,
      Instant archivedAt,
      Instant softDeletedAt,
      String severity,
      String resource,
      String cloudProvider,
      String cloudAccount,
      String cloudRegion,
      String remediation,
      String compliance,
      String rawPayload,
      String locationAssetId,
      String injectorId,
      String triageStatus,
      List<String> assetIds,
      List<String> assetHostnames,
      List<String> assetNames,
      List<String> assetUrls,
      List<String> assetExternalRefs,
      List<String> userIds,
      List<String> teamIds,
      List<String> tagIds,
      List<String> attackPatterns) {}
}
