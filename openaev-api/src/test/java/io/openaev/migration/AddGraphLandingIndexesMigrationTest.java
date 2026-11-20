package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Connection;
import java.util.List;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261119013000000: graph landing indexes")
class AddGraphLandingIndexesMigrationTest extends IntegrationTest {

  @Autowired private V6_20261119013000000__Add_graph_landing_indexes migration;

  // Puts the schema back as it was before the migration, inside the test transaction.
  @BeforeEach
  void dropIndexes() {
    execute(
        "DROP INDEX IF EXISTS "
            + V6_20261119013000000__Add_graph_landing_indexes.FAILED_ASSET_INDEX);
    execute(
        "DROP INDEX IF EXISTS "
            + V6_20261119013000000__Add_graph_landing_indexes.GROUP_MEMBER_INDEX);
  }

  @Test
  @DisplayName("Creates both indexes once, and a rerun is a no-op")
  void given_noIndex_should_createBothOnceAndRerunCleanly() {
    // Act
    runMigration();
    runMigration();

    // Assert
    assertThat(
            rows(
                "SELECT indexname FROM pg_indexes WHERE indexname IN ('"
                    + V6_20261119013000000__Add_graph_landing_indexes.FAILED_ASSET_INDEX
                    + "', '"
                    + V6_20261119013000000__Add_graph_landing_indexes.GROUP_MEMBER_INDEX
                    + "') ORDER BY indexname"))
        .containsExactly(
            V6_20261119013000000__Add_graph_landing_indexes.GROUP_MEMBER_INDEX,
            V6_20261119013000000__Add_graph_landing_indexes.FAILED_ASSET_INDEX);
    assertThat(
            rows(
                "SELECT indexdef FROM pg_indexes WHERE indexname = '"
                    + V6_20261119013000000__Add_graph_landing_indexes.FAILED_ASSET_INDEX
                    + "'"))
        .singleElement()
        .asString()
        .contains("WHERE")
        .contains("inject_expectation_score");
  }

  private void runMigration() {
    withConnection(connection -> migration.migrate(contextOn(connection)));
  }

  @SuppressWarnings("unchecked")
  private List<String> rows(String sql) {
    return entityManager.createNativeQuery(sql).getResultList();
  }

  @FunctionalInterface
  private interface ConnectionWork {
    void run(Connection connection) throws Exception;
  }

  private void withConnection(ConnectionWork work) {
    entityManager
        .unwrap(Session.class)
        .doWork(
            connection -> {
              try {
                work.run(connection);
              } catch (RuntimeException e) {
                throw e;
              } catch (Exception e) {
                throw new IllegalStateException(e);
              }
            });
  }

  private void execute(String sql) {
    withConnection(
        connection -> {
          try (var statement = connection.createStatement()) {
            statement.execute(sql);
          }
        });
  }

  private static Context contextOn(Connection connection) {
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
}
