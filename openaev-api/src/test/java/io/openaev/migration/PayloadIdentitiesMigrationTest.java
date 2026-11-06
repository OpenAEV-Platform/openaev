package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Connection;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261106120000000: payload identities")
class PayloadIdentitiesMigrationTest extends IntegrationTest {

  @Autowired private V6_20261106120000000__Payload_identities migration;

  private final String payloadId = UUID.randomUUID().toString();
  private final String externalId = UUID.randomUUID().toString();

  // Puts the schema back as it was before the migration, inside the test transaction.
  @BeforeEach
  void seedOnePayloadBeforeIdentities() {
    execute(
        "ALTER TABLE payloads ADD COLUMN payload_external_id VARCHAR(255),"
            + " ADD COLUMN payload_source VARCHAR(255), ADD COLUMN payload_collector_type"
            + " VARCHAR(255), ALTER COLUMN payload_identity DROP NOT NULL,"
            + " ALTER COLUMN payload_version DROP NOT NULL");
    execute(
        "INSERT INTO payloads (payload_id, payload_type, payload_name, tenant_id,"
            + " payload_external_id, payload_source) VALUES ('%s', 'Command', 'p', '%s', '%s',"
            + " 'COMMUNITY')",
        payloadId, DEFAULT_TENANT_UUID, externalId);
  }

  @Test
  @DisplayName(
      "Each payload gets an identity with its origin, at version 1.0.0, and a rerun is safe")
  void given_payloadBeforeIdentities_should_moveItsOriginToAnIdentity() {
    // Act
    runMigration();
    runMigration();

    // Assert
    assertThat(
            rows(
                "SELECT pi.payload_external_id || ':' || pi.payload_source || ':'"
                    + " || p.payload_version FROM payloads p JOIN payload_identities pi"
                    + " ON pi.payload_identity_id = p.payload_identity WHERE p.payload_id = '%s'",
                payloadId))
        .containsExactly(externalId + ":COMMUNITY:1.0.0");
    assertThat(
            rows(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'payloads'"
                    + " AND column_name IN ('payload_external_id', 'payload_source',"
                    + " 'payload_collector_type')"))
        .isEmpty();
  }

  private void runMigration() {
    withConnection(connection -> migration.migrate(contextOn(connection)));
  }

  @SuppressWarnings("unchecked")
  private List<String> rows(String sql, Object... args) {
    return entityManager.createNativeQuery(sql.formatted(args)).getResultList();
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
