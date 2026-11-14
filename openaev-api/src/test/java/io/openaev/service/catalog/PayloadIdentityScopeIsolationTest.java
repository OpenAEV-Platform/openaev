package io.openaev.service.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.payload.PayloadIdentity;
import io.openaev.database.repository.PayloadIdentityRepository;
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
 * Isolation of {@code payload_identities} on its own rows, at the repository.
 *
 * <p>No endpoint addresses a payload identity and no service reads one by id: the catalog services
 * read identities by an explicit tenant id, and a payload loads its identity through its own row,
 * which {@code payloads} already scopes. So the case is a read of the repository by id under one
 * tenant's scope, with one identity per tenant and no parent row between the caller and either.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction.
 * The seed is committed and swept after each test.
 */
@CoversTenantIsolation("payload_identities")
@WithMockUser(isAdmin = true)
@DisplayName("payload_identities refuses another tenant's identity under one tenant's scope")
class PayloadIdentityScopeIsolationTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private PayloadIdentityRepository repository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private final List<String> seededTenants = new ArrayList<>();
  private JdbcTemplate jdbc;
  private String tenantA;
  private String ownIdentity;
  private String foreignIdentity;

  @BeforeEach
  void seedOneIdentityPerTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant();
    String tenantB = seedTenant();
    ownIdentity = seedIdentity(tenantA);
    foreignIdentity = seedIdentity(tenantB);
  }

  @AfterEach
  void sweep() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM payload_identities WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Test
  @DisplayName("given an identity id, should read its own identity and not the other tenant's")
  void given_identityId_should_readOwnAndNotOtherTenants() {
    // Act
    boolean[] found =
        tenantTx.execute(
            TxCtx.forTenant(tenantA),
            () ->
                new boolean[] {
                  repository.findById(ownIdentity).isPresent(),
                  repository.findById(foreignIdentity).isPresent()
                });

    // Assert
    assertTrue(found[0], "A's identity must be read under A's scope");
    assertFalse(found[1], "B's identity must not be read under A's scope");
  }

  @Test
  @DisplayName("given both identity ids, should return its own identity only")
  void given_bothIdentityIds_should_returnOwnIdentityOnly() {
    // Act
    List<String> identities =
        tenantTx.execute(
            TxCtx.forTenant(tenantA),
            () ->
                repository.findAllById(List.of(ownIdentity, foreignIdentity)).stream()
                    .map(PayloadIdentity::getId)
                    .toList());

    // Assert
    assertEquals(List.of(ownIdentity), identities, "only A's identity is read under A's scope");
  }

  // -- FIXTURES --

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "payload-identity-iso-" + id);
    seededTenants.add(id);
    return id;
  }

  private String seedIdentity(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO payload_identities (payload_identity_id, payload_external_id, payload_source,"
            + " tenant_id) VALUES (?, ?, 'MANUAL', ?)",
        id,
        "payload-identity-iso-" + id,
        tenantId);
    return id;
  }
}
