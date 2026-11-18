package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tenant;
import io.openaev.utils.fixtures.InjectFixture;
import java.sql.Connection;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies that a finding outlives the inject that created it: once the migration has run (twice,
 * the rerun being a no-op), deleting the inject sets {@code finding_inject_id} and the occurrence's
 * inject to NULL and keeps the finding and its occurrence. Rows are seeded and read over the
 * session connection, like the migration runs; the test transaction rolls them back.
 */
@Transactional
@DisplayName("Migration V6_20261118120000000: keep the findings of deleted injects")
class KeepFindingsOfDeletedInjectsMigrationTest extends IntegrationTest {

  @Autowired private V6_20261118120000000__Keep_findings_of_deleted_injects migration;

  @Test
  @DisplayName("Given a finding of an inject should keep it, detached, when the inject is deleted")
  void given_aFindingOfAnInject_should_keepItWhenTheInjectIsDeleted() {
    // Arrange
    String injectId = persistInject();
    String findingId = seedFinding(injectId);
    String occurrenceId = seedOccurrence(findingId, injectId);

    // Act
    runMigration();
    runMigration();
    execute("DELETE FROM injects WHERE inject_id = '%s'", injectId);

    // Assert
    assertThat(count("SELECT count(*) FROM findings WHERE finding_id = '%s'", findingId))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM findings WHERE finding_id = '%s'"
                    + " AND finding_inject_id IS NULL",
                findingId))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM finding_occurrences WHERE finding_occurrence_id = '%s'"
                    + " AND finding_occurrence_inject_id IS NULL",
                occurrenceId))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Given the migration has run should leave a single SET NULL foreign key")
  void given_theMigrationHasRun_should_leaveASingleSetNullForeignKey() {
    // Act
    runMigration();
    runMigration();

    // Assert: confdeltype 'n' is ON DELETE SET NULL
    assertThat(
            count(
                "SELECT count(*) FROM pg_constraint WHERE conname = 'finding_inject_id_fk'"
                    + " AND conrelid = 'findings'::regclass AND confdeltype = 'n'"))
        .isEqualTo(1);
    assertThat(
            count(
                "SELECT count(*) FROM pg_constraint WHERE conrelid = 'findings'::regclass"
                    + " AND contype = 'f' AND confrelid = 'injects'::regclass"))
        .isEqualTo(1);
  }

  private String persistInject() {
    Inject inject = InjectFixture.getDefaultInject();
    inject.setTenant(new Tenant(Tenant.DEFAULT_TENANT_UUID));
    entityManager.persist(inject);
    entityManager.flush();
    return inject.getId();
  }

  private String seedFinding(String injectId) {
    String findingId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO findings (finding_id, finding_field, finding_type, finding_value,"
            + " finding_name, finding_inject_id, finding_created_at, finding_updated_at,"
            + " tenant_id) VALUES ('%s', 'port', 'Text', '%s', 'open port', '%s', now(), now(),"
            + " '%s')",
        findingId, "port-" + findingId, injectId, Tenant.DEFAULT_TENANT_UUID);
    return findingId;
  }

  private String seedOccurrence(String findingId, String injectId) {
    String occurrenceId = UUID.randomUUID().toString();
    execute(
        "INSERT INTO finding_occurrences (finding_occurrence_id, finding_occurrence_finding_id,"
            + " finding_occurrence_inject_id, finding_occurrence_date, finding_occurrence_field)"
            + " VALUES ('%s', '%s', '%s', now(), 'port')",
        occurrenceId, findingId, injectId);
    return occurrenceId;
  }

  private long count(String sql, Object... args) {
    long[] count = new long[1];
    withConnection(
        connection -> {
          try (var statement = connection.createStatement();
              var resultSet = statement.executeQuery(sql.formatted(args))) {
            resultSet.next();
            count[0] = resultSet.getLong(1);
          }
        });
    return count[0];
  }

  private void runMigration() {
    withConnection(connection -> migration.migrate(contextOn(connection)));
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

  private void execute(String sql, Object... args) {
    withConnection(
        connection -> {
          try (var statement = connection.createStatement()) {
            statement.execute(sql.formatted(args));
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
