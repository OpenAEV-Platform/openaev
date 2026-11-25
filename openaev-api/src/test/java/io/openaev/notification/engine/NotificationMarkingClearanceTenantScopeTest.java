package io.openaev.notification.engine;

import static io.openaev.database.audit.ModelBaseListener.DATA_PERSIST;
import static io.openaev.database.audit.ModelBaseListener.DATA_UPDATE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Finding;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.Tenant;
import io.openaev.helper.ObjectMapperHelper;
import io.openaev.service.stream.StreamVisibilityService;
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
 * A LIVE trigger with two recipients of different marking clearance, on a real tenant, with the
 * MARKING preview feature on and {@code assets} activated for marking: the one cleared for the
 * asset's marking gets the outbox row, the other does not, for both an ASSET event and a FINDING
 * event on an asset carrying that marking (a finding carries no marking of its own, see {@code
 * MarkingDerivedTables}).
 *
 * <p>Same shape as {@link NotificationLiveTriggerTenantScopeTest}: not {@code @Transactional} (
 * {@code TenantScopedTransaction.execute()}, which both {@code NotificationEngineService} and
 * {@code StreamVisibilityService} use, refuses to open inside an active transaction), raw JDBC
 * seeding, swept in {@code @AfterEach}.
 *
 * <p>The event's {@link io.openaev.service.stream.StreamVisibility} is resolved the same way the
 * production listener does, through the real {@code StreamVisibilityService} bean - the point of
 * this test is to exercise that resolution against a real database, not a mock.
 */
@TestPropertySource(properties = "openaev.marking.active-tables=assets")
@WithMockUser(isAdmin = true)
@DisplayName("a LIVE trigger narrows its recipients by marking clearance")
class NotificationMarkingClearanceTenantScopeTest extends IntegrationTest {

  @Autowired private NotificationEngineService notificationEngineService;
  @Autowired private NotificationTriggerCacheService triggerCacheService;
  @Autowired private StreamVisibilityService streamVisibilityService;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String assetId;
  private String assetName;
  private String findingId;
  private String markingId;
  private String capabilityGroupId;
  private String markingGroupId;
  private String roleId;
  private String clearedUserId;
  private String restrictedUserId;
  private String assetTriggerId;
  private String findingTriggerId;

