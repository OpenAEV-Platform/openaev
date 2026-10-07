package io.openaev.opencti.connectors.service;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static io.openaev.opencti.connectors.Constants.PROCESS_STIX_GROUP_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Group;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.opencti.client.OpenCTIClient;
import io.openaev.opencti.client.mutations.Mutation;
import io.openaev.opencti.client.mutations.Ping;
import io.openaev.opencti.client.mutations.QueryTypeFields;
import io.openaev.opencti.client.mutations.RegisterConnector;
import io.openaev.opencti.config.OpenCTIConfig;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.impl.IocValidationConnector;
import io.openaev.opencti.connectors.impl.SecurityCoverageConnector;
import io.openaev.service.AbstractPrivilegeService;
import io.openaev.service.TenantGroupService;
import io.openaev.utils.fixtures.opencti.ResponseFixture;
import java.io.IOException;
import java.util.ArrayList;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Pins the tenant scope of the OpenCTI connector register/ping flow, with {@code
 * marking_definitions} armed.
 *
 * <p>{@code OpenCTIConnectorRegisterPingJob} (Quartz) calls {@link
 * OpenCTIConnectorService#registerOrPingAllConnectors()}, which registers or pings every configured
 * connector; each one goes through {@code OpenCTIService} to {@link
 * PrivilegeService#ensurePrivilegedUserExistsForConnector}, whose {@code @Transactional} method
 * ensures the connector's well-known group. {@code Group.markings} is an eager association over
 * {@code marking_definitions}, a tenant-scoped table, so before this scope was set the flow read it
 * with no scope at all and the group came back granting no marking.
 *
 * <p>The scope must be the connector's own tenant, not every tenant and not the default one: {@code
 * marking_definitions.tenant_id} is NOT NULL, so a marking belongs to exactly one tenant and a
 * group may only grant markings of its own tenant. The connectors here live in a NON-default tenant
 * on purpose, so pointing the scope at the default tenant is as red as leaving it unset.
 *
 * <p>Observation point: the flow returns nothing, and reading {@code groups_markings} afterwards
 * would measure the test's own scope rather than the job's. The spy on {@link TenantGroupService}
 * captures the {@code Group} exactly as the connector transaction loaded it, with its eager
 * collection already initialized, and records the scope that transaction was carrying.
 *
 * <p>NOT {@code @Transactional}: the assertions read the scope of the transaction {@code
 * PrivilegeService} owns, and a surrounding test transaction would make it join the test's instead.
 */
@TestPropertySource(
    properties = {
      "openaev.tenant.active-tables=marking_definitions",
      "openaev.xtm.opencti."
          + OpenCTIConnectorPrivilegeScopeTest.CONNECTOR_TENANT_ID
          + ".enable=true",
      "openaev.xtm.opencti."
          + OpenCTIConnectorPrivilegeScopeTest.CONNECTOR_TENANT_ID
          + ".url=http://opencti.invalid",
      "openaev.xtm.opencti."
          + OpenCTIConnectorPrivilegeScopeTest.CONNECTOR_TENANT_ID
          + ".token=2f1a6d84-0001-4b5e-9a01-6d9c1b7e0001",
      "openaev.xtm.opencti." + OpenCTIConnectorPrivilegeScopeTest.GHOST_TENANT_ID + ".enable=true",
      "openaev.xtm.opencti."
          + OpenCTIConnectorPrivilegeScopeTest.GHOST_TENANT_ID
          + ".url=http://opencti.invalid",
      "openaev.xtm.opencti."
          + OpenCTIConnectorPrivilegeScopeTest.GHOST_TENANT_ID
          + ".token=2f1a6d84-0002-4b5e-9a01-6d9c1b7e0002"
    })
@DisplayName("OpenCTI connector register/ping tenant scope (marking_definitions armed)")
class OpenCTIConnectorPrivilegeScopeTest extends IntegrationTest {

  /**
   * The tenant the connector under test belongs to; seeded by this class, never the default one.
   */
  static final String CONNECTOR_TENANT_ID = "2f1a6d84-1001-4b5e-9a01-6d9c1b7e1001";

  /**
   * A configured connector whose tenant does not exist. Reachable in production: the connector map
   * comes from configuration, which outlives a purged tenant. It must not stop the other connectors
   * from being registered.
   */
  static final String GHOST_TENANT_ID = "2f1a6d84-1002-4b5e-9a01-6d9c1b7e1002";

  private static final String CONNECTOR_GROUP_ID =
      AbstractPrivilegeService.getUUIDFromName(PROCESS_STIX_GROUP_ID, CONNECTOR_TENANT_ID);

  @Autowired private OpenCTIConnectorService openCTIConnectorService;
  @Autowired private DataSource dataSource;
  @MockitoBean private OpenCTIClient openCTIClient;
  @MockitoSpyBean private TenantGroupService tenantGroupService;

  private JdbcTemplate jdbc;
  private String connectorTenantMarkingId;
  private String defaultTenantMarkingId;

  /** The scope and the group the connector transaction actually saw, recorded through the spy. */
  private final AtomicReference<String> observedScope = new AtomicReference<>();

  private final AtomicReference<Group> observedConnectorGroup = new AtomicReference<>();

  @BeforeEach
  void armTheSpyAndSeedTheConnectorTenant() throws IOException {
    jdbc = new JdbcTemplate(dataSource);
    connectorTenantMarkingId = UUID.randomUUID().toString();
    defaultTenantMarkingId = UUID.randomUUID().toString();

    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now()) ON CONFLICT DO NOTHING",
        CONNECTOR_TENANT_ID,
        "connector-scope-" + CONNECTOR_TENANT_ID);

    when(openCTIClient.execute(any(), any(), any(QueryTypeFields.class)))
        .thenReturn(ResponseFixture.getSchemaResponseWithJwks());
    when(openCTIClient.execute(any(), any(), any(RegisterConnector.class)))
        .thenReturn(ResponseFixture.getOkResponse());
    when(openCTIClient.execute(any(), any(), any(Ping.class)))
        .thenReturn(ResponseFixture.getOkResponse());

    openCTIConnectorService.clearRegisterBackoff();
    openCTIConnectorService.getConnectors().forEach(c -> c.setRegistered(false));

    observedScope.set(null);
    observedConnectorGroup.set(null);
    Mockito.doAnswer(
            invocation -> {
              @SuppressWarnings("unchecked")
              Optional<Group> loaded = (Optional<Group>) invocation.callRealMethod();
              if (CONNECTOR_GROUP_ID.equals(invocation.getArgument(0))) {
                observedScope.set(currentScope());
                loaded.ifPresent(observedConnectorGroup::set);
              }
              return loaded;
            })
        .when(tenantGroupService)
        .findByIdAndTenant(Mockito.anyString(), Mockito.anyString());
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM groups_markings WHERE group_id = ?", CONNECTOR_GROUP_ID);
    jdbc.update("DELETE FROM users_groups WHERE group_id = ?", CONNECTOR_GROUP_ID);
    jdbc.update("DELETE FROM users_tenants WHERE tenant_id = ?", CONNECTOR_TENANT_ID);
    jdbc.update(
        "DELETE FROM tokens WHERE token_user IN (SELECT user_id FROM users WHERE user_email = ?)",
        connectorEmail());
    jdbc.update("DELETE FROM users WHERE user_email = ?", connectorEmail());
    jdbc.update(
        "DELETE FROM marking_definitions WHERE marking_definition_id IN (?, ?)",
        connectorTenantMarkingId,
        defaultTenantMarkingId);
    // Cascades groups, roles and any remaining users_tenants row of that tenant.
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", CONNECTOR_TENANT_ID);
  }

  private String connectorEmail() {
    return PrivilegeService.CONNECTOR_EMAIL_PATTERN.formatted(
        connectorUnderTest().getServiceAccountId());
  }

  private static OpenCTIConfig openCTIConfigOf(ConnectorBase connector) {
    return switch (connector) {
      case SecurityCoverageConnector coverage -> coverage.getOpenCTIConfig();
      case IocValidationConnector validation -> validation.getOpenCTIConfig();
      default -> throw new IllegalStateException("Unexpected connector " + connector.getClass());
    };
  }

  private static void setOpenCTIConfig(ConnectorBase connector, OpenCTIConfig config) {
    switch (connector) {
      case SecurityCoverageConnector coverage -> coverage.setOpenCTIConfig(config);
      case IocValidationConnector validation -> validation.setOpenCTIConfig(config);
      default -> throw new IllegalStateException("Unexpected connector " + connector.getClass());
    }
  }

  private ConnectorBase connectorUnderTest() {
    return openCTIConnectorService.getConnectors().stream()
        .filter(c -> CONNECTOR_TENANT_ID.equals(c.getTenantId()))
        .findFirst()
        .orElseThrow(
            () -> new IllegalStateException("the connector under test is not configured any more"));
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

  private void grantMarkingToConnectorGroup(String markingId) {
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        CONNECTOR_GROUP_ID,
        markingId);
  }

  private String currentScope() {
    return (String)
        entityManager
            .createNativeQuery("SELECT current_setting('app.current_tenants', true)")
            .getSingleResult();
  }

  @Nested
  @DisplayName("the connector transaction carries its own tenant's scope")
  class ConnectorScope {

    @Test
    @DisplayName(
        "given the connector register and ping flow, should ensure the well-known group under the"
            + " connector tenant's scope")
    void given_connectorRegisterPingFlow_should_ensureGroupUnderConnectorTenantScope() {
      // Arrange
      seedMarking(connectorTenantMarkingId, CONNECTOR_TENANT_ID, "connector-own");

      // Act
      openCTIConnectorService.registerOrPingAllConnectors();

      // Assert
      assertThat(observedScope.get())
          .as("the connector flow must ensure its well-known group under its own tenant's scope")
          .isEqualTo(CONNECTOR_TENANT_ID);
    }

    @Test
    @DisplayName(
        "given a marking in the connector tenant and one in the default tenant, should hydrate only"
            + " the connector tenant's grant")
    void given_markingsInTwoTenants_should_hydrateOnlyTheConnectorTenantGrant() {
      // Arrange - the first pass creates the well-known group, the grants are seeded onto it, the
      // second pass is the one observed.
      openCTIConnectorService.registerOrPingAllConnectors();
      seedMarking(connectorTenantMarkingId, CONNECTOR_TENANT_ID, "connector-own");
      seedMarking(defaultTenantMarkingId, DEFAULT_TENANT_UUID, "connector-other");
      grantMarkingToConnectorGroup(connectorTenantMarkingId);
      grantMarkingToConnectorGroup(defaultTenantMarkingId);
      openCTIConnectorService.clearRegisterBackoff();
      observedScope.set(null);
      observedConnectorGroup.set(null);

      // Act
      openCTIConnectorService.registerOrPingAllConnectors();

      // Assert
      // The grants themselves survive the pass. The flow saves the group it loaded, so a collection
      // hydrated short of what the table holds could in principle be written back as a deletion.
      // Asserted before the hydration itself so a reverted fix reports this first, not second.
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM groups_markings WHERE group_id = ?",
                  Integer.class,
                  CONNECTOR_GROUP_ID))
          .as("ensuring the well-known group must not drop a marking grant it could not read")
          .isEqualTo(2);
      Group loaded = observedConnectorGroup.get();
      assertThat(loaded)
          .as("the second pass must find the well-known group the first pass created")
          .isNotNull();
      List<String> hydrated =
          loaded.getMarkings().stream().map(MarkingDefinition::getId).sorted().toList();
      assertThat(hydrated)
          .as(
              "unscoped the collection comes back empty; widened to every tenant, or pointed at the"
                  + " default tenant, it carries the other tenant's grant")
          .containsExactly(connectorTenantMarkingId);
    }
  }

  @Nested
  @DisplayName("the flow still works at the edges")
  class Edges {

    @Test
    @DisplayName(
        "given a configured connector whose tenant no longer exists, should still register the"
            + " others")
    void given_connectorWhoseTenantNoLongerExists_should_stillRegisterTheOthers() {
      // Arrange - GHOST_TENANT_ID is configured and never seeded, so its own transaction fails on
      // the tenant foreign key.
      seedMarking(connectorTenantMarkingId, CONNECTOR_TENANT_ID, "connector-own");

      // Act / Assert
      assertThatCode(() -> openCTIConnectorService.registerOrPingAllConnectors())
          .doesNotThrowAnyException();
      assertThat(observedScope.get())
          .as("the ghost connector must not stop the healthy one from being registered")
          .isEqualTo(CONNECTOR_TENANT_ID);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM groups WHERE group_id = ?",
                  Integer.class,
                  CONNECTOR_GROUP_ID))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM groups WHERE tenant_id = ?",
                  Integer.class,
                  GHOST_TENANT_ID))
          .as("nothing is written for a tenant that does not exist")
          .isZero();
    }

    @Test
    @DisplayName("given no connector to register, should do nothing")
    void given_noConnectorToRegister_should_doNothing() throws IOException {
      // Arrange - disable every configured connector, the shape of a platform with no OpenCTI
      // configuration at all: registerOrPingAllConnectors returns before any transaction is opened.
      List<ConnectorBase> connectors = new ArrayList<>(openCTIConnectorService.getConnectors());
      List<OpenCTIConfig> configs =
          connectors.stream().map(OpenCTIConnectorPrivilegeScopeTest::openCTIConfigOf).toList();
      connectors.forEach(c -> setOpenCTIConfig(c, null));

      try {
        // Act / Assert
        assertThatCode(() -> openCTIConnectorService.registerOrPingAllConnectors())
            .doesNotThrowAnyException();
        assertThat(observedScope.get())
            .as("no connector means no well-known group to ensure and no scope to set")
            .isNull();
        Mockito.verify(openCTIClient, Mockito.never()).execute(any(), any(), any(Mutation.class));
      } finally {
        for (int i = 0; i < connectors.size(); i++) {
          setOpenCTIConfig(connectors.get(i), configs.get(i));
        }
      }
    }
  }
}
