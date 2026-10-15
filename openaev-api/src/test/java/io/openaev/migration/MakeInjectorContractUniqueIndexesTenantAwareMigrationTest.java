package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.migration.V6_20261014160000000__Make_injector_contract_unique_indexes_tenant_aware.EXTERNAL_ID_INDEX;
import static io.openaev.migration.V6_20261014160000000__Make_injector_contract_unique_indexes_tenant_aware.GLOBAL_EXTERNAL_ID_CONSTRAINT;
import static io.openaev.migration.V6_20261014160000000__Make_injector_contract_unique_indexes_tenant_aware.GLOBAL_PAYLOAD_CONSTRAINT;
import static io.openaev.migration.V6_20261014160000000__Make_injector_contract_unique_indexes_tenant_aware.PAYLOAD_INDEX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.WithMockUser;
import java.sql.Connection;
import java.util.List;
import org.flywaydb.core.api.FlywayException;
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
 * Verifies, on the live schema, that the unique keys of {@code injectors_contracts} are per tenant
 * once the migration has run, and that the migration refuses to build a per-tenant index over rows
 * that already collide instead of resolving them itself.
 *
 * <p>{@code @Transactional} so the DDL the diagnostic tests apply (dropping an index to seed a
 * collision) rolls back with the test transaction.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261014160000000: tenant-aware injector contract unique indexes")
class MakeInjectorContractUniqueIndexesTenantAwareMigrationTest extends IntegrationTest {

  private static final String COLLIDING_EXTERNAL_ID = "colliding-external-id";
  private static final String COLLIDING_PAYLOAD_ID = "colliding-payload-id";

  @Autowired
  private V6_20261014160000000__Make_injector_contract_unique_indexes_tenant_aware migration;

  @Nested
  @DisplayName("Schema")
  class Schema {

    @Test
    @DisplayName("The global unique constraints on external id and payload are gone")
    void given_migratedSchema_should_haveNoGlobalUniqueConstraint() {
      // Act
      List<String> uniqueIndexes = uniqueIndexNames();

      // Assert
      assertThat(uniqueIndexes)
          .doesNotContain(GLOBAL_EXTERNAL_ID_CONSTRAINT, GLOBAL_PAYLOAD_CONSTRAINT);
    }

    @Test
    @DisplayName("External id and payload are each unique per tenant, ignoring nulls")
    void given_migratedSchema_should_haveTenantAwarePartialUniqueIndexes() {
      // Act + Assert
      assertThat(indexDefinition(EXTERNAL_ID_INDEX))
          .contains("CREATE UNIQUE INDEX " + EXTERNAL_ID_INDEX)
          .contains("(injector_contract_external_id, tenant_id)")
          .contains("WHERE (injector_contract_external_id IS NOT NULL)");
      assertThat(indexDefinition(PAYLOAD_INDEX))
          .contains("CREATE UNIQUE INDEX " + PAYLOAD_INDEX)
          .contains("(injector_contract_payload, tenant_id)")
          .contains("WHERE (injector_contract_payload IS NOT NULL)");
    }

    @Test
    @DisplayName("Every unique index on the table carries tenant_id")
    void given_migratedSchema_should_haveNoUniqueIndexWithoutTenant() {
      // Act
      List<String> definitions =
          nativeStrings(
              "SELECT pg_get_indexdef(indexrelid) FROM pg_index"
                  + " WHERE indrelid = 'injectors_contracts'::regclass AND indisunique");

      // Assert
      assertThat(definitions).isNotEmpty().allSatisfy(d -> assertThat(d).contains("tenant_id"));
    }
  }

  @Nested
  @DisplayName("Duplicate diagnostic")
  class DuplicateDiagnostic {

