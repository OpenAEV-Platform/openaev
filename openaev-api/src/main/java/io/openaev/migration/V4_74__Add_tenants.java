package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_74__Add_tenants extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      statement.execute(
          """
        CREATE TABLE IF NOT EXISTS tenants (
            tenant_id VARCHAR(255) NOT NULL CONSTRAINT tenant_pkey PRIMARY KEY,
            tenant_name VARCHAR(255) NOT NULL,
            tenant_description TEXT,
            tenant_created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            tenant_updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
            CONSTRAINT uk_tenant_name UNIQUE (tenant_name)
        );
      """);

      statement.execute(
          """
        CREATE INDEX IF NOT EXISTS idx_tenants_name
        ON tenants (tenant_name);
      """);
    }
  }
}
