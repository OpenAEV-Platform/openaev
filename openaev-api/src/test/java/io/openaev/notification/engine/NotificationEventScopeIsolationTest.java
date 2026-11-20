package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.NotificationEventRecordRepository;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Duration;
import java.time.Instant;
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
 * Isolation of {@code notification_events} on its own rows, at the repository.
 *
 * <p>The table is the notification engine's outbox: no endpoint reads it and no service reads a row
 * by id, so the case is a read of the repository under one tenant's scope. Each tenant owns one
 * trigger and one event; the window read names both triggers, and it filters on the foreign key
 * column of {@code notification_events} without joining {@code notification_triggers}, so the other
 * tenant's event is refused by the scope of {@code notification_events} alone.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction.
 * The seed is committed and swept after each test.
 */
@CoversTenantIsolation("notification_events")
@WithMockUser(isAdmin = true)
@DisplayName("notification_events refuses another tenant's event under one tenant's scope")
class NotificationEventScopeIsolationTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private NotificationEventRecordRepository repository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private final List<String> seededTenants = new ArrayList<>();
  private JdbcTemplate jdbc;
  private String tenantA;
  private String triggerA;
  private String triggerB;
  private String ownEvent;
  private String foreignEvent;

  @BeforeEach
  void seedOneTriggerAndOneEventPerTenant() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant();
    String tenantB = seedTenant();
    triggerA = seedTrigger(tenantA);
    triggerB = seedTrigger(tenantB);
    ownEvent = seedEvent(tenantA, triggerA);
    foreignEvent = seedEvent(tenantB, triggerB);
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
  @DisplayName("given an event id, should read its own event and not the other tenant's")
  void given_eventId_should_readOwnAndNotOtherTenants() {
    // Act
    boolean[] found =
        tenantTx.execute(
            TxCtx.forTenant(tenantA),
            () ->
                new boolean[] {
                  repository.findById(ownEvent).isPresent(),
                  repository.findById(foreignEvent).isPresent()
                });

    // Assert
    assertTrue(found[0], "A's event must be read under A's scope");
    assertFalse(found[1], "B's event must not be read under A's scope");
  }

  @Test
  @DisplayName("given a window naming both triggers, should return its own event only")
  void given_windowOnBothTriggers_should_returnOwnEventOnly() {
    // Act
    List<String> events =
        tenantTx.execute(
            TxCtx.forTenant(tenantA),
            () -> {
              Instant now = Instant.now();
              return repository
                  .findAllByTriggerIdsAndWindow(
                      List.of(triggerA, triggerB),
                      now.minus(Duration.ofHours(1)),
                      now.plus(Duration.ofHours(1)))
                  .stream()
                  .map(NotificationEventRecord::getId)
                  .toList();
            });

    // Assert
    assertEquals(List.of(ownEvent), events, "only A's event is in A's window");
  }

  // -- FIXTURES --

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "notification-event-iso-" + id);
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
        "notification-event-iso-" + id,
        testUserHolder.get().getId(),
        tenantId);
    return id;
  }

  // Written the way NotificationEngineService.recordEvents writes it, through the repository in
  // the event's own tenant scope: the window query binds Instant bounds through Hibernate's own
  // conversion, which a raw SQL timestamp would not match.
  private String seedEvent(String tenantId, String triggerId) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          NotificationEventRecord event = new NotificationEventRecord();
          NotificationTrigger trigger = new NotificationTrigger();
          trigger.setId(triggerId);
          event.setTrigger(trigger);
          User user = new User();
          user.setId(testUserHolder.get().getId());
          event.setUser(user);
          event.setEventType(NotificationTriggerEventType.CREATE);
          event.setTenant(new Tenant(tenantId));
          return repository.save(event).getId();
        });
  }
}
