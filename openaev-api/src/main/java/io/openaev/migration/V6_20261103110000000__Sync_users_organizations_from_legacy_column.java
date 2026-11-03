package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Keeps {@code users_organizations} up to date with the writes of the pods still running a version
 * that only knows the legacy {@code users.user_organization} column, during a rolling deploy.
 *
 * <p>A change of that column made by an older pod is replayed on the join table: the organization
 * replaces the one the user holds in its tenant, and clearing the column removes the link it
 * pointed to. Nothing is replayed the other way round: the application no longer writes the column,
 * so it never fires the trigger. The trigger is dropped together with the column, by the migration
 * that removes it.
 */
@Component
public class V6_20261103110000000__Sync_users_organizations_from_legacy_column
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE OR REPLACE FUNCTION sync_users_organizations_from_legacy() RETURNS trigger AS $$
          BEGIN
            IF TG_OP = 'UPDATE' AND OLD.user_organization IS NOT NULL THEN
              DELETE FROM users_organizations
              WHERE user_id = NEW.user_id AND organization_id = OLD.user_organization;
            END IF;
            IF NEW.user_organization IS NOT NULL THEN
              DELETE FROM users_organizations uo
              USING organizations existing, organizations target
              WHERE uo.user_id = NEW.user_id
                AND existing.organization_id = uo.organization_id
                AND target.organization_id = NEW.user_organization
                AND existing.tenant_id = target.tenant_id;
              INSERT INTO users_organizations (user_id, organization_id)
              VALUES (NEW.user_id, NEW.user_organization)
              ON CONFLICT DO NOTHING;
            END IF;
            RETURN NEW;
          END;
          $$ LANGUAGE plpgsql;
          """);
      // Two triggers: a WHEN clause cannot reference OLD on an INSERT event.
      statement.execute(
          """
          DROP TRIGGER IF EXISTS trg_users_organizations_legacy_insert ON users;
          CREATE TRIGGER trg_users_organizations_legacy_insert
            AFTER INSERT ON users
            FOR EACH ROW WHEN (NEW.user_organization IS NOT NULL)
            EXECUTE FUNCTION sync_users_organizations_from_legacy();
          DROP TRIGGER IF EXISTS trg_users_organizations_legacy_update ON users;
          CREATE TRIGGER trg_users_organizations_legacy_update
            AFTER UPDATE OF user_organization ON users
            FOR EACH ROW WHEN (OLD.user_organization IS DISTINCT FROM NEW.user_organization)
            EXECUTE FUNCTION sync_users_organizations_from_legacy();
          """);
    }
  }
}
