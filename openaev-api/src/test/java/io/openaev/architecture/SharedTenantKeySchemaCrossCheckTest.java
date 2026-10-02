package io.openaev.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The second half of the shared-key guard (#7905): it re-derives the shared-key set from the live
 * schema and fails when {@code shared-tenant-keys.txt} and the database disagree.
 *
 * <p>The split is deliberate. {@code SharedTenantKeyJoinArchTest} answers a purely structural
 * question about SQL text, so it must stay a plain unit test that runs in any shard with no
 * database and no context; keying it directly on a live derivation would make the cheapest and most
 * valuable check the most expensive one. A checked-in list is only acceptable because this test
 * refuses to let it rot: the schema is the authority, the file is a cache of it, and a schema
 * change that adds, removes or reshapes a per-tenant unique index fails here with the exact line to
 * write.
 *
 * <p>The entity model is not an option for either half. Three classes in {@code openaev-model}
 * declare {@code uniqueConstraints} and none of them is a tenant composite: every one of these
 * indexes is created by a Flyway migration, in several syntaxes, and some are later dropped and
 * replaced. Only the schema knows the answer.
 */
@DisplayName("The shared-key list agrees with the live schema")
class SharedTenantKeySchemaCrossCheckTest extends IntegrationTest {

  @Autowired private DataSource dataSource;

  /**
   * A column is shared across tenants when it is the sole named non-tenant column of a multi-column
   * unique index that includes {@code tenant_id}, and its value space is not globally unique
   * anyway.
   *
   * <p>The three conditions in the WHERE clause are, in order: the index names all of its key
   * columns, so an expression key (which is not a column list) is skipped rather than guessed at;
   * the non-tenant part is a single column, since no single column of a wider key is shared on its
   * own; and the column is not a single-column foreign key onto a single-column primary key, which
   * is what keeps a value minted elsewhere and merely constrained per tenant off the list.
   */
  private static final String DERIVE_SHARED_KEYS =
      """
      WITH tenant_unique AS (
        SELECT t.oid AS table_oid,
               t.relname AS table_name,
               ix.indnatts AS key_columns,
               (SELECT array_agg(a.attname ORDER BY a.attname)
                  FROM pg_attribute a
                 WHERE a.attrelid = t.oid
                   AND a.attnum = ANY(ix.indkey)
                   AND a.attname <> 'tenant_id') AS non_tenant_columns,
               (SELECT count(*) FROM pg_attribute a
                 WHERE a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)) AS named_columns
          FROM pg_index ix
          JOIN pg_class i ON i.oid = ix.indexrelid
          JOIN pg_class t ON t.oid = ix.indrelid
          JOIN pg_namespace n ON n.oid = t.relnamespace
         WHERE ix.indisunique
           AND n.nspname = 'public'
           AND ix.indnatts > 1
           AND EXISTS (SELECT 1 FROM pg_attribute a
                        WHERE a.attrelid = t.oid
                          AND a.attname = 'tenant_id'
                          AND a.attnum = ANY(ix.indkey))
      )
      SELECT DISTINCT u.table_name, u.non_tenant_columns[1] AS shared_column
        FROM tenant_unique u
       WHERE u.named_columns = u.key_columns
         AND cardinality(u.non_tenant_columns) = 1
         AND NOT EXISTS (
               SELECT 1
                 FROM pg_constraint c
                 JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
                WHERE c.contype = 'f'
                  AND c.conrelid = u.table_oid
                  AND array_length(c.conkey, 1) = 1
                  AND a.attname = u.non_tenant_columns[1]
                  AND EXISTS (SELECT 1 FROM pg_index pi
                               WHERE pi.indrelid = c.confrelid
                                 AND pi.indisprimary
                                 AND pi.indnatts = 1))
       ORDER BY 1, 2
      """;

  /**
   * The indexes the derivation deliberately does not read a single column out of: an expression
   * key, or a key made of several non-tenant columns. Pinned by name so that one being added shows
   * up here as a decision to take rather than as a key that quietly never reaches the guard.
   */
  private static final String DERIVE_SKIPPED_INDEXES =
      """
      SELECT i.relname AS index_name
        FROM pg_index ix
        JOIN pg_class i ON i.oid = ix.indexrelid
        JOIN pg_class t ON t.oid = ix.indrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
       WHERE ix.indisunique
         AND n.nspname = 'public'
         AND ix.indnatts > 1
         AND EXISTS (SELECT 1 FROM pg_attribute a
                      WHERE a.attrelid = t.oid
                        AND a.attname = 'tenant_id'
                        AND a.attnum = ANY(ix.indkey))
         AND ((SELECT count(*) FROM pg_attribute a
                WHERE a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)) < ix.indnatts
              OR (SELECT count(*) FROM pg_attribute a
                   WHERE a.attrelid = t.oid
                     AND a.attnum = ANY(ix.indkey)
                     AND a.attname <> 'tenant_id') > 1)
       ORDER BY 1
      """;

  /**
   * Every per-tenant unique index that carries an expression key or a key of several non-tenant
   * columns. A single column of such a key is not shared on its own, so the guard does not judge
   * it; this is the documented gap, and the assertion below is what makes the gap visible instead
   * of silent.
   */
  private static final List<String> KNOWN_SKIPPED_INDEXES =
      List.of(
          // (asset_external_reference) is derived; this one is (tenant_id, LOWER(asset_name),
          // security_platform_type), an expression key.
          "unique_security_platform_name_type_ci_idx",
          // (LOWER(marking_definition_type), LOWER(marking_definition_definition), tenant_id).
          "idx_marking_definitions_type_definition_tenant_uq",
          // Link tables whose key is two ids plus the tenant.
          "injector_contract_tags_pkey",
          "injectors_contracts_attack_patterns_pkey",
          "injectors_contracts_domains_pkey",
          "injectors_contracts_vulnerabilities_pkey",
          "injectors_injector_contracts_pkey",
          "kill_chain_phases_shortname_tenant_unique",
          "kill_chain_phases_tenant_unique");

  /**
   * The columns the derivation drops by the globally-unique rule alone: a single-column foreign key
   * onto a single-column primary key. The per-tenant index over such a column constrains how many
   * rows may share the value, not who may mint it, so the value cannot collide between tenants.
   * This is the subtlest of the three rules, so its outcome is pinned rather than trusted.
   */
  private static final String DERIVE_GLOBALLY_UNIQUE_EXCLUSIONS =
      """
      SELECT t.relname || ' ' || a.attname AS excluded
        FROM pg_index ix
        JOIN pg_class t ON t.oid = ix.indrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        JOIN pg_attribute a ON a.attrelid = t.oid
                           AND a.attnum = ANY(ix.indkey)
                           AND a.attname <> 'tenant_id'
       WHERE ix.indisunique
         AND n.nspname = 'public'
         AND ix.indnatts > 1
         AND EXISTS (SELECT 1 FROM pg_attribute ta
                      WHERE ta.attrelid = t.oid
                        AND ta.attname = 'tenant_id'
                        AND ta.attnum = ANY(ix.indkey))
         AND (SELECT count(*) FROM pg_attribute na
               WHERE na.attrelid = t.oid
                 AND na.attnum = ANY(ix.indkey)
                 AND na.attname <> 'tenant_id') = 1
         AND EXISTS (SELECT 1
                       FROM pg_constraint c
                       JOIN pg_attribute fa ON fa.attrelid = c.conrelid AND fa.attnum = c.conkey[1]
                      WHERE c.contype = 'f'
                        AND c.conrelid = t.oid
                        AND array_length(c.conkey, 1) = 1
                        AND fa.attname = a.attname
                        AND EXISTS (SELECT 1 FROM pg_index pi
                                     WHERE pi.indrelid = c.confrelid
                                       AND pi.indisprimary
                                       AND pi.indnatts = 1))
       ORDER BY 1
      """;

  private static final List<String> KNOWN_GLOBALLY_UNIQUE_EXCLUSIONS =
      List.of(
          // injector_contract_payload holds payload_id values, and payloads.payload_id is a
          // single-column primary key, so the value is globally unique.
          "injectors_contracts injector_contract_payload",
          // users_tenants.user_id holds users.user_id values; the composite expresses membership.
          "users_tenants user_id");

  @Test
  @DisplayName("given a column dropped as globally unique, when listed, then it is a known one")
  void given_aGloballyUniqueColumn_should_beOneOfTheKnownExclusions() throws Exception {
    // ARRANGE / ACT
    List<String> excluded = query(DERIVE_GLOBALLY_UNIQUE_EXCLUSIONS, 1);

    // ASSERT: a new exclusion means a per-tenant unique index was added over a borrowed id. That is
    // safe by this rule, but it must be read once rather than assumed.
    assertEquals(
        new TreeSet<>(KNOWN_GLOBALLY_UNIQUE_EXCLUSIONS),
        new TreeSet<>(excluded),
        "the set of columns the shared-key derivation drops as globally unique has changed");
  }

  @Test
  @DisplayName("given the live schema, when the shared keys are derived, then the list matches it")
  void given_theLiveSchema_should_matchTheCheckedInSharedKeyList() throws Exception {
    // ARRANGE / ACT
    List<String> derived = query(DERIVE_SHARED_KEYS, 2);
    List<String> checkedIn = resourceLines("/shared-tenant-keys.txt");

    // ASSERT
    assertTrue(derived.size() > 15, "the derivation returned almost nothing: " + derived);
    assertEquals(
        new TreeSet<>(derived),
        new TreeSet<>(checkedIn),
        "shared-tenant-keys.txt and the schema disagree. The schema is the authority: write the"
            + " derived lines into the file, and check every query that joins on a key that just"
            + " appeared or disappeared.");
  }

  @Test
  @DisplayName("given a key the derivation cannot read, when listed, then it is a known skip")
  void given_anIndexTheDerivationSkips_should_beOneOfTheKnownOnes() throws Exception {
    // ARRANGE / ACT
    List<String> skipped = query(DERIVE_SKIPPED_INDEXES, 1);

    // ASSERT: a new expression or multi-column per-tenant key is a decision, not a silent gap. If
    // this fails, decide whether the guard should learn the shape, then add the index here.
    assertEquals(
        new TreeSet<>(KNOWN_SKIPPED_INDEXES),
        new TreeSet<>(skipped),
        "the set of per-tenant unique indexes the shared-key derivation does not read a single"
            + " column out of has changed");
  }

  private List<String> query(String sql, int columns) throws Exception {
    List<String> rows = new ArrayList<>();
    try (Connection connection = dataSource.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet results = statement.executeQuery()) {
      while (results.next()) {
        StringBuilder row = new StringBuilder();
        for (int column = 1; column <= columns; column++) {
          row.append(column == 1 ? "" : " ").append(results.getString(column));
        }
        rows.add(row.toString());
      }
    }
    return rows;
  }

  private List<String> resourceLines(String resource) throws Exception {
    try (InputStream stream = getClass().getResourceAsStream(resource)) {
      assertTrue(stream != null, "missing test resource " + resource);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
          .lines()
          .map(String::trim)
          .filter(line -> !line.isEmpty() && !line.startsWith("#"))
          .toList();
    }
  }
}
