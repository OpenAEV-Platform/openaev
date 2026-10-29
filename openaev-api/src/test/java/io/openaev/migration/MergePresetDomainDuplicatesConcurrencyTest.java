package io.openaev.migration;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.openaev.IntegrationTest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The preset domain duplicate merge racing a writer that links a contract to the duplicate (a node
 * still running the previous version during a rolling deployment), against the real database with
 * one connection each: the writer's link is repointed to the preset or rejected by the foreign key,
 * never removed by the cascade of the duplicate's delete. Rows are committed, since both
 * connections must see them, and removed after each test.
 */
@DisplayName("Merge preset domain duplicates migration - concurrent writer")
class MergePresetDomainDuplicatesConcurrencyTest extends IntegrationTest {

  private static final Instant LONG_AGO = Instant.parse("2020-01-01T00:00:00Z");

  @Autowired private V6_20261012120000000__Merge_preset_domain_duplicates migration;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String presetId;
  private String duplicateId;
  private String contractId;

  @BeforeEach
  void seedTenantWithADuplicate() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "preset-domain-merge-race-" + tenantId);
    presetId = insertDomain("Tabletop");
    duplicateId = insertDomain("Table-Top");
    contractId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO injectors_contracts (injector_contract_id, tenant_id,"
            + " injector_contract_content, injector_contract_import_available,"
            + " injector_contract_updated_at) VALUES (?, ?, '{}', false, ?)",
        contractId,
        tenantId,
        Timestamp.from(LONG_AGO));
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM injectors_contracts_domains WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM injectors_contracts WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM domains WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    jdbc.update("DELETE FROM engine_deletions WHERE deletion_id = ?", duplicateId);
  }

  @Test
  @DisplayName("given_linkAddedBeforeTheMergeLocksTheDuplicate_should_repointItOnceCommitted")
  void given_linkAddedBeforeTheMergeLocksTheDuplicate_should_repointItOnceCommitted()
      throws Exception {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try (Connection writer = dataSource.getConnection();
        Connection merger = dataSource.getConnection()) {
      writer.setAutoCommit(false);
      merger.setAutoCommit(false);
      try {
        // Arrange: the writer's link exists, uncommitted, when the merge starts.
        link(writer, duplicateId);
        int mergerPid = backendPid(merger);

        // Act
        Future<?> merge = pool.submit(() -> migrate(merger));
        awaitWaiting(mergerPid);
        writer.commit();
        merge.get(30, SECONDS);
        merger.commit();
      } finally {
        writer.rollback();
        merger.rollback();
      }
    } finally {
      pool.shutdownNow();
    }

    // Assert
    assertThat(linkedDomainIds()).containsExactly(presetId);
    assertThat(domainExists(duplicateId)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT injector_contract_updated_at FROM injectors_contracts"
                    + " WHERE injector_contract_id = ?",
                Timestamp.class,
                contractId))
        .as("the contract linked once the lock was held is touched for re-indexing")
        .isAfter(Timestamp.from(LONG_AGO));
  }

  @Test
  @DisplayName("given_linkAddedWhileTheMergeHoldsTheDuplicate_should_rejectItOnTheForeignKey")
  void given_linkAddedWhileTheMergeHoldsTheDuplicate_should_rejectItOnTheForeignKey()
      throws Exception {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<?> write;
    try (Connection writer = dataSource.getConnection();
        Connection merger = dataSource.getConnection()) {
      writer.setAutoCommit(false);
      merger.setAutoCommit(false);
      try {
        // Arrange: the merge ran and holds the duplicate, uncommitted.
        migrate(merger);
        int writerPid = backendPid(writer);

        // Act
        write = pool.submit(() -> link(writer, duplicateId));
        awaitWaiting(writerPid);
        merger.commit();

        // Assert
        try {
          write.get(30, SECONDS);
          fail("the writer's link to the deleted duplicate must be rejected");
        } catch (ExecutionException rejected) {
          assertThat(rejected.getCause())
              .isInstanceOf(SQLException.class)
              .extracting(cause -> ((SQLException) cause).getSQLState())
              .isEqualTo("23503");
        }
      } finally {
        writer.rollback();
        merger.rollback();
      }
    } finally {
      pool.shutdownNow();
    }
    assertThat(linkedDomainIds()).isEmpty();
    assertThat(domainExists(duplicateId)).isFalse();
    assertThat(domainExists(presetId)).isTrue();
  }

  private String insertDomain(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO domains (domain_id, domain_name, domain_color, tenant_id) VALUES (?, ?, ?, ?)",
        id,
        name,
        "#FFCC33",
        tenantId);
    return id;
  }

  private Void link(Connection connection, String domainId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO injectors_contracts_domains (injector_contract_id, domain_id, tenant_id)"
                + " VALUES (?, ?, ?)")) {
      statement.setString(1, contractId);
      statement.setString(2, domainId);
      statement.setString(3, tenantId);
      statement.executeUpdate();
    }
    return null;
  }

  private Void migrate(Connection connection) throws Exception {
    migration.migrate(
        new Context() {
          @Override
          public Configuration getConfiguration() {
            return null;
          }

          @Override
          public Connection getConnection() {
            return connection;
          }
        });
    return null;
  }

  private static int backendPid(Connection connection) throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()");
        ResultSet resultSet = statement.executeQuery()) {
      resultSet.next();
      return resultSet.getInt(1);
    }
  }

  /** Polls {@code pg_locks} until the session of this backend waits on a lock. */
  private void awaitWaiting(int pid) throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(30);
    while (System.nanoTime() < deadline) {
      Long waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_locks WHERE pid = ? AND NOT granted", Long.class, pid);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    fail("the session %d never waited on a lock", pid);
  }

  private List<String> linkedDomainIds() {
    return jdbc.queryForList(
        "SELECT domain_id FROM injectors_contracts_domains WHERE injector_contract_id = ?",
        String.class,
        contractId);
  }

  private boolean domainExists(String domainId) {
    return !jdbc.queryForList("SELECT 1 FROM domains WHERE domain_id = ?", Integer.class, domainId)
        .isEmpty();
  }
}
