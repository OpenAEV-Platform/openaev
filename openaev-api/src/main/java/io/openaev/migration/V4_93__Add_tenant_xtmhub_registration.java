package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_93__Add_tenant_xtmhub_registration extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS tenant_xtmhub_registrations (
              registration_id           VARCHAR(255) NOT NULL
                  CONSTRAINT tenant_xtmhub_registration_pkey PRIMARY KEY,
              tenant_id                 VARCHAR(255) NOT NULL
                  CONSTRAINT tenant_xtmhub_registration_tenant_fk
                      REFERENCES tenants (tenant_id),
              registration_token        TEXT,
              registration_date         TIMESTAMP,
              registration_status       VARCHAR(255),
              registration_user_id      VARCHAR(255),
              registration_user_name    VARCHAR(255),
              registration_last_connectivity_check TIMESTAMP,
              CONSTRAINT uk_tenant_xtmhub_registration UNIQUE (tenant_id)
          );
          """);
    }
  }
}
