package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Payload approval notifications now go through live triggers, like revocations: the outbox sent by
 * a job and the per-user notification preferences are gone.
 *
 * <p>Both tables were created by {@link V6_20261102100400000__Add_payload_approval_notifications}
 * on the payload approval prototype and were never released, so they are dropped without a
 * deprecation step. The alerts already sent stay in {@code notifications}.
 */
@Component
public class V6_20261125100000000__Drop_payload_approval_notifications_and_preferences
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.executeUpdate("DROP TABLE IF EXISTS payload_approval_notifications;");
      statement.executeUpdate("DROP TABLE IF EXISTS user_notification_preferences;");
    }
  }
}
