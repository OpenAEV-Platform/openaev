package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Connection;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code @Transactional} so the re-run of the migration rolls back with the test transaction
 * instead of leaking any DDL side effect into the other tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261118120000000 — news feed item metadata and available types")
class AddNewsFeedItemMetadataAndAvailableTypesMigrationTest extends IntegrationTest {

  private static final String METADATA_TABLE = "news_feed_items_metadata";

  @Autowired
  private V6_20261118120000000__Add_news_feed_item_metadata_and_available_types migration;

  private Object single(String sql, Object... parameters) {
    var query = entityManager.createNativeQuery(sql);
    for (int i = 0; i < parameters.length; i++) {
      query.setParameter(i + 1, parameters[i]);
    }
    return query.getSingleResult();
  }

  private String column(String attribute, String table, String column) {
    return (String)
        single(
            "SELECT "
                + attribute
                + " FROM information_schema.columns WHERE table_name = ?1 AND column_name = ?2",
            table,
            column);
  }

  @Nested
  @DisplayName("schema")
  class Schema {

    @Test
    @DisplayName("the metadata table has one row per item and key, deleted with its item")
    void given_migratedSchema_should_haveTheMetadataTable() {
      // Act + Assert — the migration ran at startup
      assertThat(column("is_nullable", METADATA_TABLE, "news_feed_item_metadata_key"))
          .isEqualTo("NO");
      assertThat(column("is_nullable", METADATA_TABLE, "news_feed_item_metadata_value"))
          .isEqualTo("NO");
      assertThat(column("data_type", METADATA_TABLE, "news_feed_item_metadata_value"))
          .isEqualTo("text");
      assertThat(
              single(
                  "SELECT constraint_type FROM information_schema.table_constraints"
                      + " WHERE constraint_name = 'pk_news_feed_items_metadata'"))
          .isEqualTo("PRIMARY KEY");
      assertThat(
              single(
                  "SELECT rc.delete_rule FROM information_schema.referential_constraints rc"
                      + " JOIN information_schema.table_constraints tc"
                      + " ON tc.constraint_name = rc.constraint_name"
                      + " WHERE tc.table_name = ?1 AND tc.constraint_type = 'FOREIGN KEY'",
                  METADATA_TABLE))
          .isEqualTo("CASCADE");
    }

    @Test
    @DisplayName("the registrations have no available news feed type by default")
    void given_migratedSchema_should_haveTheAvailableTypesColumn() {
      // Act + Assert
      String table = "tenant_xtmhub_registrations";
      String availableTypes = "registration_available_news_feed_types";
      assertThat(column("is_nullable", table, availableTypes)).isEqualTo("NO");
      assertThat(column("data_type", table, availableTypes)).isEqualTo("ARRAY");
      assertThat(column("column_default", table, availableTypes)).isEqualTo("'{}'::text[]");
    }
  }

  @Nested
  @DisplayName("idempotency")
  class Idempotency {

    @Test
    @DisplayName("re-running the migration is a no-op")
    void given_alreadyAppliedMigration_should_rerunWithoutError() {
      // Act + Assert
      assertThatCode(
              () ->
                  entityManager
                      .unwrap(Session.class)
                      .doWork(connection -> runMigration(contextOf(connection))))
          .doesNotThrowAnyException();
    }
  }

  private static Context contextOf(Connection connection) {
    return new Context() {
      @Override
      public Configuration getConfiguration() {
        return null;
      }

      @Override
      public Connection getConnection() {
        return connection;
      }
    };
  }

  private void runMigration(Context context) {
    try {
      migration.migrate(context);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
