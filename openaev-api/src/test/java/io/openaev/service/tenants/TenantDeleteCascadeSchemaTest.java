package io.openaev.service.tenants;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The tenant purge deletes the tenant row and relies on the database to remove everything the
 * tenant owns. Read from the live schema, so a new table or foreign key is checked the day it is
 * added: a foreign key that refuses the delete makes every tenant owning such a row impossible to
 * purge.
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
      // SET NULL is refused too: on a dual-scope table it would turn a tenant row into a platform
      // row.
      // Act
      List<String> refusing =
          jdbc.queryForList(
              "SELECT conrelid::regclass || '.' || conname FROM pg_constraint"
                  + " WHERE contype = 'f' AND confrelid = 'tenants'::regclass"
                  + " AND confdeltype <> 'c' ORDER BY 1",
              String.class);

      // Assert
      assertThat(refusing)
          .as("a foreign key to tenants without ON DELETE CASCADE refuses the tenant delete")
          .isEmpty();
    }

    @Test
    @DisplayName(
        "given the live schema, should have no table outside the tenant cascade referencing a"
            + " table inside it without a delete action")
    void given_theLiveSchema_should_haveNoRefusingForeignKeyFromOutsideTheTenantCascade() {
      // Act - a row of such a table is not removed by the tenant delete, so it blocks it.
      List<String> refusing =
          jdbc.queryForList(
              CASCADED_TABLES
                  + "SELECT c.conrelid::regclass || '.' || c.conname || ' -> '"
                  + " || c.confrelid::regclass FROM pg_constraint c"
                  + " WHERE c.contype = 'f' AND c.confdeltype IN ('a', 'r')"
                  + " AND c.confrelid IN (SELECT table_oid FROM cascaded)"
                  + " AND c.conrelid NOT IN (SELECT table_oid FROM cascaded) ORDER BY 1",
              String.class);

      // Assert
      assertThat(refusing)
          .as("a referencing row outside the tenant cascade makes the tenant delete fail")
          .isEmpty();
    }

    @Test
    @DisplayName(
        "given the live schema, should cascade the tenant delete to collector types and scenario"
            + " reply-to addresses")
    void given_theLiveSchema_should_cascadeToCollectorTypesAndScenarioReplyTo() {
      // Act - the two checks above stay green when a foreign key is dropped instead of cascaded,
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
}
