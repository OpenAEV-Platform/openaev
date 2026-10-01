package io.openaev.runner;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.database.model.User.ADMIN_UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.service.AbstractPrivilegeService;
import io.openaev.service.TenantGroupService;
import io.openaev.service.account.AdminPrivilegeService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Pins the tenant scope of the platform bootstrap transaction, with {@code marking_definitions}
 * armed.
 *
 * <p>{@link InitAdminCommandLineRunner#run} ensures two well-known "Administrators" groups, and
 * {@code Group.markings} is an eager association over {@code marking_definitions}, a tenant-scoped
 * table. Before this scope was set the bootstrap ran with no scope at all, so {@code
 * can_access_tenant} denied every row and both groups were loaded granting no marking - on every
 * boot, and on every well-known group ensure.
 *
 * <p>The scope has to be the default tenant, not every tenant: {@code
 * marking_definitions.tenant_id} is NOT NULL, so a marking always belongs to exactly one tenant and
 * a group may only grant markings of its own tenant. The second test below fails either way round,
 * on an unscoped read (nothing comes back) and on a widened one (another tenant's grant comes
 * back).
 *
 * <p>Observation point: the bootstrap returns nothing, and reading {@code groups_markings}
 * afterwards would measure the test's own scope rather than the runner's. The spy on {@link
 * TenantGroupService} - the signature the fail-closed gate flagged - captures the {@code Group}
 * exactly as the bootstrap transaction loaded it, with its eager collection already initialized,
 * and records the scope that transaction was carrying.
 *
 * <p>NOT {@code @Transactional}: the assertions read the scope of the transaction the runner owns,
 * and a surrounding test transaction would make the runner join it instead.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=marking_definitions")
@DisplayName("Platform bootstrap tenant scope (marking_definitions armed)")
class InitAdminBootstrapScopeTest extends IntegrationTest {

  private static final String DEFAULT_ADMIN_GROUP_ID =
      AbstractPrivilegeService.getUUIDFromName(
          AdminPrivilegeService.ADMIN_GROUP_ID, DEFAULT_TENANT_UUID);

  @Autowired private InitAdminCommandLineRunner initAdminCommandLineRunner;
  @Autowired private DataSource dataSource;
  @MockitoSpyBean private TenantGroupService tenantGroupService;

  private JdbcTemplate jdbc;
  private String otherTenantId;
  private String defaultTenantMarkingId;
  private String otherTenantMarkingId;

  /** The scope and the group the bootstrap transaction actually saw, recorded through the spy. */
  private final AtomicReference<String> observedScope = new AtomicReference<>();

  private final AtomicReference<Group> observedDefaultAdminGroup = new AtomicReference<>();

  @BeforeEach
  void armTheSpyAndSeedTwoTenantsWithOneMarkingEach() {
    jdbc = new JdbcTemplate(dataSource);
    otherTenantId = UUID.randomUUID().toString();
    defaultTenantMarkingId = UUID.randomUUID().toString();
    otherTenantMarkingId = UUID.randomUUID().toString();

    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        otherTenantId,
        "bootstrap-scope-" + otherTenantId);

    observedScope.set(null);
    observedDefaultAdminGroup.set(null);
    Mockito.doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Optional<Group> loaded = (Optional<Group>) invocation.callRealMethod();
              if (DEFAULT_ADMIN_GROUP_ID.equals(invocation.getArgument(0))) {
                observedScope.set(currentScope());
                loaded.ifPresent(observedDefaultAdminGroup::set);
              }
              return loaded;
            })
        .when(tenantGroupService)
        .findByIdAndTenant(Mockito.anyString(), Mockito.anyString());
  }

  @AfterEach
  void cleanup() {
    jdbc.update(
        "DELETE FROM groups_markings WHERE marking_id IN (?, ?)",
        defaultTenantMarkingId,
        otherTenantMarkingId);
    jdbc.update(
        "DELETE FROM marking_definitions WHERE marking_definition_id IN (?, ?)",
        defaultTenantMarkingId,
        otherTenantMarkingId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", otherTenantId);
  }

  /**
   * Ground truth through raw JDBC on purpose: {@link JdbcTemplate} never reaches the Hibernate
   * statement inspector, so seeding and cleanup are not themselves filtered by a scope the test
   * would then be measuring.
   */
  private void seedMarking(String markingId, String tenantId, String definition) {
    jdbc.update(
        "INSERT INTO marking_definitions (marking_definition_id, marking_definition_type,"
            + " marking_definition_definition, marking_definition_color,"
            + " marking_definition_order, tenant_id) VALUES (?, 'TLP', ?, '#ffffff', 0, ?)",
        markingId,
        definition,
        tenantId);
  }

  private void grantMarkingToDefaultAdminGroup(String markingId) {
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)"
            + " ON CONFLICT DO NOTHING",
        DEFAULT_ADMIN_GROUP_ID,
        markingId);
  }

  private String currentScope() {
    return (String)
        entityManager
            .createNativeQuery("SELECT current_setting('app.current_tenants', true)")
            .getSingleResult();
  }

  @Nested
  @DisplayName("the bootstrap transaction carries the default tenant's scope")
  class BootstrapScope {

    @Test
    @DisplayName(
        "given the platform bootstrap, should load the well-known group under the default tenant's"
            + " scope")
    void given_platformBootstrap_should_loadWellKnownGroupUnderDefaultTenantScope() {
      // Arrange
      seedMarking(defaultTenantMarkingId, DEFAULT_TENANT_UUID, "bootstrap-default");

      // Act
      initAdminCommandLineRunner.run();

      // Assert
      assertThat(observedScope.get())
          .as("the bootstrap must load the well-known groups under the default tenant's scope")
          .isEqualTo(DEFAULT_TENANT_UUID);
    }

    @Test
    @DisplayName(
        "given a marking in the default tenant and one in another tenant, should hydrate only the"
            + " default tenant's grant")
    void given_markingsInTwoTenants_should_hydrateOnlyTheDefaultTenantGrant() {
      // Arrange
      seedMarking(defaultTenantMarkingId, DEFAULT_TENANT_UUID, "bootstrap-default");
      seedMarking(otherTenantMarkingId, otherTenantId, "bootstrap-other");
      grantMarkingToDefaultAdminGroup(defaultTenantMarkingId);
      grantMarkingToDefaultAdminGroup(otherTenantMarkingId);

      // Act
      initAdminCommandLineRunner.run();

      // Assert
      Group loaded = observedDefaultAdminGroup.get();
      assertThat(loaded)
          .as("the bootstrap must find the existing well-known default-tenant admin group")
          .isNotNull();
      List<String> hydrated =
          loaded.getMarkings().stream().map(MarkingDefinition::getId).sorted().toList();
      assertThat(hydrated)
          .as(
              "unscoped the collection comes back empty; widened to every tenant it also carries the"
                  + " other tenant's grant")
          .containsExactly(defaultTenantMarkingId);
    }
  }

  @Nested
  @DisplayName("a platform with no marking definition still boots")
  class FirstBoot {

    @Test
    @DisplayName("given no marking definition at all, should still complete the bootstrap")
    void given_noMarkingDefinitionAtAll_should_stillCompleteTheBootstrap() {
      // Arrange - nothing seeded: this is the first-boot shape, the scope resolves without reading
      // the tenant registry (TxCtx.forTenant passes straight through TenantScopeIntentionResolver)
      // and the clearance it derives is simply empty.

      // Act / Assert
      assertThatCode(() -> initAdminCommandLineRunner.run()).doesNotThrowAnyException();
      assertThat(observedScope.get()).isEqualTo(DEFAULT_TENANT_UUID);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM users WHERE user_id = ?", Integer.class, ADMIN_UUID))
          .isEqualTo(1);
    }
  }
}
