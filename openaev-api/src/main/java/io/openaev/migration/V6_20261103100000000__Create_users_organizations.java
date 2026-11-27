package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Moves the user's organization from the single {@code users.user_organization} column to the
 * {@code users_organizations} join table, so a user can hold one organization per tenant: a user is
 * platform-level while an organization belongs to one tenant, and linking a user in a tenant used
 * to overwrite its organization in every other.
 *
 * <p>The legacy column is kept (and still populated by pods running an older version during a
 * rolling deploy): it is no longer read by the application and is dropped by a later migration,
 * following the deprecation-first rule for columns.
 */
@Component
public class V6_20261103100000000__Create_users_organizations extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS users_organizations (
            user_id         VARCHAR(255) NOT NULL
              CONSTRAINT fk_users_organizations_user
              REFERENCES users(user_id) ON DELETE CASCADE,
            organization_id VARCHAR(255) NOT NULL
              CONSTRAINT fk_users_organizations_organization
              REFERENCES organizations(organization_id) ON DELETE CASCADE,
            CONSTRAINT pk_users_organizations PRIMARY KEY (user_id, organization_id)
          );
          CREATE INDEX IF NOT EXISTS idx_users_organizations_organization_id
            ON users_organizations(organization_id);
          """);
      // A user had a single organization, so each tenant holds at most one of them per user.
      statement.execute(
          """
          INSERT INTO users_organizations (user_id, organization_id)
          SELECT user_id, user_organization
          FROM users
          WHERE user_organization IS NOT NULL
          ON CONFLICT DO NOTHING;
          """);
    }
  }
}
