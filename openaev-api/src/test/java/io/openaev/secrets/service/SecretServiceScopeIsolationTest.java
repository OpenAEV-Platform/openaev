package io.openaev.secrets.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Secret.SECRET_TYPE;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Isolation of {@code secrets} on its own rows, through {@link SecretService}, the one service that
 * reads and deletes a secret by id.
 *
 * <p>No endpoint addresses a secret: the credential endpoints reach one through its {@code
 * secret_references} row, which is refused first. Here each tenant owns one secret and the service
 * is called under one tenant's scope, so no parent row stands between the caller and either secret
 * and the refusal comes from the scope of {@code secrets}.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction.
 * The seed is committed and swept after each test.
 */
@CoversTenantIsolation("secrets")
@WithMockUser(isAdmin = true)
@DisplayName("secrets refuses another tenant's secret under one tenant's scope")
class SecretServiceScopeIsolationTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private SecretService secretService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private final List<String> seededTenants = new ArrayList<>();
  private JdbcTemplate jdbc;
  private String tenantA;
  private String ownSecret;
  private String foreignSecret;

  @BeforeEach
  void seedOneSecretPerTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant();
    String tenantB = seedTenant();
    ownSecret = seedSecret(tenantA);
    foreignSecret = seedSecret(tenantB);
  }

  @AfterEach
  void sweep() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM secrets WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Test
  @DisplayName("given its own secret id, should read it")
  void given_ownSecretId_should_readIt() {
    // Act
    String found =
        tenantTx.execute(
            TxCtx.forTenant(tenantA), () -> secretService.findByIdOrThrow(ownSecret).getId());

    // Assert
    assertEquals(ownSecret, found);
  }

  @Test
  @DisplayName("given another tenant's secret id, should not find it")
  void given_otherTenantSecretId_should_notFindIt() {
    // Act & Assert
    assertThrows(
        IllegalArgumentException.class,
        () ->
            tenantTx.execute(
                TxCtx.forTenant(tenantA), () -> secretService.findByIdOrThrow(foreignSecret)));
  }

  @Test
  @DisplayName("given another tenant's secret id, should not delete it")
  void given_otherTenantSecretId_should_notDeleteIt() {
    // Act
    tenantTx.execute(TxCtx.forTenant(tenantA), () -> secretService.deleteById(foreignSecret));

    // Assert
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT count(*) FROM secrets WHERE secret_id = ?", Integer.class, foreignSecret),
        "B's secret must survive a delete under A's scope");
  }

  // -- FIXTURES --

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "secret-iso-" + id);
    seededTenants.add(id);
    return id;
  }

  private String seedSecret(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO secrets (secret_id, secret_type, secret_hash_algorithm, secret_hash,"
            + " tenant_id) VALUES (?, ?, 'SHA', 'secret-iso-hash', ?)",
        id,
        SECRET_TYPE.HASH.name(),
        tenantId);
    return id;
  }
}
