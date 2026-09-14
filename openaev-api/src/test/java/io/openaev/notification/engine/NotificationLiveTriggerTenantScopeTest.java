package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * A LIVE notification trigger with a filter re-checks that filter against the watched entity before
 * it fires, in {@code NotificationMatchingService.matches}. That method is {@code @Transactional}
 * with no {@code TxCtx}, and it is reached from an {@code @Async}
 * {@code @TransactionalEventListener} - a pool thread, after commit, with no ambient transaction.
 *
 * <p>Before the v2 activations it was scoped by the v1 Hibernate filter, which {@code
 * HibernateFilterTransactionAspect} enables on every {@code @Transactional} method from the
 * thread-local {@code NotificationEngineService} sets. Activating a table removes that filter, so
 * the re-check ran against {@code can_access_tenant} with an empty {@code app.current_tenants},
 * counted zero, and every filtered LIVE trigger on that resource silently stopped firing. No
 * exception either: {@code matches} catches and returns false.
 *
 * <p>The catalog maps ASSET to {@code Endpoint}, ASSET_GROUP to {@code AssetGroup} and FINDING to
 * {@code Finding}, so the same hole opens once per activation. This pins the assets case; the fix
 * is one scope in {@code NotificationEngineService} and covers all three.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction,
 * and the production path has none. Seeding auto-commits through {@link JdbcTemplate} and is swept.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=assets")
@WithMockUser(isAdmin = true)
@DisplayName("a filtered LIVE trigger still fires once its watched table is tenant-active")
class NotificationLiveTriggerTenantScopeTest extends IntegrationTest {

  @Autowired private NotificationEngineService notificationEngineService;
  @Autowired private NotificationTriggerCacheService triggerCacheService;
  @Autowired private DataSource dataSource;
  @Autowired private TestUserHolder testUserHolder;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String endpointId;
  private String triggerId;
  private String endpointName;

  @BeforeEach
  void seedATenantAnEndpointAndAFilteredLiveTrigger() throws Exception {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    endpointId = UUID.randomUUID().toString();
    triggerId = UUID.randomUUID().toString();
    endpointName = "notif-scope-" + UUID.randomUUID();

    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "notif-" + tenantId);
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        endpointId,
        endpointName,
        tenantId,
        endpointName);

    // A NON-EMPTY filter on purpose: an empty filter group short-circuits in matches() before any
    // query, and an instance trigger short-circuits earlier still. Only a filtered trigger reaches
    // the database, which is the case that broke.
    String filters =
        "{\"mode\":\"and\",\"filters\":[{\"key\":\"asset_name\",\"mode\":\"and\","
            + "\"operator\":\"eq\",\"values\":[\""
            + endpointName
            + "\"]}]}";
    jdbc.update(
        "INSERT INTO notification_triggers (notification_trigger_id, notification_trigger_name,"
            + " notification_trigger_type, notification_trigger_enabled,"
            + " notification_trigger_resource_type, notification_trigger_event_types,"
            + " notification_trigger_filters, tenant_id, user_id,"
            + " notification_trigger_created_at, notification_trigger_updated_at)"
            + " VALUES (?, ?, 'LIVE', true, 'ASSET', CAST(? AS jsonb), CAST(? AS jsonb), ?, ?,"
            + " now(), now())",
        triggerId,
        "notif-scope-trigger",
        "[\"CREATE\"]",
        filters,
        tenantId,
        testUserHolder.get().getId());

    triggerCacheService.invalidate();
  }

  @AfterEach
  void sweep() {
    jdbc.update("DELETE FROM notification_events WHERE notification_trigger_id = ?", triggerId);
    jdbc.update("DELETE FROM notification_triggers WHERE notification_trigger_id = ?", triggerId);
    jdbc.update("DELETE FROM assets WHERE asset_id = ?", endpointId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    triggerCacheService.invalidate();
  }

  @Test
  @DisplayName("the filter re-check reaches the endpoint, so the trigger records its event")
  void filteredLiveTriggerOnAnActiveTableStillMatches() {
    notificationEngineService.handleEvent(
        NotificationResourceCatalog.ASSET,
        endpointId,
        tenantId,
        NotificationTriggerEventType.CREATE,
        endpointName);

    // Ground truth through raw JDBC: a scoped read could not see the row either, so it would agree
    // with a broken re-check for the wrong reason.
    Long records =
        jdbc.queryForObject(
            "SELECT count(*) FROM notification_events WHERE notification_trigger_id = ?",
            Long.class,
            triggerId);
    assertEquals(
        1L,
        records,
        "the trigger must record its event; zero here means the filter re-check counted zero rows"
            + " because it ran with no tenant scope against a v2-activated table, which is silent"
            + " and drops every filtered LIVE notification");
  }

  @Test
  @DisplayName("a trigger of another tenant is not matched by this tenant's event")
  void aTriggerOfAnotherTenantDoesNotFire() {
    notificationEngineService.handleEvent(
        NotificationResourceCatalog.ASSET,
        endpointId,
        UUID.randomUUID().toString(),
        NotificationTriggerEventType.CREATE,
        endpointName);

    Long records =
        jdbc.queryForObject(
            "SELECT count(*) FROM notification_events WHERE notification_trigger_id = ?",
            Long.class,
            triggerId);
    assertEquals(
        0L, records, "the event belongs to another tenant and must not reach this trigger");
  }
}
