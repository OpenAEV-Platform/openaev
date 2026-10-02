package io.openaev.database.audit;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantBase;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refuses an unattributed write on a table whose attribution has moved to v2 ({@code
 * openaev.tenant.active-tables}); on every other table, the legacy contract still owns attribution,
 * so an unattributed write keeps falling back to the default tenant exactly as before this
 * fail-fast was introduced.
 *
 * <p>The active-tables set is also read by the isolation of the tables it names, so a table that is
 * not in it never gets a tenant-scoped read either: leaving its writes on the default-tenant
 * fallback does not open an isolation gap that did not already exist.
 */
@Component
public class TenantBaseListener<T extends TenantBase> {

  private static final String ALL_STRICT = "*";

  private final Set<String> activeTables;

  public TenantBaseListener(@Value("${openaev.tenant.active-tables:}") List<String> activeTables) {
    Set<String> normalized = new HashSet<>();
    for (String table : activeTables) {
      if (!table.isBlank()) {
        normalized.add(table.trim().toLowerCase(Locale.ROOT));
      }
    }
    this.activeTables = normalized;
  }

  @PrePersist
  private void manageTenant(T entity) {
    if (entity.getTenant() == null) {
      if (!TenantContext.hasCurrentTenant() && isActive(entity.getClass())) {
        throw new IllegalStateException(
            "unattributed tenant write: " + entity.getClass().getSimpleName());
      }
      entity.setTenant(new Tenant(TenantContext.getCurrentTenant()));
    }
  }

  private boolean isActive(Class<?> entityClass) {
    return activeTables.contains(ALL_STRICT) || activeTables.contains(tableName(entityClass));
  }

  private static String tableName(Class<?> entityClass) {
    for (Class<?> type = entityClass;
        type != null && type != Object.class;
        type = type.getSuperclass()) {
      Table table = type.getAnnotation(Table.class);
      if (table != null && !table.name().isBlank()) {
        return table.name().toLowerCase(Locale.ROOT);
      }
    }
    throw new IllegalStateException(
        "tenant-aware entity must declare @Table(name=...): " + entityClass.getName());
  }
}
