package io.openaev.migration;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V4_99__Add_tenant_xtmhub_registration extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {

      statement.execute(
          """
              CREATE TABLE IF NOT EXISTS tenant_xtmhub_registrations (
                  registration_id                      VARCHAR(255) NOT NULL
                      CONSTRAINT tenant_xtmhub_registration_pkey PRIMARY KEY,
                  tenant_id                            VARCHAR(255) NOT NULL
                      CONSTRAINT tenant_xtmhub_registration_tenant_fk
                          REFERENCES tenants (tenant_id) ON DELETE CASCADE,
                  registration_token                   TEXT,
                  registration_date                    TIMESTAMP,
                  registration_status                  VARCHAR(255),
                  registration_user_id                 VARCHAR(255),
                  registration_user_name               VARCHAR(255),
                  registration_last_connectivity_check TIMESTAMP,
                  CONSTRAINT uk_tenant_xtmhub_registration UNIQUE (tenant_id)
              );
              """);

      // Migrate existing XTM Hub registration from platform settings to the default tenant.
      // The HAVING clause ensures we only insert if a registration status actually exists.
      statement.execute(
          """
              INSERT INTO tenant_xtmhub_registrations (
                  registration_id, tenant_id, registration_token, registration_date,
                  registration_status, registration_user_id, registration_user_name,
                  registration_last_connectivity_check
              )
              SELECT
                  gen_random_uuid(),
                  '%s',
                  MAX(parameter_value) FILTER (WHERE parameter_key = 'xtm_hub_token'),
                  CAST(REPLACE(MAX(parameter_value) FILTER (WHERE parameter_key = 'xtm_hub_registration_date'), 'T', ' ') AS TIMESTAMP),
                  UPPER(MAX(parameter_value) FILTER (WHERE parameter_key = 'xtm_hub_registration_status')),
                  MAX(parameter_value) FILTER (WHERE parameter_key = 'xtm_hub_registration_user_id'),
                  MAX(parameter_value) FILTER (WHERE parameter_key = 'xtm_hub_registration_user_name'),
                  CAST(REPLACE(MAX(parameter_value) FILTER (WHERE parameter_key = 'xtm_hub_last_connectivity_check'), 'T', ' ') AS TIMESTAMP)
              FROM parameters
              WHERE parameter_key IN (
                  'xtm_hub_token', 'xtm_hub_registration_date', 'xtm_hub_registration_status',
                  'xtm_hub_registration_user_id', 'xtm_hub_registration_user_name',
                  'xtm_hub_last_connectivity_check'
              )
              HAVING NULLIF(MAX(parameter_value) FILTER (WHERE parameter_key = 'xtm_hub_registration_status'), '') IS NOT NULL;
              """
              .formatted(DEFAULT_TENANT_UUID));
    }
  }
}
