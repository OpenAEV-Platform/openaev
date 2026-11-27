package io.openaev.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantXtmHubRegistration;
import io.openaev.database.repository.TenantXtmHubRegistrationRepository;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utils.fixtures.tenants.TenantFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.WithoutTenantScope;
import io.openaev.xtmhub.XtmHubRegistrationStatus;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The table is active, and the migration runs on a plain connection with no scope: a statement the
 * scope hid would match no row and change nothing, silently, so the test counts the rows changed
 * and reads each row back.
 */
@WithoutTenantScope
@WithMockUser(isAdmin = true)
@DisplayName("Migration V6_20261116180000000: reset of the XTM Hub connectivity email markers")
class ResetXtmHubConnectivityEmailMarkersMigrationTest extends IntegrationTest {

  @Autowired private V6_20261116180000000__Reset_xtmhub_connectivity_email_markers migration;
  @Autowired private TenantXtmHubRegistrationRepository registrationRepository;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private JdbcTemplate jdbcTemplate;

  private final List<String> tenantIds = new ArrayList<>();

  @AfterEach
  void cleanup() {
    tenantIds.forEach(
        id -> {
          jdbcTemplate.update("delete from tenant_xtmhub_registrations where tenant_id = ?", id);
          tenantRepository.deleteById(id);
        });
  }

  @Test
  @DisplayName(
      "given tenants lost and registered, should mark the lost ones as notified and leave the"
          + " others as they are")
  void given_lostAndRegisteredTenants_should_markTheLostOnesAsNotified() {
    // Arrange
    String lostPending = register(XtmHubRegistrationStatus.LOST_CONNECTIVITY, true);
    String lostPendingToo = register(XtmHubRegistrationStatus.LOST_CONNECTIVITY, true);
    String lostNotified = register(XtmHubRegistrationStatus.LOST_CONNECTIVITY, false);
    String registered = register(XtmHubRegistrationStatus.REGISTERED, true);
    String registeredNotified = register(XtmHubRegistrationStatus.REGISTERED, false);
    int pendingBefore = pendingLostRegistrations();
    assertThat(pendingBefore).isGreaterThanOrEqualTo(2);

    // Act
    AtomicInteger changed = new AtomicInteger();
    withConnection(
        connection -> {
          Context context = contextOn(connection);
          changed.set(
              V6_20261116180000000__Reset_xtmhub_connectivity_email_markers
                  .resetMarkersOfLostTenants(context.getConnection()));
        });
    migrate();

    // Assert
    assertThat(changed.get()).isEqualTo(pendingBefore);
    assertThat(pendingLostRegistrations()).isZero();
    assertThat(eligible(lostPending)).isFalse();
    assertThat(eligible(lostPendingToo)).isFalse();
    assertThat(eligible(lostNotified)).isFalse();
    assertThat(eligible(registered)).isTrue();
    assertThat(eligible(registeredNotified)).isFalse();
  }

  private void migrate() {
    withConnection(connection -> migration.migrate(contextOn(connection)));
  }

  private String register(XtmHubRegistrationStatus status, boolean eligible) {
    Tenant tenant = TenantFixture.getTenant("xtmhub-reset-" + tenantIds.size());
    tenantComposer.forTenant(tenant).persist();
    tenantIds.add(tenant.getId());
    TenantXtmHubRegistration registration = new TenantXtmHubRegistration();
    registration.setToken("token-" + tenant.getId());
    registration.setRegistrationStatus(status);
    registration.setConnectivityEmailEligible(eligible);
    registration.setTenant(tenant);
    registrationRepository.save(registration);
    return tenant.getId();
  }

  private int pendingLostRegistrations() {
    return jdbcTemplate.queryForObject(
        "select count(*) from tenant_xtmhub_registrations"
            + " where registration_status = 'LOST_CONNECTIVITY'"
            + " and registration_connectivity_email_eligible",
        Integer.class);
  }

  private boolean eligible(String tenantId) {
    return jdbcTemplate.queryForObject(
        "select registration_connectivity_email_eligible from tenant_xtmhub_registrations"
            + " where tenant_id = ?",
        Boolean.class,
        tenantId);
  }

  @FunctionalInterface
  private interface ConnectionWork {
    void run(Connection connection) throws Exception;
  }

  private void withConnection(ConnectionWork work) {
    jdbcTemplate.execute(
        (Connection connection) -> {
          try {
            work.run(connection);
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new IllegalStateException(e);
          }
          return null;
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
