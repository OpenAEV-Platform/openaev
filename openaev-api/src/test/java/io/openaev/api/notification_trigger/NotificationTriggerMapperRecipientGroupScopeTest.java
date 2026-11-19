package io.openaev.api.notification_trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Group;
import io.openaev.database.model.NotificationTrigger;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.NotificationTriggerRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.persistence.EntityManager;
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

/**
 * The mapper's own contract for the recipient groups: they resolve inside the tenant the trigger is
 * written into, not inside the request's read scope, and a platform group is not a recipient a
 * tenant names.
 *
 * <p>The read scope only differs from the write tenant when it holds more than one tenant, which
 * {@code NotificationTriggerApi} refuses upstream with a 400, so this exercises the mapper directly
 * under a two-tenant scope.
 */
@WithMockUser(isAdmin = true)
@DisplayName("recipient groups resolve in the write tenant, not across the whole read scope")
class NotificationTriggerMapperRecipientGroupScopeTest extends ProductionTablesIntegrationTest {

  @Autowired private NotificationTriggerMapper mapper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;
  @Autowired private NotificationTriggerRepository notificationTriggerRepository;
  @Autowired private EntityManager entityManager;

  private JdbcTemplate jdbc;
  private final List<String> seededTenants = new ArrayList<>();
  private final List<String> seededGroups = new ArrayList<>();
  private final List<String> seededTriggers = new ArrayList<>();
  private String writeTenant;
  private String otherTenant;
  private String groupOfWriteTenant;
  private String groupOfOtherTenant;
  private String platformGroup;

  @BeforeEach
  void seedAGroupInEachOfTwoInScopeTenantsAndOnePlatformGroup() {
    jdbc = new JdbcTemplate(dataSource);
    writeTenant = seedTenant();
    otherTenant = seedTenant();
    groupOfWriteTenant = seedGroup(writeTenant);
    groupOfOtherTenant = seedGroup(otherTenant);
    platformGroup = seedGroup(null);
  }

  @AfterEach
  void sweep() {
    seededTriggers.forEach(
        id -> {
          jdbc.update(
              "DELETE FROM notification_triggers_groups WHERE notification_trigger_id = ?", id);
          jdbc.update("DELETE FROM notification_triggers WHERE notification_trigger_id = ?", id);
        });
    seededGroups.forEach(id -> jdbc.update("DELETE FROM groups WHERE group_id = ?", id));
    seededTenants.forEach(id -> jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", id));
    seededTriggers.clear();
    seededGroups.clear();
    seededTenants.clear();
  }

  @Test
  @DisplayName("given_aGroupOfAnotherInScopeTenant_should_reportItAsNotFound")
  void given_aGroupOfAnotherInScopeTenant_should_reportItAsNotFound() {
    // Act + Assert
    tenantTx.execute(
        bothTenants(),
        () ->
            assertThrows(
                ElementNotFoundException.class,
                () -> mapper.toNotificationTrigger(writeTenant, recipients(groupOfOtherTenant))));
  }

  @Test
  @DisplayName("given_aPlatformGroup_should_reportItAsNotFound")
  void given_aPlatformGroup_should_reportItAsNotFound() {
    // Act + Assert
    tenantTx.execute(
        bothTenants(),
        () ->
            assertThrows(
                ElementNotFoundException.class,
                () -> mapper.toNotificationTrigger(writeTenant, recipients(platformGroup))));
  }

  @Test
  @DisplayName("given_aGroupOfTheWriteTenant_should_resolveItUnderTheSameScope")
  void given_aGroupOfTheWriteTenant_should_resolveItUnderTheSameScope() {
    // Act
    NotificationTrigger trigger =
        tenantTx.execute(
            bothTenants(),
            () -> mapper.toNotificationTrigger(writeTenant, recipients(groupOfWriteTenant)));

    // Assert: without this the refusals above could come from the scope seeing nothing at all
    assertEquals(
        List.of(groupOfWriteTenant),
        trigger.getRecipientGroups().stream().map(Group::getId).toList());
  }

  @Test
  @DisplayName("given_anAlreadyLinkedPlatformGroup_shouldUpdate_keepIt")
  void given_anAlreadyLinkedPlatformGroup_shouldUpdate_keepIt() {
    // Arrange: a trigger that already carries the platform group as a recipient, grandfathered
    // from before groups activated under this guard.
    String triggerId =
        tenantTx.execute(TxCtx.forTenant(writeTenant), () -> persistTrigger(platformGroup));

    // Act
    NotificationTrigger updated =
        tenantTx.execute(
            bothTenants(),
            () ->
                mapper.toNotificationTriggerForUpdate(
                    writeTenant, triggerId, recipients(platformGroup)));

    // Assert: no refusal, the platform group is kept
    assertEquals(
        List.of(platformGroup), updated.getRecipientGroups().stream().map(Group::getId).toList());
  }

  @Test
  @DisplayName("given_aNewPlatformGroupNotPreviouslyLinked_shouldUpdate_stillRefuseIt")
  void given_aNewPlatformGroupNotPreviouslyLinked_shouldUpdate_stillRefuseIt() {
    // Arrange: a trigger with no recipient group yet
    String triggerId = tenantTx.execute(TxCtx.forTenant(writeTenant), () -> persistTrigger());

    // Act + Assert: the platform group was never linked, so it is refused as any new one would be
    tenantTx.execute(
        bothTenants(),
        () ->
            assertThrows(
                ElementNotFoundException.class,
                () ->
                    mapper.toNotificationTriggerForUpdate(
                        writeTenant, triggerId, recipients(platformGroup))));
  }

  private String persistTrigger(String... recipientGroupIds) {
    NotificationTrigger trigger = new NotificationTrigger();
    trigger.setName("mapper-recipients-existing");
    trigger.setType(NotificationTriggerType.LIVE);
    trigger.setTenant(entityManager.getReference(Tenant.class, writeTenant));
    trigger.setOwner(entityManager.getReference(User.class, User.ADMIN_UUID));
    for (String groupId : recipientGroupIds) {
      trigger.getRecipientGroups().add(entityManager.getReference(Group.class, groupId));
    }
    String id = notificationTriggerRepository.save(trigger).getId();
    seededTriggers.add(id);
    return id;
  }

  private TxCtx bothTenants() {
    return TxCtx.forTenants(List.of(writeTenant, otherTenant));
  }

  private NotificationTriggerInput recipients(String groupId) {
    return NotificationTriggerInput.builder()
        .name("mapper-recipients")
        .type(NotificationTriggerType.LIVE)
        .recipientGroupIds(List.of(groupId))
        .build();
  }

  private String seedTenant() {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        "mapper-recipients-" + id);
    seededTenants.add(id);
    return id;
  }

  private String seedGroup(String tenantId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO groups (group_id, group_name, group_default_user_assign,"
            + " group_auto_new_marking, tenant_id) VALUES (?, ?, false, false, ?)",
        id,
        "mapper-recipient-group-" + id,
        tenantId);
    seededGroups.add(id);
    return id;
  }
}
