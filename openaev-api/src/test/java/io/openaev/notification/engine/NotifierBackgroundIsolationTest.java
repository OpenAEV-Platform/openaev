package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
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
 * The notification engine resolves every enabled trigger's delivery channels through {@code
 * NotificationTrigger#notifiers}, a lazy {@code @ManyToMany} onto {@code notifiers}, inside {@link
 * NotificationTriggerLoader}. That loader carries no tenant scope: it is reached from the trigger
 * cache refresh (async engine thread) and from the digest job, and it declares its cross-tenant
 * intent the v1 way, with {@code disableFilter("tenantFilter")} - inert under v2.
 *
 * <p>Once {@code notifiers} is tenant-active the inspector fails closed on that collection query,
 * every resolved trigger comes back with an EMPTY notifier list, and {@code
 * NotificationDispatchService.dispatch} returns without delivering anything. Nothing throws and
 * nothing is logged, so the platform simply stops notifying.
 *
 * <p>This asserts on the notifiers that actually reach the dispatch pipeline. The outbox rows
 * {@code NotificationLiveTriggerTenantScopeTest} checks are written BEFORE dispatch, so they stay
 * green straight through this break and cannot stand in for it.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction,
 * and the production path has none. Seeding auto-commits through {@link JdbcTemplate} and is swept.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=notifiers")
@WithMockUser(isAdmin = true)
@DisplayName("the engine still resolves each trigger's notifiers once notifiers is tenant-active")
class NotifierBackgroundIsolationTest extends IntegrationTest {

  @Autowired private NotificationTriggerLoader triggerLoader;
  @Autowired private DataSource dataSource;
  @Autowired private TestUserHolder testUserHolder;

  private JdbcTemplate jdbc;
  private String tenantA;
  private String tenantB;
  private String notifierA;
  private String notifierB;
  private String triggerA;
  private String triggerB;

  @BeforeEach
  void seedTwoTenantsEachWithATriggerAndItsNotifier() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant("notifier-bg-a");
    tenantB = seedTenant("notifier-bg-b");
    notifierA = seedNotifier(tenantA, "notifier-bg-a");
    notifierB = seedNotifier(tenantB, "notifier-bg-b");
    triggerA = seedLiveTrigger(tenantA, "trigger-bg-a", notifierA);
    triggerB = seedLiveTrigger(tenantB, "trigger-bg-b", notifierB);
  }

  @AfterEach
  void sweep() {
    for (String triggerId : List.of(triggerA, triggerB)) {
      jdbc.update(
          "DELETE FROM notification_triggers_notifiers WHERE notification_trigger_id = ?",
          triggerId);
      jdbc.update("DELETE FROM notification_triggers WHERE notification_trigger_id = ?", triggerId);
    }
    jdbc.update("DELETE FROM notifiers WHERE tenant_id IN (?, ?)", tenantA, tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id IN (?, ?)", tenantA, tenantB);
  }

  @Test
  @DisplayName("each loaded trigger still carries its own tenant's notifier")
  void everyTriggerResolvesItsNotifier() {
    List<ResolvedNotificationTrigger> triggers =
        triggerLoader.loadEnabledTriggers(NotificationTriggerType.LIVE);

    assertEquals(
        List.of(notifierA),
        notifierIdsOf(triggers, triggerA),
        "tenant A's trigger must resolve its own notifier; an empty list here means the lazy"
            + " notifiers association was read with no tenant scope against a v2-activated table,"
            + " which silently stops every notification from being delivered");
    assertEquals(
        List.of(notifierB),
        notifierIdsOf(triggers, triggerB),
        "tenant B's trigger must resolve its own notifier");
  }

  @Test
  @DisplayName("a trigger never resolves another tenant's notifier")
  void noTriggerResolvesAnotherTenantsNotifier() {
    List<ResolvedNotificationTrigger> triggers =
        triggerLoader.loadEnabledTriggers(NotificationTriggerType.LIVE);

    List<String> resolvedForA = notifierIdsOf(triggers, triggerA);
    List<String> resolvedForB = notifierIdsOf(triggers, triggerB);

    // Non-emptiness first: with both lists empty the cross-tenant assertions below hold for the
    // wrong reason, which is precisely how a fail-closed read passes for isolation it never did.
    assertFalse(resolvedForA.isEmpty(), "tenant A's trigger resolved no notifier at all");
    assertFalse(resolvedForB.isEmpty(), "tenant B's trigger resolved no notifier at all");
    assertTrue(
        resolvedForA.stream().noneMatch(notifierB::equals),
        "tenant B's notifier must not reach tenant A's trigger");
    assertTrue(
        resolvedForB.stream().noneMatch(notifierA::equals),
        "tenant A's notifier must not reach tenant B's trigger");
  }

  private List<String> notifierIdsOf(List<ResolvedNotificationTrigger> triggers, String triggerId) {
    return triggers.stream()
        .filter(trigger -> triggerId.equals(trigger.id()))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("the loader did not return the seeded trigger " + triggerId))
        .notifiers()
        .stream()
        .map(ResolvedNotifier::id)
        .toList();
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name + "-" + id);
    return id;
  }

  private String seedNotifier(String tenantId, String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO notifiers (notifier_id, notifier_name, notifier_type,"
            + " notifier_configuration, notifier_built_in, tenant_id,"
            + " notifier_created_at, notifier_updated_at)"
            + " VALUES (?, ?, 'WEBHOOK', CAST(? AS jsonb), false, ?, now(), now())",
        id,
        name,
        "{\"url\":\"https://example.org/" + name + "\"}",
        tenantId);
    return id;
  }

  private String seedLiveTrigger(String tenantId, String name, String notifierId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO notification_triggers (notification_trigger_id, notification_trigger_name,"
            + " notification_trigger_type, notification_trigger_enabled,"
            + " notification_trigger_resource_type, notification_trigger_event_types, tenant_id,"
            + " user_id, notification_trigger_created_at, notification_trigger_updated_at)"
            + " VALUES (?, ?, 'LIVE', true, 'SCENARIO', CAST(? AS jsonb), ?, ?, now(), now())",
        id,
        name,
        "[\"CREATE\"]",
        tenantId,
        testUserHolder.get().getId());
    jdbc.update(
        "INSERT INTO notification_triggers_notifiers (notification_trigger_id, notifier_id)"
            + " VALUES (?, ?)",
        id,
        notifierId);
    return id;
  }
}
