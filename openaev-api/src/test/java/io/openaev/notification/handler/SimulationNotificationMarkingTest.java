package io.openaev.notification.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.openaev.IntegrationTest;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.AssetCriticality;
import io.openaev.database.model.Capability;
import io.openaev.database.model.MarkingDefinition;
import io.openaev.database.model.ResourceType;
import io.openaev.notification.engine.NotificationTriggerCacheService;
import io.openaev.notification.model.NotificationEvent;
import io.openaev.notification.model.NotificationEventType;
import io.openaev.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * {@code CRITICAL_ASSET_REACHED} must not tell a recipient that a critical asset was reached when
 * that asset is marked above the recipient's clearance.
 *
 * <p>Today the detection ({@code SimulationNotificationEventHandler.detect}) runs under the system
 * clearance, so it sees the criticality of every asset, and the engine is called with {@code
 * StreamVisibility.UNFILTERED}: every recipient who can read the simulation is notified. On the
 * attack-path page, the same recipient gets {@code criticality = null} for that asset, because
 * {@code applyEndpointCriticality} reads the marked {@code assets} table. The notification reveals
 * what the page hides.
 *
 * <p>The recipient is the mock user: READ on simulations ({@code ACCESS_ASSESSMENT}), not an admin
 * (an admin holds the whole tenant scale and would make the negative case vacuous), cleared for a
 * TLP:AMBER-like marking only. The positive case is the control: same seed with the critical asset
 * marked AMBER, so a red result in the negative case cannot come from a broken seed.
 *
 * <p>Not {@code @Transactional}: the handler opens its own scoped transaction and the primitive
 * refuses to open inside an active one. Seeding auto-commits through {@link JdbcTemplate}, which
 * also bypasses the statement inspector, and is swept.
 */
@TestPropertySource(properties = "openaev.enabled-dev-features=MARKING")
@WithMockUser(withCapabilities = {Capability.ACCESS_ASSESSMENT})
@DisplayName("CRITICAL_ASSET_REACHED and the recipient's marking clearance")
class SimulationNotificationMarkingTest extends IntegrationTest {

  @Autowired private SimulationNotificationEventHandler handler;
  @Autowired private NotificationTriggerCacheService triggerCacheService;
  @Autowired private MarkingClearanceCacheManager clearanceCache;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantId;
  private String userId;
  private String amberId;
  private String redId;
  private String simulationId;
  private String sourceAssetId;
  private String criticalAssetId;
  private String triggerId;

  @BeforeEach
  void seedASimulationARecipientAndItsTrigger() {
    jdbc = new JdbcTemplate(dataSource);
    tenantId = TenantContext.getCurrentTenant();
    userId = testUserHolder.get().getId();
    jdbc.update(
        "INSERT INTO users_tenants (user_id, tenant_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
        userId,
        tenantId);
    tenantMembershipCacheManager.evict(userId, tenantId);

    // Orders above the seeded 10..50 band so a fixture never ties with a default.
    amberId = insertMarking(60);
    redId = insertMarking(70);
    // The recipient holds AMBER (and, by ordinality, everything below it), never RED.
    jdbc.update(
        "INSERT INTO groups_markings (group_id, marking_id)"
            + " SELECT group_id, ? FROM users_groups WHERE user_id = ?",
        amberId,
        userId);
    clearanceCache.evictForUser(userId);

    simulationId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO exercises (exercise_id, exercise_name, exercise_status, tenant_id)"
            + " VALUES (?, 'critical asset marking', 'FINISHED', ?)",
        simulationId,
        tenantId);
    sourceAssetId = insertAsset(null, null);

