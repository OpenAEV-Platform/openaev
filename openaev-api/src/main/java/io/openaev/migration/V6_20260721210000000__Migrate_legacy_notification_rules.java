package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Migrates the legacy notification rules (scenario score-degradation emails) to the new
 * notifications engine and removes the legacy storage.
 *
 * <p>Each {@code notification_rules} row becomes a live notification trigger scoped to its scenario
 * (instance trigger) subscribed to the {@code SCORE_DEGRADATION} event, delivered through the
 * tenant's built-in email notifier - the exact behavior the legacy rule had. The legacy table and
 * its scenario-deletion trigger/function are then dropped.
 */
@Component
public class V6_20260721210000000__Migrate_legacy_notification_rules extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // Nothing to migrate when the legacy table is already gone (re-run safety)
      statement.execute("SELECT to_regclass('notification_rules') IS NOT NULL AS present");
      boolean legacyTablePresent;
      try (var resultSet = statement.getResultSet()) {
        resultSet.next();
        legacyTablePresent = resultSet.getBoolean("present");
      }

      if (legacyTablePresent) {
        // Built-in email notifiers must exist for every tenant owning legacy rules
        // (normally seeded by the notifications engine migration, re-asserted here)
        statement.executeUpdate(
            """
            INSERT INTO notifiers (notifier_id, notifier_name, notifier_description, notifier_type,
                                   notifier_configuration, notifier_built_in, tenant_id)
            SELECT gen_random_uuid(), 'Default mailer',
                   'Built-in email notifier', 'EMAIL', '{}'::jsonb, true, t.tenant_id
            FROM (SELECT DISTINCT tenant_id FROM notification_rules) t
            WHERE NOT EXISTS (
                SELECT 1 FROM notifiers n
                WHERE n.tenant_id = t.tenant_id
                  AND n.notifier_type = 'EMAIL' AND n.notifier_built_in
            );
            """);

        // Convert each legacy rule into a live instance trigger on its scenario, subscribed to
        // SCORE_DEGRADATION and wired to the tenant's built-in email notifier. Rules pointing at
        // deleted users or scenarios are skipped; the NOT EXISTS guard keeps a crashed retry
        // from duplicating triggers.
        statement.executeUpdate(
            """
            WITH migrated AS (
                INSERT INTO notification_triggers (
                    notification_trigger_id, notification_trigger_name, notification_trigger_type,
                    notification_trigger_enabled, notification_trigger_resource_type,
                    notification_trigger_event_types, notification_trigger_instance_id,
                    user_id, tenant_id)
                SELECT gen_random_uuid(),
                       nr.notification_rule_subject,
                       'LIVE',
                       true,
                       'SCENARIO',
                       '["SCORE_DEGRADATION"]'::jsonb,
                       nr.notification_resource_id,
                       nr.user_id,
                       nr.tenant_id
                FROM notification_rules nr
                WHERE nr.notification_resource_type = 'SCENARIO'
                  AND EXISTS (SELECT 1 FROM users u WHERE u.user_id = nr.user_id)
                  AND EXISTS (
                      SELECT 1 FROM scenarios s
                      WHERE s.scenario_id = nr.notification_resource_id)
                  AND NOT EXISTS (
                      SELECT 1 FROM notification_triggers t
                      WHERE t.user_id = nr.user_id
                        AND t.notification_trigger_instance_id = nr.notification_resource_id
                        AND t.notification_trigger_event_types @> '["SCORE_DEGRADATION"]'::jsonb)
                RETURNING notification_trigger_id, tenant_id
            )
            INSERT INTO notification_triggers_notifiers (notification_trigger_id, notifier_id)
            SELECT m.notification_trigger_id, n.notifier_id
            FROM migrated m
            JOIN LATERAL (
                SELECT n2.notifier_id
                FROM notifiers n2
                WHERE n2.tenant_id = m.tenant_id
                  AND n2.notifier_type = 'EMAIL' AND n2.notifier_built_in
                LIMIT 1
            ) n ON true
            ON CONFLICT DO NOTHING;
            """);
      }

      // Remove the legacy storage and its scenario-deletion machinery
      statement.executeUpdate(
          """
          DROP TRIGGER IF EXISTS trg_delete_scenario_notification_rules ON scenarios;
          DROP FUNCTION IF EXISTS delete_notification_rules_for_scenario();
          DROP TABLE IF EXISTS notification_rules;
          """);
    }
  }
}

// -- ROLLBACK --
// The legacy notification_rules table is dropped after conversion; recreate it from V3_82 and
// repopulate from the migrated triggers if a rollback is ever needed:
// SELECT t.notification_trigger_instance_id, t.notification_trigger_name, t.user_id, t.tenant_id
// FROM notification_triggers t
// WHERE t.notification_trigger_event_types @> '["SCORE_DEGRADATION"]'::jsonb;