  @BeforeEach
  void seedATenantAMarkedAssetAndTwoRecipientsOfDifferentClearance() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = UUID.randomUUID().toString();
    assetId = UUID.randomUUID().toString();
    assetName = "notif-marking-" + UUID.randomUUID();
    findingId = UUID.randomUUID().toString();
    markingId = UUID.randomUUID().toString();
    capabilityGroupId = UUID.randomUUID().toString();
    markingGroupId = UUID.randomUUID().toString();
    roleId = UUID.randomUUID().toString();

    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        tenantId,
        "notif-marking-" + tenantId);

    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64')",
        assetId,
        assetName,
        tenantId,
        assetName);

    jdbc.update(
        "INSERT INTO marking_definitions (marking_definition_id, marking_definition_type,"
            + " marking_definition_definition, marking_definition_color,"
            + " marking_definition_order, marking_definition_protected, tenant_id,"
            + " marking_definition_created_at, marking_definition_updated_at)"
            + " VALUES (?, 'TLP', 'RED', '#ff0000', 70, false, ?, now(), now())",
        markingId,
        tenantId);

    // Out-of-band write on purpose: the marking predicate guards writes too, so an ORM update
    // would be blocked for a caller not holding this very marking (see AssetMarkingIsolationTest).
    jdbc.update(
        "UPDATE assets SET marking_ids = ? WHERE asset_id = ?",
        (Object) new String[] {markingId},
        assetId);

    // A finding carries no marking of its own; it is shown or hidden through its linked asset.
    jdbc.update(
        "INSERT INTO findings (finding_id, finding_type, finding_value, finding_status, tenant_id,"
            + " finding_created_at, finding_updated_at)"
            + " VALUES (?, 'Text', 'finding on the marked asset', 'OPEN', ?, now(), now())",
        findingId,
        tenantId);
    jdbc.update(
        "INSERT INTO findings_assets (finding_id, asset_id) VALUES (?, ?)", findingId, assetId);

    // One role, one group, shared by both recipients: the only difference between them must be
    // their marking clearance, never their RBAC rights on ASSET / FINDING.
    jdbc.update(
        "INSERT INTO roles (role_id, role_name, role_created_at, role_updated_at, tenant_id)"
            + " VALUES (?, ?, now(), now(), ?)",
        roleId,
        "notif-marking-role",
        tenantId);
    jdbc.update(
        "INSERT INTO roles_capabilities (role_id, capability) VALUES (?, 'ACCESS_ASSETS'), (?,"
            + " 'ACCESS_FINDINGS')",
        roleId,
        roleId);
    jdbc.update(
        "INSERT INTO groups (group_id, group_name, group_default_user_assign,"
            + " group_auto_new_marking, tenant_id) VALUES (?, ?, false, false, ?)",
        capabilityGroupId,
        "notif-marking-capability",
        tenantId);
    jdbc.update(
        "INSERT INTO groups_roles (group_id, role_id) VALUES (?, ?)", capabilityGroupId, roleId);

    // A second group grants the marking; only the cleared user ends up in it.
    jdbc.update(
        "INSERT INTO groups (group_id, group_name, group_default_user_assign,"
            + " group_auto_new_marking, tenant_id) VALUES (?, ?, false, false, ?)",
        markingGroupId,
        "notif-marking-clearance",
        tenantId);
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id) VALUES (?, ?)",
        markingGroupId,
        markingId);

    clearedUserId = seedUser("cleared");
    restrictedUserId = seedUser("restricted");
    for (String userId : List.of(clearedUserId, restrictedUserId)) {
      jdbc.update(
          "INSERT INTO users_groups (group_id, user_id) VALUES (?, ?)", capabilityGroupId, userId);
      // Not tenantRepository.addUserToTenant(): that @Modifying query needs an already-open
      // transaction, which this test deliberately never has (see the class javadoc).
      jdbc.update(
          "INSERT INTO users_tenants (user_id, tenant_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
          userId,
          tenantId);
    }
    jdbc.update(
        "INSERT INTO users_groups (group_id, user_id) VALUES (?, ?)",
        markingGroupId,
        clearedUserId);
    clearanceCache.evictForUser(clearedUserId);
    clearanceCache.evictForUser(restrictedUserId);

    assetTriggerId = seedTrigger("ASSET", "[\"CREATE\",\"UPDATE\"]");
    findingTriggerId = seedTrigger("FINDING", "[\"CREATE\"]");

    triggerCacheService.invalidate();
  }

  @AfterEach
  void sweep() {
    jdbc.update(
        "DELETE FROM notification_triggers_users WHERE notification_trigger_id IN (?, ?)",
        assetTriggerId,
        findingTriggerId);
    jdbc.update(
        "DELETE FROM notification_events WHERE notification_trigger_id IN (?, ?)",
        assetTriggerId,
        findingTriggerId);
    jdbc.update(
        "DELETE FROM notification_triggers WHERE notification_trigger_id IN (?, ?)",
        assetTriggerId,
        findingTriggerId);
    jdbc.update("DELETE FROM findings_assets WHERE finding_id = ?", findingId);
    jdbc.update("DELETE FROM findings WHERE finding_id = ?", findingId);
    jdbc.update(
        "DELETE FROM users_groups WHERE group_id IN (?, ?)", capabilityGroupId, markingGroupId);
    jdbc.update("DELETE FROM groups_markings WHERE group_id = ?", markingGroupId);
    jdbc.update("DELETE FROM groups_roles WHERE group_id = ?", capabilityGroupId);
    jdbc.update("DELETE FROM roles_capabilities WHERE role_id = ?", roleId);
    jdbc.update("DELETE FROM roles WHERE role_id = ?", roleId);
    jdbc.update("DELETE FROM groups WHERE group_id IN (?, ?)", capabilityGroupId, markingGroupId);
    jdbc.update(
        "DELETE FROM users_tenants WHERE user_id IN (?, ?)", clearedUserId, restrictedUserId);
    jdbc.update("DELETE FROM users WHERE user_id IN (?, ?)", clearedUserId, restrictedUserId);
    jdbc.update("DELETE FROM assets WHERE asset_id = ?", assetId);
    jdbc.update("DELETE FROM marking_definitions WHERE marking_definition_id = ?", markingId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId);
    triggerCacheService.invalidate();
  }

  @Test
  @DisplayName("a marked asset CREATE reaches only the cleared recipient")
  void markedAssetCreateReachesOnlyTheClearedRecipient() {
    fireAssetEvent(DATA_PERSIST, NotificationTriggerEventType.CREATE);

    assertRecipients(assetTriggerId, assetId, true, false);
  }

  @Test
  @DisplayName("a marked asset UPDATE reaches only the cleared recipient")
  void markedAssetUpdateReachesOnlyTheClearedRecipient() {
    fireAssetEvent(DATA_UPDATE, NotificationTriggerEventType.UPDATE);

    assertRecipients(assetTriggerId, assetId, true, false);
  }

  @Test
  @DisplayName(
      "a finding CREATE on the marked asset reaches only the cleared recipient, though the"
          + " finding itself carries no marking")
  void findingOnMarkedAssetCreateReachesOnlyTheClearedRecipient() {
    Endpoint asset = new Endpoint();
    asset.setId(assetId);
    asset.setName(assetName);
    Finding finding = new Finding();
    finding.setId(findingId);
    finding.setAssets(List.<Asset>of(asset));
    BaseEvent event = new BaseEvent(DATA_PERSIST, finding, ObjectMapperHelper.openAEVJsonMapper());

    notificationEngineService.handleEvent(
        NotificationResourceCatalog.FINDING,
        findingId,
        tenantId,
        NotificationTriggerEventType.CREATE,
        "finding on the marked asset",
        () -> streamVisibilityService.visibilityOrUnresolved(event, tenantId));

    assertRecipients(findingTriggerId, findingId, true, false);
  }

  private void fireAssetEvent(String lifecycleEventType, NotificationTriggerEventType eventType) {
    Endpoint endpoint = new Endpoint();
    endpoint.setId(assetId);
    endpoint.setName(assetName);
    endpoint.setMarkingIds(new String[] {markingId});
    endpoint.setTenant(new Tenant(tenantId));
    BaseEvent event =
        new BaseEvent(lifecycleEventType, endpoint, ObjectMapperHelper.openAEVJsonMapper());

    notificationEngineService.handleEvent(
        NotificationResourceCatalog.ASSET,
        assetId,
        tenantId,
        eventType,
        assetName,
        () -> streamVisibilityService.visibilityOrUnresolved(event, tenantId));
  }

  private void assertRecipients(
      String triggerId, String resourceId, boolean clearedExpected, boolean restrictedExpected) {
    assertEquals(
        clearedExpected,
        hasEventFor(triggerId, resourceId, clearedUserId),
        "the cleared recipient's outbox row");
    assertEquals(
        restrictedExpected,
        hasEventFor(triggerId, resourceId, restrictedUserId),
        "the restricted recipient's outbox row");
  }

  private boolean hasEventFor(String triggerId, String resourceId, String userId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM notification_events"
                + " WHERE notification_trigger_id = ? AND notification_event_resource_id = ?"
                + " AND user_id = ?",
            Long.class,
            triggerId,
            resourceId,
            userId);
    return count != null && count > 0;
  }

  private String seedUser(String label) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO users (user_id, user_email, user_status, user_admin, user_created_at,"
            + " user_updated_at) VALUES (?, ?, 0, false, now(), now())",
        id,
        "notif-marking-%s-%s@unittests.invalid".formatted(label, id));
    return id;
  }

  private String seedTrigger(String resourceType, String eventTypesJson) {
    String triggerId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO notification_triggers (notification_trigger_id, notification_trigger_name,"
            + " notification_trigger_type, notification_trigger_enabled,"
            + " notification_trigger_resource_type, notification_trigger_event_types, tenant_id,"
            + " user_id, notification_trigger_created_at, notification_trigger_updated_at)"
            + " VALUES (?, ?, 'LIVE', true, ?, CAST(? AS jsonb), ?, ?, now(), now())",
        triggerId,
        "notif-marking-" + resourceType,
        resourceType,
        eventTypesJson,
        tenantId,
        testUserHolder.get().getId());
    for (String userId : List.of(clearedUserId, restrictedUserId)) {
      jdbc.update(
          "INSERT INTO notification_triggers_users (notification_trigger_id, user_id) VALUES (?,"
              + " ?)",
          triggerId,
          userId);
    }
    return triggerId;
  }
}
