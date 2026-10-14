package io.openaev.migration;

import io.openaev.rest.domain.enums.PresetDomain;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Merges, in every tenant, the domains whose name differs from a preset domain name only by case,
 * spaces or punctuation (same {@link PresetDomain#toMatchingKey matching key}) into that preset.
 *
 * <p>The Akira starter pack scenario was exported from a platform where the tabletop domain was
 * named "Table-Top", and its import looked domains up by exact name: every platform bootstrapped
 * with it got a "Table-Top" domain next to the "Tabletop" preset. The archive and the lookup are
 * fixed; this migration heals the platforms that already imported it.
 *
 * <p>Steps, for each duplicate of a preset present in the same tenant:
 *
 * <ol>
 *   <li>Touch the injector contracts linked to the duplicate, so the engine re-indexes their
 *       expectations with the preset id.
 *   <li>Row-lock the duplicate, so a link a concurrent writer adds to it is either repointed or
 *       rejected by the foreign key, never removed by the cascade of the final delete, then touch
 *       the contracts again with the current time, since the lock may have waited.
 *   <li>Repoint every foreign key referencing {@code domains(domain_id)} to the preset. The
 *       referencing columns are read from {@code pg_constraint} ({@code
 *       injectors_contracts_domains} on the current schema). A row whose twin already references
 *       the preset (same primary key once repointed) is deleted instead.
 *   <li>Delete the duplicate and journal its id in {@code engine_deletions}, so the replay job
 *       removes its document from the search engine: this raw SQL bypasses the entity listeners
 *       that normally do it.
 * </ol>
 *
 * <p>Idempotent (a second run finds no duplicate) and lock-light: row-level locks on the duplicates
 * and their links only, a few dozen rows on a platform bootstrapped with the starter pack, so no
 * batching is needed. A platform without duplicate only reads the {@code domains} table.
 */
@Component
public class V6_20261012120000000__Merge_preset_domain_duplicates extends BaseJavaMigration {

  record DomainRow(String id, String name, String tenantId) {}

  private record ReferencingColumn(String table, String column, List<String> primaryKey) {}

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Map<String, String> presetIdByDuplicateId = findPresetIdByDuplicateId(loadDomains(connection));
    if (presetIdByDuplicateId.isEmpty()) {
      return;
    }
    List<ReferencingColumn> referencingColumns = findReferencingColumns(connection);
    for (Map.Entry<String, String> merge : presetIdByDuplicateId.entrySet()) {
      String duplicateId = merge.getKey();
      String presetId = merge.getValue();
      touchLinkedContracts(connection, duplicateId);
      if (!lockDomain(connection, duplicateId)) {
        continue;
      }
      // Touched again once the lock is held: the lock may have waited, and a timestamp older than
      // the indexing grace window at commit time is never re-read by the incremental indexer.
      touchLinkedContracts(connection, duplicateId);
      for (ReferencingColumn referencingColumn : referencingColumns) {
        repoint(connection, referencingColumn, duplicateId, presetId);
      }
      deleteAndJournal(connection, duplicateId);
    }
  }

  /**
   * Maps every duplicate domain id to the id of the preset of its tenant sharing its matching key.
   * A domain named exactly like a preset is never a duplicate, and a tenant without the preset
   * keeps its variants untouched.
   */
  static Map<String, String> findPresetIdByDuplicateId(List<DomainRow> domains) {
    Map<String, String> presetIdByDuplicateId = new LinkedHashMap<>();
    Map<String, List<DomainRow>> domainsByTenant =
        domains.stream()
            .collect(
                Collectors.groupingBy(
                    DomainRow::tenantId, LinkedHashMap::new, Collectors.toList()));
    for (List<DomainRow> tenantDomains : domainsByTenant.values()) {
      Map<String, String> presetIdByKey = new HashMap<>();
      tenantDomains.stream()
          .filter(domain -> PresetDomain.isPresetName(domain.name()))
          .forEach(
              preset -> presetIdByKey.put(PresetDomain.toMatchingKey(preset.name()), preset.id()));
      tenantDomains.stream()
          .filter(domain -> !PresetDomain.isPresetName(domain.name()))
          .forEach(
              domain -> {
                String presetId = presetIdByKey.get(PresetDomain.toMatchingKey(domain.name()));
                if (presetId != null) {
                  presetIdByDuplicateId.put(domain.id(), presetId);
                }
              });
    }
    return presetIdByDuplicateId;
  }

  private List<DomainRow> loadDomains(Connection connection) throws SQLException {
    List<DomainRow> domains = new ArrayList<>();
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                "SELECT domain_id, domain_name, tenant_id FROM domains"
                    + " ORDER BY tenant_id, domain_name, domain_id")) {
      while (resultSet.next()) {
        domains.add(
            new DomainRow(
                resultSet.getString("domain_id"),
                resultSet.getString("domain_name"),
                resultSet.getString("tenant_id")));
      }
    }
    return domains;
  }

  private List<ReferencingColumn> findReferencingColumns(Connection connection)
      throws SQLException {
    Map<String, List<String>> columnsByTable = new LinkedHashMap<>();
    // pg_constraint rather than information_schema: the information_schema views join constraints
    // by name, which PostgreSQL only makes unique per table.
    try (Statement statement = connection.createStatement();
        ResultSet resultSet =
            statement.executeQuery(
                """
                SELECT con.conrelid::regclass::text AS referencing_table,
                       att.attname AS referencing_column
                FROM pg_constraint con
                CROSS JOIN LATERAL unnest(con.conkey, con.confkey) AS k(attnum, ref_attnum)
                JOIN pg_attribute att
                  ON att.attrelid = con.conrelid AND att.attnum = k.attnum
                JOIN pg_attribute ref_att
                  ON ref_att.attrelid = con.confrelid AND ref_att.attnum = k.ref_attnum
                WHERE con.contype = 'f'
                  AND con.confrelid = 'domains'::regclass
                  AND ref_att.attname = 'domain_id'
                ORDER BY 1, 2
                """)) {
      while (resultSet.next()) {
        columnsByTable
            .computeIfAbsent(resultSet.getString("referencing_table"), table -> new ArrayList<>())
            .add(resultSet.getString("referencing_column"));
      }
    }
    List<ReferencingColumn> referencingColumns = new ArrayList<>();
    for (Map.Entry<String, List<String>> table : columnsByTable.entrySet()) {
      List<String> primaryKey = findPrimaryKey(connection, table.getKey());
      table
          .getValue()
          .forEach(
              column ->
                  referencingColumns.add(
                      new ReferencingColumn(table.getKey(), column, primaryKey)));
    }
    return referencingColumns;
  }

  private List<String> findPrimaryKey(Connection connection, String table) throws SQLException {
    List<String> primaryKey = new ArrayList<>();
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            SELECT att.attname
            FROM pg_index idx
            JOIN pg_attribute att
              ON att.attrelid = idx.indrelid AND att.attnum = ANY (idx.indkey)
            WHERE idx.indrelid = ?::regclass AND idx.indisprimary
            ORDER BY att.attnum
            """)) {
      statement.setString(1, table);
      try (ResultSet resultSet = statement.executeQuery()) {
        while (resultSet.next()) {
          primaryKey.add(resultSet.getString(1));
        }
      }
    }
    return primaryKey;
  }

  private void touchLinkedContracts(Connection connection, String duplicateId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            """
            UPDATE injectors_contracts ic
            SET injector_contract_updated_at = clock_timestamp()
            WHERE EXISTS (
              SELECT 1 FROM injectors_contracts_domains icd
              WHERE icd.injector_contract_id = ic.injector_contract_id
                AND icd.tenant_id = ic.tenant_id
                AND icd.domain_id = ?
            )
            """)) {
      statement.setString(1, duplicateId);
      statement.executeUpdate();
    }
  }

  /**
   * Row-locks the duplicate until the migration commits. A concurrent writer (a node still running
   * the previous version during a rolling deployment) linking a contract to it then either commits
   * first, and its link is repointed, or waits and fails on the foreign key once the duplicate is
   * deleted, instead of losing its link to the {@code ON DELETE CASCADE}. Must be taken after the
   * contracts are touched: a writer updates its contract row before inserting the contract's domain
   * links, so locking the domain first could deadlock with it.
   *
   * @return false when the duplicate no longer exists, so there is nothing left to merge
   */
  private boolean lockDomain(Connection connection, String domainId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT 1 FROM domains WHERE domain_id = ? FOR UPDATE")) {
      statement.setString(1, domainId);
      try (ResultSet resultSet = statement.executeQuery()) {
        return resultSet.next();
      }
    }
  }

  private void repoint(
      Connection connection,
      ReferencingColumn referencingColumn,
      String duplicateId,
      String presetId)
      throws SQLException {
    String table = referencingColumn.table();
    String column = quote(referencingColumn.column());
    if (!referencingColumn.primaryKey().contains(referencingColumn.column())) {
      try (PreparedStatement statement =
          connection.prepareStatement(
              "UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?")) {
        statement.setString(1, presetId);
        statement.setString(2, duplicateId);
        statement.executeUpdate();
      }
      return;
    }
    String sameOtherKeyColumns =
        referencingColumn.primaryKey().stream()
            .filter(keyColumn -> !keyColumn.equals(referencingColumn.column()))
            .map(keyColumn -> " AND twin." + quote(keyColumn) + " = link." + quote(keyColumn))
            .collect(Collectors.joining());
    try (PreparedStatement update =
            connection.prepareStatement(
                "UPDATE "
                    + table
                    + " AS link SET "
                    + column
                    + " = ? WHERE link."
                    + column
                    + " = ? AND NOT EXISTS (SELECT 1 FROM "
                    + table
                    + " AS twin WHERE twin."
                    + column
                    + " = ?"
                    + sameOtherKeyColumns
                    + ")");
        PreparedStatement deleteRemaining =
            connection.prepareStatement("DELETE FROM " + table + " WHERE " + column + " = ?")) {
      update.setString(1, presetId);
      update.setString(2, duplicateId);
      update.setString(3, presetId);
      update.executeUpdate();
      deleteRemaining.setString(1, duplicateId);
      deleteRemaining.executeUpdate();
    }
  }

  private void deleteAndJournal(Connection connection, String duplicateId) throws SQLException {
    try (PreparedStatement delete =
            connection.prepareStatement("DELETE FROM domains WHERE domain_id = ?");
        PreparedStatement journal =
            connection.prepareStatement(
                "INSERT INTO engine_deletions (deletion_id, deletion_date) VALUES (?, now())"
                    + " ON CONFLICT (deletion_id) DO UPDATE SET deletion_date = EXCLUDED.deletion_date")) {
      delete.setString(1, duplicateId);
      delete.executeUpdate();
      journal.setString(1, duplicateId);
      journal.executeUpdate();
    }
  }

  private static String quote(String identifier) {
    return "\"" + identifier.replace("\"", "\"\"") + "\"";
  }
}
