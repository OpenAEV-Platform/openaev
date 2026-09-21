package io.openaev.api.notification_trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerPeriod;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.rest.exception.ElementNotFoundException;
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
 * The mapper's own contract: child triggers resolve inside the tenant the row is written into, not
 * inside the request's read scope.
 *
 * <p>The two only differ when the read scope holds more than one tenant, which {@code
 * NotificationTriggerApi} refuses upstream with a 400 - so no HTTP test can tell a write-tenant
 * lookup apart from a bare {@code findAllById}. This exercises the mapper directly under a
 * two-tenant scope so the constraint is covered by something that can actually fail if it is
 * removed.
 *
 * <p>Notifier ids are left empty on purpose: the mapper short-circuits an empty id list, so this
 * test says nothing about notifier resolution, which is still v1 and is corrected by #7864.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=notification_triggers")
@WithMockUser(isAdmin = true)
@DisplayName("child triggers resolve in the write tenant, not across the whole read scope")
class NotificationTriggerMapperChildScopeTest extends IntegrationTest {

  @Autowired private NotificationTriggerMapper mapper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;
  @Autowired private TestUserHolder testUserHolder;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private String writeTenant;
  private String otherTenant;
  private String childInWriteTenant;
  private String childInOtherTenant;

  @BeforeEach
  void seedAChildInEachOfTwoInScopeTenants() {
    jdbc = new JdbcTemplate(dataSource);
    writeTenant = seedTenant();
    otherTenant = seedTenant();
    childInWriteTenant = seedTrigger(writeTenant);
    childInOtherTenant = seedTrigger(otherTenant);
  }

  @AfterEach
  void sweep() {
    for (String tenantId : seededTenants) {
      jdbc.update("DELETE FROM notification_triggers WHERE tenant_id = ?", tenantId);
      jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    }
    seededTenants.clear();
  }

  @Test
  @DisplayName("a child of the other in-scope tenant is reported as not found")
  void aChildOfAnotherInScopeTenantDoesNotResolve() {
    tenantTx.execute(
        bothTenants(),
        () ->
            assertThrows(
                ElementNotFoundException.class,
                () -> mapper.toNotificationTrigger(writeTenant, digestWith(childInOtherTenant)),
                "the read scope holds both tenants, so only the write tenant may narrow this"
                    + " lookup; resolving it would compose a digest over another tenant's outbox"));
  }

  @Test
  @DisplayName("non-vacuity: a child of the write tenant resolves under the same scope")
  void aChildOfTheWriteTenantResolves() {
    NotificationTrigger trigger =
        tenantTx.execute(
            bothTenants(),
            () -> mapper.toNotificationTrigger(writeTenant, digestWith(childInWriteTenant)));
    assertEquals(
        List.of(childInWriteTenant),
        trigger.getChildTriggers().stream().map(NotificationTrigger::getId).toList(),
        "without this the refusal above could come from the scope seeing nothing at all");
  }

  private TxCtx bothTenants() {
    return TxCtx.forTenants(List.of(writeTenant, otherTenant));
  }

  private NotificationTriggerInput digestWith(String childId) {
    return NotificationTriggerInput.builder()
        .name("mapper-digest")
        .type(NotificationTriggerType.DIGEST)
        .period(NotificationTriggerPeriod.DAY)
        .triggerTime("09:00")
        .childTriggerIds(List.of(childId))
        .build();
  }

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "mapper-scope-" + id);
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
        "mapper-child-" + id,
        testUserHolder.get().getId(),
        tenantId);
    return id;
  }
}
