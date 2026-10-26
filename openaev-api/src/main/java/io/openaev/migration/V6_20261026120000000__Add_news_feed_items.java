package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the per-user XTM Hub news feed items and the per-user news feed types a user unsubscribed
 * from ({@code *} when every type is unsubscribed).
 */
@Component
public class V6_20261026120000000__Add_news_feed_items extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // The unique (user, hub item) constraint also serves as the index of the user FK.
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS news_feed_items (
              news_feed_item_id VARCHAR(255) PRIMARY KEY,
              news_feed_item_hub_id VARCHAR(255) NOT NULL,
              news_feed_item_title TEXT NOT NULL,
              news_feed_item_type VARCHAR(255) NOT NULL,
              news_feed_item_tags TEXT[] NOT NULL DEFAULT '{}',
              news_feed_item_url_path TEXT,
              news_feed_item_creation_date TIMESTAMP WITH TIME ZONE NOT NULL,
              news_feed_item_is_read BOOLEAN NOT NULL DEFAULT FALSE,
              user_id VARCHAR(255) NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
              CONSTRAINT uk_news_feed_items_user_hub_item UNIQUE (user_id, news_feed_item_hub_id)
          );
          """);

      // The unique constraint leads with user_id, so it cannot serve the lookup by Hub item.
      statement.execute(
          """
          CREATE INDEX IF NOT EXISTS idx_news_feed_items_hub_id
            ON news_feed_items (news_feed_item_hub_id);
          """);

      statement.execute(
          """
          ALTER TABLE users
            ADD COLUMN IF NOT EXISTS user_unsubscribed_news_feed_types TEXT[] NOT NULL DEFAULT '{}';
          """);
    }
  }
}
