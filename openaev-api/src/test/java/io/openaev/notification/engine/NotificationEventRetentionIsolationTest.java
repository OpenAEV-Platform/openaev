package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.utils.mockUser.TestUserHolder;
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
import org.springframework.test.context.TestPropertySource;

/**
 * The outbox purge is a bulk {@code DELETE ... WHERE created_at < ?} spanning every tenant, run
 * from a Quartz job. {@code TenantStatementInspector} rewrites DELETE the same way it rewrites
 * SELECT, so once {@code notification_events} is active an unscoped purge matches no row, reports
 * success and the outbox grows without bound. Nothing else in the codebase asserts on this service,
 * so that failure mode is invisible outside a test like this one.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction
 * and the production path has none. Seeding auto-commits through {@link JdbcTemplate} and is swept.
 */
@TestPropertySource(
    properties = "openaev.tenant.active-tables=notification_triggers,notification_events")
@WithMockUser(isAdmin = true)
@DisplayName("the notification outbox purge deletes old rows in every tenant")
class NotificationEventRetentionIsolationTest extends IntegrationTest {

  @Autowired private NotificationEventRetentionService retentionService;
  @Autowired private DataSource dataSource;
  @Autowired private TestUserHolder testUserHolder;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private String tenantA;
  private String tenantB;
  private String oldEventA;
  private String recentEventA;
  private String oldEventB;
  private String recentEventB;

  @BeforeEach
  void seedTwoTenantsWithAnOldAndARecentEvent() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("retention-a");
    tenantB = seedTenant("retention-b");
    String triggerA = seedTrigger(tenantA);
    String triggerB = seedTrigger(tenantB);
    oldEventA = seedEvent(tenantA, triggerA, "now() - interval '100 days'");
    recentEventA = seedEvent(tenantA, triggerA, "now()");
    oldEventB = seedEvent(tenantB, triggerB, "now() - interval '100 days'");
    recentEventB = seedEvent(tenantB, triggerB, "now()");
  }

  @AfterEach
  void sweep() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM notification_events WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM notification_triggers WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Test
  @DisplayName("every tenant's old events are purged and its recent ones survive")
  void purgeReachesEveryTenantAndSparesRecentRows() {
    // Non-vacuity guard: without it, "no old row for tenant A" is also true when the purge deleted
    // nothing and A never had one. Both tenants must start with exactly one old and one recent row.
    assertEquals(1L, rawCount(oldEventA), "tenant A must start with its old event");
    assertEquals(1L, rawCount(oldEventB), "tenant B must start with its old event");
    assertEquals(1L, rawCount(recentEventA), "tenant A must start with its recent event");
    assertEquals(1L, rawCount(recentEventB), "tenant B must start with its recent event");

    retentionService.deleteOldEvents();

    assertEquals(
        0L,
        rawCount(oldEventA),
        "tenant A's old event must be purged; still present means the DELETE was rewritten against"
            + " an empty scope, matched nothing and reported success");
    assertEquals(
        0L,
        rawCount(oldEventB),
        "tenant B's old event must be purged too: a purge that only reaches one tenant is a purge"
            + " that carries a single-tenant scope instead of every tenant's");
    assertEquals(
        1L,
        rawCount(recentEventA),
        "tenant A's recent event is inside the window and must survive");
    assertEquals(
        1L,
        rawCount(recentEventB),
        "tenant B's recent event is inside the window and must survive");
  }

  private long rawCount(String eventId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM notification_events WHERE notification_event_id = ?",
        Long.class,
        eventId);
  }

  private String seedTenant(String prefix) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        prefix + "-" + id);
    seededTenants.add(id);
    return id;
  }

  private String seedTrigger(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO notification_triggers (notification_trigger_id, notification_trigger_name,"
            + " notification_trigger_type, notification_trigger_enabled, user_id, tenant_id)"
            + " VALUES (?, ?, 'LIVE', true, ?, ?)",
        id,
        "retention-trigger-" + id,
        testUserHolder.get().getId(),
        tenantId);
    return id;
  }

  private String seedEvent(String tenantId, String triggerId, String createdAtExpression) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO notification_events (notification_event_id, notification_trigger_id, user_id,"
            + " notification_event_type, notification_event_created_at, tenant_id)"
            + " VALUES (?, ?, ?, 'CREATE', "
            + createdAtExpression
            + ", ?)",
        id,
        triggerId,
        testUserHolder.get().getId(),
        tenantId);
    return id;
  }
}