    @Test
    @DisplayName("A tenant holding an external id twice stops the migration and names it")
    void given_anExternalIdHeldTwiceInOneTenant_should_failNamingValueAndTenant() {
      // Arrange
      execute("DROP INDEX IF EXISTS " + EXTERNAL_ID_INDEX);
      insertContract("collision-a", COLLIDING_EXTERNAL_ID, null);
      insertContract("collision-b", COLLIDING_EXTERNAL_ID, null);

      // Act + Assert
      assertThatThrownBy(this::runMigration)
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining(EXTERNAL_ID_INDEX)
          .hasMessageContaining(COLLIDING_EXTERNAL_ID)
          .hasMessageContaining(DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName("A tenant backing two contracts with one payload stops the migration and names it")
    void given_aPayloadHeldTwiceInOneTenant_should_failNamingValueAndTenant() {
      // Arrange
      execute("DROP INDEX IF EXISTS " + PAYLOAD_INDEX);
      execute(
          "INSERT INTO payloads (payload_id, payload_type, payload_name) VALUES ('"
              + COLLIDING_PAYLOAD_ID
              + "', 'Command', 'colliding payload')");
      insertContract("collision-a", null, COLLIDING_PAYLOAD_ID);
      insertContract("collision-b", null, COLLIDING_PAYLOAD_ID);

      // Act + Assert
      assertThatThrownBy(this::runMigration)
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining(PAYLOAD_INDEX)
          .hasMessageContaining(COLLIDING_PAYLOAD_ID)
          .hasMessageContaining(DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName("A per-tenant index name held by another shape stops the migration")
    void given_aPayloadIndexWithoutTenant_should_failInsteadOfDroppingTheGlobalOne() {
      // Arrange
      execute("DROP INDEX IF EXISTS " + PAYLOAD_INDEX);
      execute(
          "CREATE UNIQUE INDEX "
              + PAYLOAD_INDEX
              + " ON injectors_contracts (injector_contract_payload)");

      // Act + Assert
      assertThatThrownBy(this::runMigration)
          .isInstanceOf(FlywayException.class)
          .hasMessageContaining(PAYLOAD_INDEX)
          .hasMessageContaining("unexpected definition");
    }

    private void insertContract(String id, String externalId, String payloadId) {
      execute(
          "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
              + " injector_contract_external_id, injector_contract_payload) VALUES ('"
              + id
              + "', '{}', "
              + quoteOrNull(externalId)
              + ", "
              + quoteOrNull(payloadId)
              + ")");
    }

    private void runMigration() {
      withConnection(connection -> migration.migrate(contextOn(connection)));
    }
  }

  @Nested
  @DisplayName("Idempotency")
  class Idempotency {

    @Test
    @DisplayName("Re-running the migration is a no-op")
    void given_alreadyAppliedMigration_should_rerunWithoutError() {
      // Act + Assert
      assertThatCode(() -> withConnection(c -> migration.migrate(contextOn(c))))
          .doesNotThrowAnyException();
      assertThat(uniqueIndexNames()).contains(EXTERNAL_ID_INDEX, PAYLOAD_INDEX);
    }
  }

  // -- helpers --

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

  private static String quoteOrNull(String value) {
    return value == null ? "NULL" : "'" + value + "'";
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

  private List<String> uniqueIndexNames() {
    return nativeStrings(
        "SELECT c.relname::text FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid"
            + " WHERE i.indrelid = 'injectors_contracts'::regclass AND i.indisunique");
  }

  private String indexDefinition(String index) {
    List<String> definitions =
        nativeStrings(
            "SELECT pg_get_indexdef(i.indexrelid) FROM pg_index i"
                + " JOIN pg_class c ON c.oid = i.indexrelid"
                + " WHERE i.indrelid = 'injectors_contracts'::regclass AND c.relname = '"
                + index
                + "'");
    assertThat(definitions).as("index %s on injectors_contracts", index).hasSize(1);
    return definitions.getFirst();
  }

  @SuppressWarnings("unchecked")
  private List<String> nativeStrings(String sql) {
    return (List<String>) entityManager.createNativeQuery(sql).getResultList();
  }
}
