package io.openaev.service.tenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * The tenant purge deletes the tenant row and relies on the database to remove everything the
 * tenant owns. The checks below read the foreign keys of the live schema, so a foreign key added
 * later is checked too. The cascade is the set of tables reached from {@code tenants} through
 * {@code ON DELETE CASCADE} foreign keys. They refuse:
 *
 * <ul>
 *   <li>a foreign key to {@code tenants} that does not cascade;
 *   <li>a {@code tenant_id} column with no {@code ON DELETE CASCADE} foreign key to {@code
 *       tenants}, unless it is in {@link #REVIEWED_TENANT_IDS_WITHOUT_THEIR_OWN_CASCADE};
 *   <li>a {@code NO ACTION} or {@code RESTRICT} foreign key from a table outside the cascade to a
 *       table inside it;
 *   <li>a {@code SET NULL} foreign key to a table inside the cascade that nulls a {@code NOT NULL}
 *       column, and a {@code SET DEFAULT} one that sets a {@code NOT NULL} column or a column with
 *       a default, whose parent row may be gone;
 *   <li>a foreign key between two tables inside the cascade that is {@code RESTRICT}, or that is
 *       {@code NO ACTION} with a referencing table whose {@code tenant_id} is nullable or has no
 *       {@code ON DELETE CASCADE} foreign key to {@code tenants}, or with a key that does not pair
 *       {@code tenant_id} with {@code tenant_id}, unless it is in {@link
 *       #REVIEWED_BLOCKING_KEYS_INSIDE_THE_CASCADE}.
 * </ul>
 *
 * <p>They read the schema only: a row referencing another tenant's row through a key outside this
 * list is not visible to them.
 */
@DisplayName("Schema support for the tenant delete")
class TenantDeleteCascadeSchemaTest extends IntegrationTest {

  /** Every table whose rows the tenant delete removes, following ON DELETE CASCADE from tenants. */
  private static final String CASCADED_TABLES =
      """
      WITH RECURSIVE cascaded(table_oid) AS (
        SELECT 'tenants'::regclass::oid
        UNION
        SELECT c.conrelid FROM pg_constraint c JOIN cascaded ON c.confrelid = cascaded.table_oid
        WHERE c.contype = 'f' AND c.confdeltype = 'c'
      )
      """;

  /** Whether the table {@code %s} has an ON DELETE CASCADE foreign key on tenant_id to tenants. */
  private static final String TENANT_ID_CASCADES_TO_TENANTS =
      "EXISTS (SELECT 1 FROM pg_constraint t"
          + " JOIN pg_attribute ta ON ta.attrelid = t.conrelid AND ta.attnum = ANY (t.conkey)"
          + " WHERE t.contype = 'f' AND t.conrelid = %s AND t.confrelid = 'tenants'::regclass"
          + " AND t.confdeltype = 'c' AND ta.attname = 'tenant_id')";

  /**
   * SET NULL is refused too: on a dual-scope table it would turn a tenant row into a platform row.
   */
  private static final String NOT_CASCADING_TO_TENANTS =
      "SELECT conrelid::regclass || '.' || conname FROM pg_constraint"
          + " WHERE contype = 'f' AND confrelid = 'tenants'::regclass"
          + " AND confdeltype <> 'c' ORDER BY 1";

  /**
   * A tenant_id column the tenant delete does not reach directly: its rows stay unless another
   * cascading key removes them.
   */
  private static final String TENANT_ID_WITHOUT_ITS_OWN_CASCADE =
      "SELECT a.attrelid::regclass::text FROM pg_attribute a"
          + " JOIN pg_class cl ON cl.oid = a.attrelid"
          + " WHERE cl.relnamespace = current_schema()::text::regnamespace AND cl.relkind IN ('r', 'p')"
          + " AND a.attname = 'tenant_id' AND NOT a.attisdropped"
          + " AND a.attrelid <> 'tenants'::regclass AND NOT "
          + TENANT_ID_CASCADES_TO_TENANTS.formatted("a.attrelid")
          + " ORDER BY 1";

  private static final String FOREIGN_KEY_LABEL =
      "SELECT c.conrelid::regclass || '.' || c.conname || ' -> ' || c.confrelid::regclass"
          + " FROM pg_constraint c WHERE c.contype = 'f'"
          + " AND c.confrelid IN (SELECT table_oid FROM cascaded)";

  private static final String BLOCKING_FROM_OUTSIDE_THE_CASCADE =
      CASCADED_TABLES
          + FOREIGN_KEY_LABEL
          + " AND c.confdeltype IN ('a', 'r')"
          + " AND c.conrelid NOT IN (SELECT table_oid FROM cascaded) ORDER BY 1";

  /**
   * SET NULL and SET DEFAULT update the referencing row instead of deleting it, wherever it is: a
   * NOT NULL column refuses the null, and a default may point at a parent row the delete removes.
   */
  private static final String FAILING_SET_ACTIONS =
      CASCADED_TABLES
          + FOREIGN_KEY_LABEL
          + " AND c.confdeltype IN ('n', 'd') AND EXISTS ("
          + "SELECT 1 FROM pg_attribute a WHERE a.attrelid = c.conrelid"
          + " AND a.attnum = ANY (COALESCE(c.confdelsetcols, c.conkey))"
          + " AND (a.attnotnull OR (c.confdeltype = 'd' AND a.atthasdef))) ORDER BY 1";

  /**
   * NO ACTION is checked once the whole cascade has run, so it holds when the referencing row is
   * removed by the same tenant delete. That is not known from the schema when the referencing table
   * also holds platform rows, when its tenant_id does not cascade from tenants, or when the key
   * lets a row reference another tenant's row. RESTRICT is checked before the cascade reaches the
   * referencing row, so it is always listed.
   */
  private static final String BLOCKING_INSIDE_THE_CASCADE =
      CASCADED_TABLES
          + FOREIGN_KEY_LABEL
          + " AND c.confrelid <> 'tenants'::regclass"
          + " AND c.confdeltype IN ('a', 'r')"
          + " AND c.conrelid IN (SELECT table_oid FROM cascaded)"
          + " AND (c.confdeltype = 'r'"
          + " OR EXISTS (SELECT 1 FROM pg_attribute a WHERE a.attrelid = c.conrelid"
          + " AND a.attname = 'tenant_id' AND NOT a.attisdropped AND NOT a.attnotnull)"
          + " OR NOT "
          + TENANT_ID_CASCADES_TO_TENANTS.formatted("c.conrelid")
          + " OR NOT EXISTS (SELECT 1 FROM unnest(c.conkey, c.confkey) AS k(child, parent)"
          + " JOIN pg_attribute ca ON ca.attrelid = c.conrelid AND ca.attnum = k.child"
          + " JOIN pg_attribute pa ON pa.attrelid = c.confrelid AND pa.attnum = k.parent"
          + " WHERE ca.attname = 'tenant_id' AND pa.attname = 'tenant_id')) ORDER BY 1";

  /**
   * Blocking foreign keys between two tables of the cascade, each with the reason the referencing
   * row is removed by the same tenant delete as the row it references.
   */
  private static final Map<String, String> REVIEWED_BLOCKING_KEYS_INSIDE_THE_CASCADE =
      Map.of(
          "challenges_documents.challenge_id_fk -> challenges",
          "removed with its document (document_id_fk cascades); a challenge and its documents"
              + " belong to one tenant",
          "scenarios_documents.scenario_id_fk -> scenarios",
          "removed with its document (document_id_fk cascades); a scenario and its documents belong"
              + " to one tenant",
          "collectors.fk_collector_type_ref -> collector_types",
          "removed by its own tenant_id cascade; a collector's type is a collector type of its own"
              + " tenant",
          "payloads.fk_payload_collector_type -> collector_types",
          "removed by its own tenant_id cascade; a payload's collector type is a collector type of"
              + " its own tenant",
          "detection_remediations.fk_remediation_collector_type -> collector_types",
          "removed with its payload (detection_remediation_payload_id cascades); a remediation's"
              + " collector type is one of its payload's tenant",
          "tag_rules.tag_id_fk -> tags",
          "removed by its own tenant_id cascade; a tag rule's tag is a tag of its own tenant",
          "tag_rule_asset_groups.tag_rule_id_fk -> tag_rules",
          "removed with its asset group (asset_group_id_fk cascades); a tag rule and its asset"
              + " groups belong to one tenant");

  private static final String REMOVED_WITH_ITS_INJECTOR_CONTRACT =
      "tenant_id is NOT NULL and part of (injector_contract_id, tenant_id), which cascades from"
          + " injectors_contracts, whose tenant_id cascades from tenants";

  /**
   * Tables whose tenant_id has no cascading key of its own, each with the reason its rows still go
   * with the tenant delete.
   */
  private static final Map<String, String> REVIEWED_TENANT_IDS_WITHOUT_THEIR_OWN_CASCADE =
      Map.of(
          "injectors_contracts_attack_patterns",
          REMOVED_WITH_ITS_INJECTOR_CONTRACT,
          "injectors_contracts_domains",
          REMOVED_WITH_ITS_INJECTOR_CONTRACT,
          "injectors_contracts_vulnerabilities",
          REMOVED_WITH_ITS_INJECTOR_CONTRACT,
          "injectors_injector_contracts",
          REMOVED_WITH_ITS_INJECTOR_CONTRACT,
          "injector_contract_tags",
          REMOVED_WITH_ITS_INJECTOR_CONTRACT);

  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
  }

  @Nested
  @DisplayName("foreign keys")
  class ForeignKeys {

    @Test
    @DisplayName("given the live schema, should cascade every foreign key to tenants")
    void given_theLiveSchema_should_cascadeEveryForeignKeyToTenants() {
      // Act
      List<String> refusing = jdbc.queryForList(NOT_CASCADING_TO_TENANTS, String.class);

      // Assert
      assertThat(refusing)
          .as("a foreign key to tenants without ON DELETE CASCADE refuses the tenant delete")
          .isEmpty();
    }

    @Test
    @DisplayName(
        "given the live schema, should list only reviewed tenant_id columns without a cascading"
            + " foreign key to tenants")
    void given_theLiveSchema_should_listOnlyReviewedTenantIdsWithoutTheirOwnCascade() {
      // Act
      List<String> unbound = jdbc.queryForList(TENANT_ID_WITHOUT_ITS_OWN_CASCADE, String.class);

      // Assert - an entry that no longer matches must leave the list with its table.
      assertThat(unbound)
          .as(
              "a tenant_id column the tenant delete does not cascade to needs a reason why its rows"
                  + " go with the same tenant delete")
          .containsExactlyInAnyOrderElementsOf(
              REVIEWED_TENANT_IDS_WITHOUT_THEIR_OWN_CASCADE.keySet());
    }

    @Test
    @DisplayName(
        "given the live schema, should have no table outside the tenant cascade referencing a"
            + " table inside it without a delete action")
    void given_theLiveSchema_should_haveNoRefusingForeignKeyFromOutsideTheTenantCascade() {
      // Act - a row of such a table is not removed by the tenant delete, so it blocks it.
      List<String> refusing = jdbc.queryForList(BLOCKING_FROM_OUTSIDE_THE_CASCADE, String.class);

      // Assert
      assertThat(refusing)
          .as("a referencing row outside the tenant cascade makes the tenant delete fail")
          .isEmpty();
    }

    @Test
    @DisplayName(
        "given the live schema, should have no SET NULL or SET DEFAULT foreign key into the tenant"
            + " cascade that the database would refuse")
    void given_theLiveSchema_should_haveNoFailingSetActionIntoTheTenantCascade() {
      // Act
      List<String> failing = jdbc.queryForList(FAILING_SET_ACTIONS, String.class);

      // Assert
      assertThat(failing)
          .as("a refused SET NULL or SET DEFAULT aborts the tenant delete")
          .isEmpty();
    }

    @Test
    @DisplayName(
        "given the live schema, should list only reviewed blocking foreign keys inside the tenant"
            + " cascade")
    void given_theLiveSchema_should_listOnlyReviewedBlockingForeignKeysInsideTheTenantCascade() {
      // Act
      List<String> blocking = jdbc.queryForList(BLOCKING_INSIDE_THE_CASCADE, String.class);

      // Assert - an entry that no longer matches must leave the list with its foreign key.
      assertThat(blocking)
          .as(
              "a blocking foreign key inside the tenant cascade needs a reason why the referencing"
                  + " row goes with the same tenant delete")
          .containsExactlyInAnyOrderElementsOf(REVIEWED_BLOCKING_KEYS_INSIDE_THE_CASCADE.keySet());
    }

    @Test
    @DisplayName(
        "given the live schema, should cascade the tenant delete to collector types and scenario"
            + " reply-to addresses")
    void given_theLiveSchema_should_cascadeToCollectorTypesAndScenarioReplyTo() {
      // Act - the checks above stay green when a foreign key is dropped instead of cascaded,
      // which leaves rows behind after the tenant delete.
      List<String> cascading =
          jdbc.queryForList(
              "SELECT conrelid::regclass || ' -> ' || confrelid::regclass FROM pg_constraint"
                  + " WHERE contype = 'f' AND confdeltype = 'c'"
                  + " AND ((conrelid = 'collector_types'::regclass"
                  + " AND confrelid = 'tenants'::regclass)"
                  + " OR (conrelid = 'scenario_mails_reply_to'::regclass"
                  + " AND confrelid = 'scenarios'::regclass)) ORDER BY 1",
              String.class);

      // Assert
      assertThat(cascading)
          .containsExactly("collector_types -> tenants", "scenario_mails_reply_to -> scenarios");
    }
  }

  /**
   * The checks above, run against tables created inside a transaction that is rolled back, so each
   * shape they refuse is shown to be seen, and nothing is left in the schema.
   */
  @Nested
  @DisplayName("checks against a foreign key of each refused shape")
  class ChecksAgainstEachRefusedShape {

    private static final String CASCADED_PARENT =
        "CREATE TABLE schema_check_parent (parent_id varchar(255) PRIMARY KEY,"
            + " tenant_id varchar(255) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,"
            + " UNIQUE (parent_id, tenant_id))";

    @Test
    @DisplayName(
        "given NO ACTION and SET NULL foreign keys to tenants, should list both and not a"
            + " cascading one")
    void given_foreignKeysToTenantsOfEachDeleteAction_should_listOnlyTheNonCascadingOnes()
        throws Exception {
      List<String> refusing =
          inRolledBackTransaction(
              tx -> {
                // Arrange
                tx.execute(
                    "CREATE TABLE schema_check_tenant_no_action (tenant_id varchar(255)"
                        + " CONSTRAINT fk_tenant_no_action REFERENCES tenants(tenant_id))");
                tx.execute(
                    "CREATE TABLE schema_check_tenant_set_null (tenant_id varchar(255)"
                        + " CONSTRAINT fk_tenant_set_null REFERENCES tenants(tenant_id)"
                        + " ON DELETE SET NULL)");
                tx.execute(
                    "CREATE TABLE schema_check_tenant_cascade (tenant_id varchar(255)"
                        + " CONSTRAINT fk_tenant_cascade REFERENCES tenants(tenant_id)"
                        + " ON DELETE CASCADE)");

                // Act
                return tx.queryForList(NOT_CASCADING_TO_TENANTS, String.class);
              });

      // Assert
      assertThat(refusing)
          .contains(
              "schema_check_tenant_no_action.fk_tenant_no_action",
              "schema_check_tenant_set_null.fk_tenant_set_null")
          .doesNotContain("schema_check_tenant_cascade.fk_tenant_cascade");
    }

    @Test
    @DisplayName(
        "given tenant_id columns with no key and a NO ACTION key to tenants, should list both and"
            + " not one with a cascading key")
    void given_tenantIdColumnsOfEachKeyShape_should_listOnlyTheOnesWithoutACascade()
        throws Exception {
      List<String> unbound =
          inRolledBackTransaction(
              tx -> {
                // Arrange
                tx.execute("CREATE TABLE schema_check_tenant_id_no_key (tenant_id varchar(255))");
                tx.execute(
                    "CREATE TABLE schema_check_tenant_id_no_action (tenant_id varchar(255)"
                        + " REFERENCES tenants(tenant_id))");
                tx.execute(
                    "CREATE TABLE schema_check_tenant_id_cascade (tenant_id varchar(255)"
                        + " REFERENCES tenants(tenant_id) ON DELETE CASCADE)");

                // Act
                return tx.queryForList(TENANT_ID_WITHOUT_ITS_OWN_CASCADE, String.class);
              });

      // Assert
      assertThat(unbound)
          .contains("schema_check_tenant_id_no_key", "schema_check_tenant_id_no_action")
          .doesNotContain("schema_check_tenant_id_cascade");
    }

    @Test
    @DisplayName(
        "given SET NULL or SET DEFAULT on a NOT NULL column and SET DEFAULT on a column with a"
            + " default, should list them and not SET NULL or SET DEFAULT on a nullable column"
            + " without a default")
    void given_setActionsOfEachShape_should_listOnlyTheRefusedOnes() throws Exception {
      List<String> failing =
          inRolledBackTransaction(
              tx -> {
                // Arrange
                tx.execute(CASCADED_PARENT);
                tx.execute(
                    "CREATE TABLE schema_check_set_null_not_null (parent_id varchar(255) NOT NULL"
                        + " CONSTRAINT fk_set_null_not_null REFERENCES schema_check_parent"
                        + " ON DELETE SET NULL)");
                tx.execute(
                    "CREATE TABLE schema_check_set_default (parent_id varchar(255) DEFAULT 'x'"
                        + " CONSTRAINT fk_set_default REFERENCES schema_check_parent"
                        + " ON DELETE SET DEFAULT)");
                tx.execute(
                    "CREATE TABLE schema_check_set_default_not_null (parent_id varchar(255) NOT"
                        + " NULL CONSTRAINT fk_set_default_not_null REFERENCES schema_check_parent"
                        + " ON DELETE SET DEFAULT)");
                tx.execute(
                    "CREATE TABLE schema_check_set_null_nullable (parent_id varchar(255)"
                        + " CONSTRAINT fk_set_null_nullable REFERENCES schema_check_parent"
                        + " ON DELETE SET NULL)");
                tx.execute(
                    "CREATE TABLE schema_check_set_default_nullable (parent_id varchar(255)"
                        + " CONSTRAINT fk_set_default_nullable REFERENCES schema_check_parent"
                        + " ON DELETE SET DEFAULT)");

                // Act
                return tx.queryForList(FAILING_SET_ACTIONS, String.class);
              });

      // Assert
      assertThat(failing)
          .contains(
              "schema_check_set_null_not_null.fk_set_null_not_null -> schema_check_parent",
              "schema_check_set_default.fk_set_default -> schema_check_parent",
              "schema_check_set_default_not_null.fk_set_default_not_null -> schema_check_parent")
          .doesNotContain(
              "schema_check_set_null_nullable.fk_set_null_nullable -> schema_check_parent",
              "schema_check_set_default_nullable.fk_set_default_nullable -> schema_check_parent");
    }

    @Test
    @DisplayName(
        "given blocking foreign keys inside the cascade, should list a dual-scope referencing"
            + " table, a tenant_id that does not cascade from tenants, a key without tenant_id and"
            + " RESTRICT, and not a NO ACTION key on a cascading tenant_id")
    void given_blockingKeysInsideTheCascade_should_listEachUnprovenShape() throws Exception {
      List<String> blocking =
          inRolledBackTransaction(
              tx -> {
                // Arrange
                tx.execute(CASCADED_PARENT);
                tx.execute(
                    "CREATE TABLE schema_check_dual_scope (parent_id varchar(255),"
                        + " tenant_id varchar(255) REFERENCES tenants(tenant_id) ON DELETE CASCADE,"
                        + " CONSTRAINT fk_dual_scope FOREIGN KEY (parent_id, tenant_id)"
                        + " REFERENCES schema_check_parent (parent_id, tenant_id))");
                tx.execute(
                    "CREATE TABLE schema_check_tenant_unbound (holder_id varchar(255)"
                        + " REFERENCES schema_check_parent ON DELETE CASCADE,"
                        + " parent_id varchar(255), tenant_id varchar(255) NOT NULL,"
                        + " CONSTRAINT fk_tenant_unbound FOREIGN KEY (parent_id, tenant_id)"
                        + " REFERENCES schema_check_parent (parent_id, tenant_id))");
                tx.execute(
                    "CREATE TABLE schema_check_no_tenant_key (parent_id varchar(255)"
                        + " CONSTRAINT fk_no_tenant_key REFERENCES schema_check_parent,"
                        + " tenant_id varchar(255) NOT NULL REFERENCES tenants(tenant_id)"
                        + " ON DELETE CASCADE)");
                tx.execute(
                    "CREATE TABLE schema_check_restrict (parent_id varchar(255),"
                        + " tenant_id varchar(255) NOT NULL REFERENCES tenants(tenant_id)"
                        + " ON DELETE CASCADE, CONSTRAINT fk_restrict FOREIGN KEY (parent_id,"
                        + " tenant_id) REFERENCES schema_check_parent (parent_id, tenant_id)"
                        + " ON DELETE RESTRICT)");
                tx.execute(
                    "CREATE TABLE schema_check_tenant_key (parent_id varchar(255),"
                        + " tenant_id varchar(255) NOT NULL REFERENCES tenants(tenant_id)"
                        + " ON DELETE CASCADE, CONSTRAINT fk_tenant_key FOREIGN KEY (parent_id,"
                        + " tenant_id) REFERENCES schema_check_parent (parent_id, tenant_id))");

                // Act
                return tx.queryForList(BLOCKING_INSIDE_THE_CASCADE, String.class);
              });

      // Assert
      assertThat(blocking)
          .contains(
              "schema_check_dual_scope.fk_dual_scope -> schema_check_parent",
              "schema_check_tenant_unbound.fk_tenant_unbound -> schema_check_parent",
              "schema_check_no_tenant_key.fk_no_tenant_key -> schema_check_parent",
              "schema_check_restrict.fk_restrict -> schema_check_parent")
          .doesNotContain("schema_check_tenant_key.fk_tenant_key -> schema_check_parent");
    }

    @Test
    @DisplayName(
        "given NO ACTION and RESTRICT foreign keys from outside the cascade and a NO ACTION one"
            + " from inside, should list only the ones from outside")
    void given_blockingKeysFromOutsideAndInsideTheCascade_should_listOnlyTheOnesFromOutside()
        throws Exception {
      List<String> blocking =
          inRolledBackTransaction(
              tx -> {
                // Arrange
                tx.execute(CASCADED_PARENT);
                tx.execute(
                    "CREATE TABLE schema_check_outside (parent_id varchar(255)"
                        + " CONSTRAINT fk_outside REFERENCES schema_check_parent)");
                tx.execute(
                    "CREATE TABLE schema_check_outside_restrict (parent_id varchar(255)"
                        + " CONSTRAINT fk_outside_restrict REFERENCES schema_check_parent"
                        + " ON DELETE RESTRICT)");
                tx.execute(
                    "CREATE TABLE schema_check_inside (parent_id varchar(255)"
                        + " CONSTRAINT fk_inside REFERENCES schema_check_parent,"
                        + " tenant_id varchar(255) NOT NULL REFERENCES tenants(tenant_id)"
                        + " ON DELETE CASCADE)");

                // Act
                return tx.queryForList(BLOCKING_FROM_OUTSIDE_THE_CASCADE, String.class);
              });

      // Assert
      assertThat(blocking)
          .contains(
              "schema_check_outside.fk_outside -> schema_check_parent",
              "schema_check_outside_restrict.fk_outside_restrict -> schema_check_parent")
          .doesNotContain("schema_check_inside.fk_inside -> schema_check_parent");
    }

    private <T> T inRolledBackTransaction(TransactionWork<T> work) throws Exception {
      try (Connection connection = dataSource.getConnection()) {
        connection.setAutoCommit(false);
        try {
          return work.run(new JdbcTemplate(new SingleConnectionDataSource(connection, true)));
        } finally {
          connection.rollback();
          connection.setAutoCommit(true);
        }
      }
    }
  }

  @FunctionalInterface
  private interface TransactionWork<T> {
    T run(JdbcTemplate transaction) throws Exception;
  }
}
