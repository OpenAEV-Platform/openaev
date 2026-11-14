package io.openaev.xtmhub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
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
 * Isolation of {@code tenant_xtmhub_registrations} on its own rows, at the repository.
 *
 * <p>Every path of {@link XtmHubService} names the tenant in its query ({@code findByTenantId},
 * {@code findByTenantIdIn}, {@code deleteByTenantId}), so no endpoint can show the table's own
 * scope: a query for another tenant matches nothing whether the table is active or not. The case is
 * therefore a read of the repository under one tenant's scope, with one registration per tenant: a
 * read by id, and the read by a list of tenant ids that the news feed status makes, here naming
 * both tenants so that only the scope of {@code tenant_xtmhub_registrations} can drop the other.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction.
 * The seed is committed and swept after each test.
 */
@CoversTenantIsolation("tenant_xtmhub_registrations")
@WithMockUser(isAdmin = true)
@DisplayName("tenant_xtmhub_registrations refuses another tenant's row under one tenant's scope")
class TenantXtmHubRegistrationScopeIsolationTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private TenantXtmHubRegistrationRepository repository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private final List<String> seededTenants = new ArrayList<>();
  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String ownRegistration;
  private String foreignRegistration;

  @BeforeEach
  void seedOneRegistrationPerTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant();
    tenantB = seedTenant();
    ownRegistration = seedRegistration(tenantA);
    foreignRegistration = seedRegistration(tenantB);
  }

  @AfterEach
  void sweep() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM tenant_xtmhub_registrations WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Test
  @DisplayName("given a registration id, should read its own and not the other tenant's")
  void given_registrationId_should_readOwnAndNotOtherTenants() {
    // Act
    boolean[] found =
        tenantTx.execute(
            TxCtx.forTenant(tenantA),
            () ->
                new boolean[] {
                  repository.findById(ownRegistration).isPresent(),
                  repository.findById(foreignRegistration).isPresent()
                });

    // Assert
    assertTrue(found[0], "A's registration must be read under A's scope");
    assertFalse(found[1], "B's registration must not be read under A's scope");
  }

  @Test
  @DisplayName("given both tenant ids, should return its own registration only")
  void given_bothTenantIds_should_returnOwnRegistrationOnly() {
    // Act
    List<String> registrations =
        tenantTx.execute(
            TxCtx.forTenant(tenantA),
            () ->
                repository.findByTenantIdIn(List.of(tenantA, tenantB)).stream()
                    .map(TenantXtmHubRegistration::getId)
                    .toList());

    // Assert
    assertEquals(
        List.of(ownRegistration), registrations, "only A's registration is read under A's scope");
  }

  // -- FIXTURES --

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "xtmhub-registration-iso-" + id);
    seededTenants.add(id);
    return id;
  }

  private String seedRegistration(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenant_xtmhub_registrations (registration_id, tenant_id) VALUES (?, ?)",
        id,
        tenantId);
    return id;
  }
}
