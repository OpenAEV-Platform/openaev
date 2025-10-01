package io.openaev.migration;

import java.sql.Connection;
import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V3_82__NotificationRule extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    Connection connection = context.getConnection();
    Statement select = connection.createStatement();
    select.execute(
        """
                   CREATE TABLE notification_rules (
                       notification_rule_id varchar(255) not null,
                       notification_resource_type varchar(255) not null,
                       notification_resource_id varchar(255) not null,
                       notification_rule_trigger varchar(255) not null,
                       notification_rule_type varchar(255) not null,
                       notification_rule_subject varchar(255) not null,
                       user_id varchar(255) not null,
                       primary key (notification_rule_id),
                       CONSTRAINT uq_notification_rule UNIQUE (
                           notification_resource_id,
                           notification_rule_trigger,
                           user_id,
                           notification_rule_type
                       )
                   );
               CREATE INDEX idx_resource_id ON notification_rules (notification_resource_id);
                """);

    // add a trigger to delete the rule when a scenario is deleted
    select.execute(
        """
                  -- Delete function
                  CREATE OR REPLACE FUNCTION delete_notification_rules_for_scenario() RETURNS TRIGGER AS $$
                  BEGIN
                      DELETE FROM notification_rules
                      WHERE notification_resource_type = 'SCENARIO'
                          AND notification_resource_id = OLD.scenario_id;
                      RETURN OLD;
                  END;
                  $$ LANGUAGE plpgsql;

                 -- create the trigger on scenario table
                 CREATE TRIGGER trg_delete_scenario_notification_rules
                 AFTER DELETE ON scenarios
                 FOR EACH ROW
                 EXECUTE FUNCTION delete_notification_rules_for_scenario();

            """);
  }
}
