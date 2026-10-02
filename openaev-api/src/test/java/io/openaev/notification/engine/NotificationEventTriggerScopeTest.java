package io.openaev.notification.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationEventRecord;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.NotificationEventRecordRepository;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 * Pins why the digest window read must keep its {@code allTenants()} scope, and what narrowing it
 * would actually cost.
 *
 * <p>{@code NotificationEventRecordRepository#findAllByTriggerIdsAndWindow} filters on {@code
 * e.trigger.id}, which Hibernate resolves on the foreign key column of {@code notification_events}
 * itself - it does NOT join {@code notification_triggers}. So a scope that excludes the trigger's
 * tenant still RETURNS the event (the event row's own {@code tenant_id} is what the inspector
 * checks) and the damage lands one step later: {@code NotificationTriggerLoader} reads {@code
 * event.getTrigger().getName()} on the result, and {@code trigger} is a required {@code @ManyToOne}
 * with no {@code @NotFound}, so Hibernate throws {@link EntityNotFoundException} rather than
 * returning null. A narrowed digest read fails loudly mid-loop instead of quietly skipping rows.
 *
 * <p>Both behaviours were observed before being written here, not inferred from the mapping.
 *
 * <p>The fixture is a deliberately cross-tenant edge (an event of tenant A referencing tenant B's
 * trigger) that production never writes - {@code NotificationEngineService.recordEvents} stamps the
 * event with the trigger's own tenant - so that the degradation can be exercised at all.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction.
 */
@TestPropertySource(
    properties = "openaev.tenant.active-tables=notification_triggers,notification_events")
@WithMockUser(isAdmin = true)
@DisplayName(
    "an event whose trigger is out of scope breaks the digest read, so it stays allTenants")
class NotificationEventTriggerScopeTest extends IntegrationTest {

  @Autowired private NotificationEventRecordRepository repository;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;
  @Autowired private TestUserHolder testUserHolder;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private String tenantA;
  private String triggerInB;
  private String eventInA;

  @BeforeEach
  void seedAnEventOfTenantAPointingAtTenantBsTrigger() {
    jdbc = new JdbcTemplate(dataSource);
    tenantA = seedTenant();
    String tenantB = seedTenant();
    triggerInB = seedTrigger(tenantB);
    eventInA = seedEvent(tenantA, triggerInB);
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
  @DisplayName("under allTenants() the window read returns the event and its trigger resolves")
  void allTenantsSeesTheEventAndItsTrigger() {
    String name =
        tenantTx.execute(
            TxCtx.allTenants(),
            () -> {
              List<NotificationEventRecord> events = window();
              assertEquals(1, events.size(), "the digest window must see the event");
              return events.get(0).getTrigger().getName();
            });
    assertTrue(name.startsWith("scope-trigger-"), "the trigger must resolve, got " + name);
  }

  @Test
  @DisplayName(
      "a scope excluding the trigger's tenant still returns the event: the filter is on the event")
  void aNarrowerScopeStillReturnsTheEventBecauseTheQueryDoesNotJoinTheTrigger() {
    tenantTx.execute(
        TxCtx.forTenant(tenantA),
        () -> {
          Optional<NotificationEventRecord> byId = repository.findById(eventInA);
          assertTrue(byId.isPresent(), "the event belongs to tenant A and must be visible under A");
          assertEquals(
              1,
              window().size(),
              "e.trigger.id reads the foreign key column of notification_events, so the trigger's"
                  + " own tenant never filters this query. Expecting it to is the mistake this test"
                  + " exists to prevent");
        });
  }

  @Test
  @DisplayName("but the lazy trigger association then throws instead of degrading to null")
  void aNarrowerScopeMakesTheLazyAssociationThrow() {
    tenantTx.execute(
        TxCtx.forTenant(tenantA),
        () -> {
          NotificationEventRecord event = repository.findById(eventInA).orElseThrow();
          assertThrows(
              EntityNotFoundException.class,
              () -> event.getTrigger().getName(),
              "a required @ManyToOne with no @NotFound throws when its target fails"
                  + " can_access_tenant; it does not come back null, so narrowing the digest read"
                  + " would abort the loop rather than skip the row");
        });
  }

  private List<NotificationEventRecord> window() {
    Instant now = Instant.now();
    return repository.findAllByTriggerIdsAndWindow(
        List.of(triggerInB), now.minus(Duration.ofHours(1)), now.plus(Duration.ofHours(1)));
  }

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "scope-" + id);
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
        "scope-trigger-" + id,
        testUserHolder.get().getId(),
        tenantId);
    return id;
  }

  /**
   * Seeds through the repository under a scope, exactly like {@code
   * NotificationEngineService.recordEvents}, rather than by raw SQL. {@code
   * notification_event_created_at} is a {@code timestamp without time zone} and the window query
   * binds its {@code Instant} bounds through Hibernate's own conversion; a raw-SQL seed writes a
   * different wall clock and the window then misses it by the JVM offset (measured: a row written
   * by the database's {@code now()} is matched by a +/-24h window and not by a +/-1h one, on a JVM
   * in {@code Europe/Paris}). Writing it the way production writes it removes the question.
   */
  private String seedEvent(String tenantId, String triggerId) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          NotificationEventRecord record = new NotificationEventRecord();
          NotificationTrigger triggerReference = new NotificationTrigger();
          triggerReference.setId(triggerId);
          record.setTrigger(triggerReference);
          User userReference = new User();
          userReference.setId(testUserHolder.get().getId());
          record.setUser(userReference);
          record.setEventType(NotificationTriggerEventType.CREATE);
          record.setTenant(new Tenant(tenantId));
          return repository.save(record).getId();
        });
  }
}
