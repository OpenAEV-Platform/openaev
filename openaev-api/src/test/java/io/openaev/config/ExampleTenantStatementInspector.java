package io.openaev.config;

import io.openaev.database.model.Tag;
import java.util.List;
import java.util.regex.Pattern;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * End-to-end example wiring: a {@link StatementInspector} that activates tenant filtering for a
 * single table ({@code tags}) and passes every other statement through untouched. This mirrors the
 * table-by-table activation model and is the shape the real wiring (T5.8) will take, scoped down to
 * one table so the chain (set_config → rewrite → {@code can_access_tenant} → execution) can be
 * proven against PostgreSQL in {@code TenantIsolationExampleTest}.
 *
 * <p>Only statements that mention the activated table are parsed and rewritten; the rest are
 * returned as-is, so unrelated (and possibly unparseable) SQL is never affected by a one-table
 * activation.
 */
public class ExampleTenantStatementInspector implements StatementInspector {

  private static final TenantStatementInspector DELEGATE =
      new TenantStatementInspector(TenantTables.fromEntities(List.of(Tag.class)));

  private static final Pattern ACTIVE_TABLE =
      Pattern.compile("(?i)(?<![a-z0-9_])tags(?![a-z0-9_])");

  @Override
  public String inspect(String sql) {
    if (sql == null || !ACTIVE_TABLE.matcher(sql).find()) {
      return sql;
    }
    return DELEGATE.inspect(sql);
  }
}
