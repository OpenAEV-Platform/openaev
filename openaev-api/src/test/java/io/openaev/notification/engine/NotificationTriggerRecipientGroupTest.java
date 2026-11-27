package io.openaev.notification.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.database.model.NotificationTriggerType;
import io.openaev.database.model.Tenant;
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

/**
 * The engine resolves the members of a trigger's recipient groups on a background thread. {@code
 * Group.users} is LAZY, so this read must stay inside the loader's transaction.
 *
 * <p>Not {@code @Transactional}: the scoped primitive refuses to open inside an active transaction.
 */
@WithMockUser(isAdmin = true)
@DisplayName("Notification trigger recipient groups (LAZY members)")
class NotificationTriggerRecipientGroupTest extends IntegrationTest {

  @Autowired private NotificationTriggerLoader loader;
  @Autowired private DataSource dataSource;
  @Autowired private TestUserHolder testUserHolder;

  private JdbcTemplate jdbc;
  private final String groupId = UUID.randomUUID().toString();
  private final String triggerId = UUID.randomUUID().toString();

  @BeforeEach
  void seedATriggerTargetingAGroup() {
    jdbc = new JdbcTemplate(dataSource);
    String memberId = testUserHolder.get().getId();
    jdbc.update(
        "INSERT INTO groups (group_id, group_name, tenant_id) VALUES (?, ?, ?)",
        groupId,
        "recipient-group-" + groupId,
        Tenant.DEFAULT_TENANT_UUID);
    jdbc.update("INSERT INTO users_groups (group_id, user_id) VALUES (?, ?)", groupId, memberId);
    jdbc.update(
        "INSERT INTO notification_triggers (notification_trigger_id, notification_trigger_name,"
            + " notification_trigger_type, notification_trigger_enabled, user_id, tenant_id)"
            + " VALUES (?, ?, 'LIVE', true, ?, ?)",
        triggerId,
        "recipient-trigger-" + triggerId,
        memberId,
        Tenant.DEFAULT_TENANT_UUID);
    jdbc.update(
        "INSERT INTO notification_triggers_groups (notification_trigger_id, group_id) VALUES (?, ?)",
        triggerId,
        groupId);
  }

  @AfterEach
  void sweep() {
    jdbc.update("DELETE FROM notification_triggers WHERE notification_trigger_id = ?", triggerId);
    jdbc.update("DELETE FROM groups WHERE group_id = ?", groupId);
  }

  @Test
  @DisplayName("given_triggerTargetingAGroup_should_resolveItsMembersAsRecipients")
  void given_triggerTargetingAGroup_should_resolveItsMembersAsRecipients() {
    // -- ACT --
    ResolvedNotificationTrigger resolved =
        loader.loadEnabledTriggers(NotificationTriggerType.LIVE).stream()
            .filter(trigger -> triggerId.equals(trigger.id()))
            .findFirst()
            .orElseThrow();

    // -- ASSERT --
    assertThat(resolved.recipientUserIds()).containsExactly(testUserHolder.get().getId());
  }
}
