package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Payload approval notifications (Task 6).
 *
 * <ul>
 *   <li>{@code payload_approval_notifications}: an outbox written in the same transaction as the
 *       approval change it reports (a payload or version going pending, approved, rejected or
 *       superseded), then sent and marked by a job every minute, so nothing is lost.
 *   <li>{@code user_notification_preferences}: per user (global, not per tenant), which channels
 *       receive each notification type. No row means the defaults: in-app on, email off.
 * </ul>
 */
@Component
public class V6_20261014120000001__Add_payload_approval_notifications extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate(
          """
          CREATE TABLE IF NOT EXISTS payload_approval_notifications (
            payload_approval_notification_id VARCHAR(255) NOT NULL PRIMARY KEY,
            tenant_id VARCHAR(255) NOT NULL REFERENCES tenants(tenant_id) ON DELETE CASCADE,
            payload_id VARCHAR(255) NOT NULL REFERENCES payloads(payload_id) ON DELETE CASCADE,
            payload_approval_notification_event VARCHAR(32) NOT NULL,
            payload_approval_notification_payload_name VARCHAR(255),
            payload_approval_notification_version_number INTEGER,
            payload_approval_notification_actor VARCHAR(255)
              REFERENCES users(user_id) ON DELETE SET NULL,
            payload_approval_notification_actor_name VARCHAR(255),
            payload_approval_notification_recipient VARCHAR(255)
              REFERENCES users(user_id) ON DELETE CASCADE,
            payload_approval_notification_comment TEXT,
            payload_approval_notification_created_at TIMESTAMP WITH TIME ZONE NOT NULL
              DEFAULT now(),
            payload_approval_notification_dispatched_at TIMESTAMP WITH TIME ZONE
          );
          """);
      // The job reads what is still to send, tenant by tenant.
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_payload_approval_notifications_to_send
            ON payload_approval_notifications(tenant_id, payload_approval_notification_created_at)
            WHERE payload_approval_notification_dispatched_at IS NULL;
          """);
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_payload_approval_notifications_payload
            ON payload_approval_notifications(payload_id);
          """);
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_payload_approval_notifications_actor
            ON payload_approval_notifications(payload_approval_notification_actor);
          """);
      statement.executeUpdate(
          """
          CREATE INDEX IF NOT EXISTS idx_payload_approval_notifications_recipient
            ON payload_approval_notifications(payload_approval_notification_recipient);
          """);

      statement.executeUpdate(
          """
          CREATE TABLE IF NOT EXISTS user_notification_preferences (
            user_notification_preference_id VARCHAR(255) NOT NULL PRIMARY KEY,
            user_id VARCHAR(255) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
            user_notification_preference_type VARCHAR(64) NOT NULL,
            user_notification_preference_in_app BOOLEAN NOT NULL DEFAULT TRUE,
            user_notification_preference_email BOOLEAN NOT NULL DEFAULT FALSE,
            CONSTRAINT uq_user_notification_preferences_user_type
              UNIQUE (user_id, user_notification_preference_type)
          );
          """);
    }
  }
}
