package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the details XTM Hub sends with a news feed item (description, image, authors...), as flat
 * key/value rows per item, and the news feed types XTM Hub provisions for a registered tenant.
 *
 * <p>The metadata rows carry no tenant: like their news feed item, they belong to a single user.
 */
@Component
public class V6_20261120100000000__Add_news_feed_item_metadata_and_available_types
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      // The primary key leads with the item, so it also serves as the index of the item FK.
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS news_feed_items_metadata (
              news_feed_item_id VARCHAR(255) NOT NULL
                REFERENCES news_feed_items(news_feed_item_id) ON DELETE CASCADE,
              news_feed_item_metadata_key TEXT NOT NULL,
              news_feed_item_metadata_value TEXT NOT NULL,
              CONSTRAINT pk_news_feed_items_metadata
                PRIMARY KEY (news_feed_item_id, news_feed_item_metadata_key)
          );
          """);

      statement.execute(
          """
          ALTER TABLE tenant_xtmhub_registrations
            ADD COLUMN IF NOT EXISTS registration_available_news_feed_types TEXT[] NOT NULL
              DEFAULT '{}';
          """);
    }
  }
}
