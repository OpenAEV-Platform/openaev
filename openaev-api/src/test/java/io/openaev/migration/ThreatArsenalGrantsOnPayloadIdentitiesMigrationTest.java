package io.openaev.migration;

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
@DisplayName("Migration V6_20261110090100000: threat arsenal grants on payload identities")
class ThreatArsenalGrantsOnPayloadIdentitiesMigrationTest extends IntegrationTest {

  @Autowired private V6_20261110090100000__Threat_arsenal_grants_on_payload_identities migration;

  private final String contractId = UUID.randomUUID().toString();
  private final String tenantA = UUID.randomUUID().toString();
  private final String tenantB = UUID.randomUUID().toString();
  private final String payloadA = UUID.randomUUID().toString();
  private final String payloadB = UUID.randomUUID().toString();
  private final String tenantGroup = UUID.randomUUID().toString();
  private final String platformGroup = UUID.randomUUID().toString();

  @BeforeEach
  void seedOneContractIdInTwoTenants() {
    for (String[] row :
        List.of(new String[] {tenantA, payloadA}, new String[] {tenantB, payloadB})) {
      execute("INSERT INTO tenants (tenant_id, tenant_name) VALUES ('%s', '%s')", row[0], row[0]);
      execute(
          "INSERT INTO payload_identities (payload_identity_id, tenant_id, payload_source)"
              + " VALUES ('%s', '%s', 'MANUAL')",
          row[1], row[0]);
      execute(
          "INSERT INTO payloads (payload_id, payload_type, payload_name, tenant_id,"
              + " payload_identity, payload_version) VALUES ('%s', 'Command', 'p', '%s', '%s',"
              + " '1.0.0')",
          row[1], row[0], row[1]);
      execute(
          "INSERT INTO injectors_contracts (injector_contract_id, injector_contract_content,"
              + " injector_contract_payload, tenant_id) VALUES ('%s', '{}', '%s', '%s')",
          contractId, row[1], row[0]);
    }
    execute(
        "INSERT INTO groups (group_id, group_name, tenant_id) VALUES ('%s', 'g', '%s')",
        tenantGroup, tenantA);
    execute(
        "INSERT INTO groups (group_id, group_name, tenant_id) VALUES ('%s', 'g', NULL)",
        platformGroup);
    for (String group : List.of(tenantGroup, platformGroup)) {
      execute(
          "INSERT INTO grants (grant_id, grant_group, grant_name, grant_resource,"
              + " grant_resource_type) VALUES ('%s', '%s', 'OBSERVER', '%s', 'THREAT_ARSENAL')",
          UUID.randomUUID().toString(), group, contractId);
    }
  }

  @Test
  @DisplayName("Grants move to each tenant's payload identity, a rerun changes nothing")
  void given_grantsOnAContractInTwoTenants_should_targetEachTenantsPayloadIdentity() {
    // Act
    runMigration();
    runMigration();

    // Assert
    assertThat(grantResourcesOf(tenantGroup)).containsExactly(payloadA);
    assertThat(grantResourcesOf(platformGroup)).containsExactlyInAnyOrder(payloadA, payloadB);
  }

  private List<String> grantResourcesOf(String groupId) {
    @SuppressWarnings("unchecked")
    List<String> resources =
        entityManager
            .createNativeQuery("SELECT grant_resource FROM grants WHERE grant_group = :group")
            .setParameter("group", groupId)
            .getResultList();
    return resources;
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