    triggerId = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO notification_triggers (notification_trigger_id, notification_trigger_name,"
            + " notification_trigger_type, notification_trigger_enabled,"
            + " notification_trigger_resource_type, notification_trigger_event_types,"
            + " notification_trigger_filters, tenant_id, user_id,"
            + " notification_trigger_created_at, notification_trigger_updated_at)"
            + " VALUES (?, ?, 'LIVE', true, 'SIMULATION', CAST(? AS jsonb), CAST(? AS jsonb), ?,"
            + " ?, now(), now())",
        triggerId,
        "critical-asset-marking-trigger",
        "[\"CRITICAL_ASSET_REACHED\"]",
        "{\"mode\":\"and\",\"filters\":[]}",
        tenantId,
        userId);
    triggerCacheService.invalidate();
  }

  @AfterEach
  void sweep() {
    jdbc.update("DELETE FROM notification_events WHERE notification_trigger_id = ?", triggerId);
    jdbc.update("DELETE FROM notification_triggers WHERE notification_trigger_id = ?", triggerId);
    jdbc.update(
        "DELETE FROM attackpath_execution WHERE attackpath_execution_simulation_id = ?",
        simulationId);
    jdbc.update("DELETE FROM assets WHERE asset_id IN (?, ?)", sourceAssetId, criticalAssetId);
    jdbc.update("DELETE FROM exercises WHERE exercise_id = ?", simulationId);
    jdbc.update("DELETE FROM groups_markings WHERE marking_id IN (?, ?)", amberId, redId);
    jdbc.update(
        "DELETE FROM marking_definitions WHERE marking_definition_id IN (?, ?)", amberId, redId);
    clearanceCache.evictForUser(userId);
    triggerCacheService.invalidate();
  }

  @Nested
  @DisplayName("when the attack path reaches a critical asset")
  class CriticalAssetReached {

    @Test
    @DisplayName(
        "given the critical asset is marked above the recipient's clearance, should not notify"
            + " the recipient")
    void given_criticalAssetAboveClearance_should_notNotifyTheRecipient() {
      // -- ARRANGE --
      criticalAssetId = insertAsset(AssetCriticality.VERY_HIGH, redId);
      insertAttackStep(sourceAssetId, criticalAssetId);

      // -- ACT --
      handler.handle(simulationCompleted());

      // -- ASSERT --
      assertEquals(
          0L,
          recordedFor(userId),
          "the recipient is not cleared for the only critical asset reached: being told that a"
              + " critical asset was reached discloses the criticality the attack-path page hides"
              + " from them");
    }

    @Test
    @DisplayName(
        "given the critical asset is within the recipient's clearance, should notify the"
            + " recipient")
    void given_criticalAssetWithinClearance_should_notifyTheRecipient() {
      // -- ARRANGE --
      criticalAssetId = insertAsset(AssetCriticality.VERY_HIGH, amberId);
      insertAttackStep(sourceAssetId, criticalAssetId);

      // -- ACT --
      handler.handle(simulationCompleted());

      // -- ASSERT --
      assertEquals(
          1L,
          recordedFor(userId),
          "control: the recipient may see the critical asset, so the notification is due; zero"
              + " here means the seed is broken, not that the marking check works");
    }
  }

  private NotificationEvent simulationCompleted() {
    return NotificationEvent.builder()
        .resourceType(ResourceType.SIMULATION)
        .resourceId(simulationId)
        .eventType(NotificationEventType.SIMULATION_COMPLETED)
        .tenantId(tenantId)
        .timestamp(Instant.now())
        .build();
  }

  /** Ground truth through raw JDBC, as a scoped read would itself be filtered. */
  private long recordedFor(String recipientId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM notification_events"
            + " WHERE notification_trigger_id = ? AND user_id = ?",
        Long.class,
        triggerId,
        recipientId);
  }

  private String insertMarking(int order) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO marking_definitions (marking_definition_id, marking_definition_type,"
            + " marking_definition_definition, marking_definition_color, marking_definition_order,"
            + " marking_definition_protected, tenant_id, marking_definition_created_at,"
            + " marking_definition_updated_at)"
            + " VALUES (?, ?, ?, '#c62828', ?, false, ?, now(), now())",
        id,
        MarkingDefinition.TYPE_TLP,
        "TLP:" + id,
        order,
        tenantId);
    return id;
  }

  private String insertAsset(AssetCriticality criticality, String markingId) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO assets (asset_id, asset_name, asset_type, asset_created_at, asset_updated_at,"
            + " tenant_id, asset_hostname, endpoint_platform, endpoint_arch, asset_criticality,"
            + " marking_ids)"
            + " VALUES (?, ?, 'Endpoint', now(), now(), ?, ?, 'Linux', 'x86_64', ?, ?)",
        id,
        "critical-asset-marking-" + id,
        tenantId,
        "host-" + id,
        criticality == null ? null : criticality.name(),
        markingId == null ? null : new String[] {markingId});
    return id;
  }

  private void insertAttackStep(String sourceId, String targetId) {
    jdbc.update(
        "INSERT INTO attackpath_execution (attackpath_execution_id, tenant_id,"
            + " attackpath_execution_simulation_id, attackpath_execution_source_kind,"
            + " attackpath_execution_source_asset_id, attackpath_execution_target_kind,"
            + " attackpath_execution_target_asset_id, attackpath_execution_target_key,"
            + " attackpath_execution_executed_at, attackpath_execution_row_version,"
            + " attackpath_execution_prevention_status)"
            + " VALUES (?, ?, ?, 'AGENT', ?, 'ASSET', ?, ?, now(), 0, 'Not Prevented')",
        UUID.randomUUID().toString(),
        tenantId,
        simulationId,
        sourceId,
        targetId,
        targetId);
  }
}
