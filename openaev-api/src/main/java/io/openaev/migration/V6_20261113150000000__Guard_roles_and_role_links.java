package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds row triggers on {@code roles}, {@code roles_capabilities} and {@code groups_roles} that
 * apply the platform-write rule and the tenant rule of a role link to every client of the
 * connection, whether {@code roles} is in {@code openaev.tenant.active-tables} or not. The
 * statement inspector applies the platform-write rule to what Hibernate sends once {@code roles} is
 * active; the triggers apply it, and the link rule, to every statement, a {@code JdbcTemplate} or a
 * JDBC statement included, whose SQL the inspector never sees. None of them is {@code SECURITY
 * DEFINER}: no row-level security applies to these tables, so the lookups of the parent rows read
 * what the caller reads.
 *
 * <ul>
 *   <li>{@code roles}: an {@code INSERT}, {@code UPDATE} or {@code DELETE} of a platform role
 *       ({@code tenant_id IS NULL}, before or after the statement) goes through {@code
 *       require_tenant_write(NULL, true)}, which raises unless the transaction set {@code
 *       app.platform_write}. A tenant role is written by the tenant API, tenant creation and the
 *       {@code ON DELETE CASCADE} of {@code tenants}, none of which sets the marker.
 *   <li>{@code roles_capabilities}: an {@code INSERT}, {@code UPDATE} or {@code DELETE} of a row
 *       whose role is a platform role goes through the same check, so a platform role cannot gain
 *       or lose a capability from an unmarked transaction. The table has no tenant column, so the
 *       trigger reads {@code roles.tenant_id} of the parent. A role that is not found is left to
 *       the foreign key: the role is missing, or it is already gone in the cascade that deletes the
 *       row.
 *   <li>{@code groups_roles}: an {@code INSERT} of a link, or an {@code UPDATE} that changes its
 *       role or its group, requires the role and the group to be of the same tenant ({@code
 *       role.tenant_id IS NOT DISTINCT FROM group.tenant_id}): a platform role is linked to a
 *       platform group, a tenant role to a group of its tenant. Any other link raises. A role or a
 *       group that is not found is left to the foreign key. A link is not a write of the platform
 *       role, so it does not need the marker.
 * </ul>
 *
 * <p>A Flyway migration that writes a platform role or one of its capabilities after this one runs
 * {@code SELECT set_config('app.platform_write', 'true', true)} first, in the same transaction. A
 * writer that has to commit a row these rules refuse, such as a database restore or a test that
 * seeds a cross-tenant link, runs {@code SET LOCAL session_replication_role = replica} in its
 * transaction, which skips plain triggers.
 */
@Component
public class V6_20261113150000000__Guard_roles_and_role_links extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      guardPlatformRoles(statement);
      guardPlatformRoleCapabilities(statement);
      guardRoleLinks(statement);
    }
  }

  private static void guardPlatformRoles(Statement statement) throws Exception {
    statement.execute(
        """
        CREATE OR REPLACE FUNCTION roles_guard_platform_row()
        RETURNS trigger
        LANGUAGE plpgsql AS $$
        BEGIN
          IF (TG_OP <> 'INSERT' AND OLD.tenant_id IS NULL)
             OR (TG_OP <> 'DELETE' AND NEW.tenant_id IS NULL) THEN
            PERFORM require_tenant_write(NULL, true);
          END IF;
          IF TG_OP = 'DELETE' THEN
            RETURN OLD;
          END IF;
          RETURN NEW;
        END
        $$;
        """);
    statement.execute("DROP TRIGGER IF EXISTS roles_guard_platform_row ON roles;");
    statement.execute(
        """
        CREATE TRIGGER roles_guard_platform_row
        BEFORE INSERT OR UPDATE OR DELETE ON roles
        FOR EACH ROW EXECUTE FUNCTION roles_guard_platform_row();
        """);
  }

  private static void guardPlatformRoleCapabilities(Statement statement) throws Exception {
    statement.execute(
        """
        CREATE OR REPLACE FUNCTION roles_capabilities_guard_platform_role()
        RETURNS trigger
        LANGUAGE plpgsql AS $$
        DECLARE
          role_tenant text;
        BEGIN
          IF TG_OP <> 'INSERT' THEN
            SELECT tenant_id INTO role_tenant FROM roles WHERE role_id = OLD.role_id;
            IF FOUND AND role_tenant IS NULL THEN
              PERFORM require_tenant_write(NULL, true);
            END IF;
          END IF;
          IF TG_OP <> 'DELETE' THEN
            SELECT tenant_id INTO role_tenant FROM roles WHERE role_id = NEW.role_id;
            IF FOUND AND role_tenant IS NULL THEN
              PERFORM require_tenant_write(NULL, true);
            END IF;
          END IF;
          IF TG_OP = 'DELETE' THEN
            RETURN OLD;
          END IF;
          RETURN NEW;
        END
        $$;
        """);
    statement.execute(
        "DROP TRIGGER IF EXISTS roles_capabilities_guard_platform_role ON roles_capabilities;");
    statement.execute(
        """
        CREATE TRIGGER roles_capabilities_guard_platform_role
        BEFORE INSERT OR UPDATE OR DELETE ON roles_capabilities
        FOR EACH ROW EXECUTE FUNCTION roles_capabilities_guard_platform_role();
        """);
  }

  private static void guardRoleLinks(Statement statement) throws Exception {
    statement.execute(
        """
        CREATE OR REPLACE FUNCTION groups_roles_guard_same_tenant()
        RETURNS trigger
        LANGUAGE plpgsql AS $$
        DECLARE
          role_tenant text;
          group_tenant text;
        BEGIN
          IF TG_OP = 'UPDATE'
             AND NEW.role_id IS NOT DISTINCT FROM OLD.role_id
             AND NEW.group_id IS NOT DISTINCT FROM OLD.group_id THEN
            RETURN NEW;
          END IF;
          SELECT tenant_id INTO role_tenant FROM roles WHERE role_id = NEW.role_id;
          IF NOT FOUND THEN
            RETURN NEW;
          END IF;
          SELECT tenant_id INTO group_tenant FROM groups WHERE group_id = NEW.group_id;
          IF NOT FOUND THEN
            RETURN NEW;
          END IF;
          IF role_tenant IS DISTINCT FROM group_tenant THEN
            RAISE EXCEPTION 'link of role % to group % refused: the role and the group are not of the same tenant',
              NEW.role_id, NEW.group_id USING ERRCODE = '23514';
          END IF;
          RETURN NEW;
        END
        $$;
        """);
    statement.execute("DROP TRIGGER IF EXISTS groups_roles_guard_same_tenant ON groups_roles;");
    statement.execute(
        """
        CREATE TRIGGER groups_roles_guard_same_tenant
        BEFORE INSERT OR UPDATE ON groups_roles
        FOR EACH ROW EXECUTE FUNCTION groups_roles_guard_same_tenant();
        """);
  }
}
