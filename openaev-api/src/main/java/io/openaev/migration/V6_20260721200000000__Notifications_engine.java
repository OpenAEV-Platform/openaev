package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Notifications bus / engine (OpenCTI model): notifiers (UI / email / webhook delivery channels),
 * notification triggers (live + digest), notification events outbox (digest source) and per-user
 * in-app notifications. Built-in notifiers ("User interface", "Default mailer") are seeded for
 * every existing tenant; new tenants get them lazily from NotifierService.
 */
@Component
public class V6_20260721200000000__Notifications_engine extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // -- Notifiers --
      statement.executeUpdate(
          """
          CREATE TABLE IF NOT EXISTS notifiers (
              notifier_id varchar(255) NOT NULL,
              notifier_name varchar(255) NOT NULL,
              notifier_description text,
              notifier_type varchar(255) NOT NULL,
              notifier_configuration jsonb,
              notifier_built_in bool NOT NULL DEFAULT false,
              notifier_created_at timestamp NOT NULL DEFAULT now(),
              notifier_updated_at timestamp NOT NULL DEFAULT now(),
              tenant_id varchar(255) NOT NULL,
              PRIMARY KEY (notifier_id),
              CONSTRAINT fk_notifiers_tenant
                  FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
          );
          CREATE INDEX IF NOT EXISTS idx_notifiers_tenant ON notifiers(tenant_id);
          """);

      // -- Notification triggers --
      statement.executeUpdate(
          """
          CREATE TABLE IF NOT EXISTS notification_triggers (
              notification_trigger_id varchar(255) NOT NULL,
              notification_trigger_name varchar(255) NOT NULL,
              notification_trigger_type varchar(255) NOT NULL,
              notification_trigger_enabled bool NOT NULL DEFAULT true,
              notification_trigger_resource_type varchar(255),
              notification_trigger_event_types jsonb,
              notification_trigger_filters jsonb,
              notification_trigger_instance_id varchar(255),
              notification_trigger_period varchar(255),
              notification_trigger_time varchar(255),
              user_id varchar(255) NOT NULL,
              notification_trigger_created_at timestamp NOT NULL DEFAULT now(),
              notification_trigger_updated_at timestamp NOT NULL DEFAULT now(),
              tenant_id varchar(255) NOT NULL,
              PRIMARY KEY (notification_trigger_id),
              CONSTRAINT fk_notification_triggers_user
                  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
              CONSTRAINT fk_notification_triggers_tenant
                  FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
          );
          CREATE INDEX IF NOT EXISTS idx_notification_triggers_tenant
              ON notification_triggers(tenant_id);
          CREATE INDEX IF NOT EXISTS idx_notification_triggers_resource_type
              ON notification_triggers(notification_trigger_resource_type);
          CREATE INDEX IF NOT EXISTS idx_notification_triggers_user
              ON notification_triggers(user_id);
          """);

      // -- Join tables --
      statement.executeUpdate(
          """
          CREATE TABLE IF NOT EXISTS notification_triggers_notifiers (
              notification_trigger_id varchar(255) NOT NULL,
              notifier_id varchar(255) NOT NULL,
              PRIMARY KEY (notification_trigger_id, notifier_id),
              CONSTRAINT fk_ntn_trigger
                  FOREIGN KEY (notification_trigger_id)
                  REFERENCES notification_triggers(notification_trigger_id) ON DELETE CASCADE,
              CONSTRAINT fk_ntn_notifier
                  FOREIGN KEY (notifier_id) REFERENCES notifiers(notifier_id) ON DELETE CASCADE
          );
          CREATE TABLE IF NOT EXISTS notification_triggers_children (
              notification_trigger_id varchar(255) NOT NULL,
              child_notification_trigger_id varchar(255) NOT NULL,
              PRIMARY KEY (notification_trigger_id, child_notification_trigger_id),
              CONSTRAINT fk_ntc_parent
                  FOREIGN KEY (notification_trigger_id)
                  REFERENCES notification_triggers(notification_trigger_id) ON DELETE CASCADE,
              CONSTRAINT fk_ntc_child
                  FOREIGN KEY (child_notification_trigger_id)
                  REFERENCES notification_triggers(notification_trigger_id) ON DELETE CASCADE
          );
          CREATE TABLE IF NOT EXISTS notification_triggers_users (
              notification_trigger_id varchar(255) NOT NULL,
              user_id varchar(255) NOT NULL,
              PRIMARY KEY (notification_trigger_id, user_id),
              CONSTRAINT fk_ntu_trigger
                  FOREIGN KEY (notification_trigger_id)
                  REFERENCES notification_triggers(notification_trigger_id) ON DELETE CASCADE,
              CONSTRAINT fk_ntu_user
                  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
          );
          CREATE TABLE IF NOT EXISTS notification_triggers_groups (
              notification_trigger_id varchar(255) NOT NULL,
              group_id varchar(255) NOT NULL,
              PRIMARY KEY (notification_trigger_id, group_id),
              CONSTRAINT fk_ntg_trigger
                  FOREIGN KEY (notification_trigger_id)
                  REFERENCES notification_triggers(notification_trigger_id) ON DELETE CASCADE,
              CONSTRAINT fk_ntg_group
                  FOREIGN KEY (group_id) REFERENCES groups(group_id) ON DELETE CASCADE
          );
          """);

      // -- Notification events outbox (digest source) --
      statement.executeUpdate(
          """
          CREATE TABLE IF NOT EXISTS notification_events (
              notification_event_id varchar(255) NOT NULL,
              notification_trigger_id varchar(255) NOT NULL,
              user_id varchar(255) NOT NULL,
              notification_event_type varchar(255) NOT NULL,
              notification_event_message text,
              notification_event_resource_type varchar(255),
              notification_event_resource_id varchar(255),
              notification_event_created_at timestamp NOT NULL DEFAULT now(),
              tenant_id varchar(255) NOT NULL,
              PRIMARY KEY (notification_event_id),
              CONSTRAINT fk_notification_events_trigger
                  FOREIGN KEY (notification_trigger_id)
                  REFERENCES notification_triggers(notification_trigger_id) ON DELETE CASCADE,
              CONSTRAINT fk_notification_events_user
                  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
              CONSTRAINT fk_notification_events_tenant
                  FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
          );
          CREATE INDEX IF NOT EXISTS idx_notification_events_trigger_created
              ON notification_events(notification_trigger_id, notification_event_created_at);
          CREATE INDEX IF NOT EXISTS idx_notification_events_created
              ON notification_events(notification_event_created_at);
          """);

      // -- In-app notifications --
      statement.executeUpdate(
          """
          CREATE TABLE IF NOT EXISTS notifications (
              notification_id varchar(255) NOT NULL,
              notification_name varchar(255) NOT NULL,
              notification_type varchar(255) NOT NULL,
              notification_content jsonb,
              notification_is_read bool NOT NULL DEFAULT false,
              user_id varchar(255) NOT NULL,
              notification_created_at timestamp NOT NULL DEFAULT now(),
              tenant_id varchar(255) NOT NULL,
              PRIMARY KEY (notification_id),
              CONSTRAINT fk_notifications_user
                  FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
              CONSTRAINT fk_notifications_tenant
                  FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE
          );
          CREATE INDEX IF NOT EXISTS idx_notifications_user_read
              ON notifications(user_id, notification_is_read);
          CREATE INDEX IF NOT EXISTS idx_notifications_user_created
              ON notifications(user_id, notification_created_at);
          """);

      // -- Seed built-in notifiers for every existing tenant --
      statement.executeUpdate(
          """
          INSERT INTO notifiers (notifier_id, notifier_name, notifier_description, notifier_type,
                                 notifier_configuration, notifier_built_in, tenant_id)
          SELECT gen_random_uuid(), 'User interface',
                 'Built-in in-app notifier', 'UI', '{}'::jsonb, true, t.tenant_id
          FROM tenants t
          WHERE NOT EXISTS (
              SELECT 1 FROM notifiers n
              WHERE n.tenant_id = t.tenant_id AND n.notifier_type = 'UI' AND n.notifier_built_in
          );
          INSERT INTO notifiers (notifier_id, notifier_name, notifier_description, notifier_type,
                                 notifier_configuration, notifier_built_in, tenant_id)
          SELECT gen_random_uuid(), 'Default mailer',
                 'Built-in email notifier', 'EMAIL', '{}'::jsonb, true, t.tenant_id
          FROM tenants t
          WHERE NOT EXISTS (
              SELECT 1 FROM notifiers n
              WHERE n.tenant_id = t.tenant_id AND n.notifier_type = 'EMAIL' AND n.notifier_built_in
          );
          """);
    }
  }
}

// -- ROLLBACK --
// DROP TABLE IF EXISTS notifications;
// DROP TABLE IF EXISTS notification_events;
// DROP TABLE IF EXISTS notification_triggers_groups;
// DROP TABLE IF EXISTS notification_triggers_users;
// DROP TABLE IF EXISTS notification_triggers_children;
// DROP TABLE IF EXISTS notification_triggers_notifiers;
// DROP TABLE IF EXISTS notification_triggers;
// DROP TABLE IF EXISTS notifiers;
