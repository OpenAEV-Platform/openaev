package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

@Component
public class V6_20260727154533443__Add_secrets_provider_connector_type_and_secret_table
    extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
                    ALTER TYPE connector_type
                    ADD VALUE IF NOT EXISTS 'SECRETS_PROVIDER'
                    AFTER 'EXECUTOR'
                    """);

      statement.execute(
          """
                  CREATE TABLE IF NOT EXISTS secrets (
                      secret_id          VARCHAR(255) NOT NULL CONSTRAINT secrets_pkey PRIMARY KEY,
                      secret_type        VARCHAR(255) NOT NULL,
                      secret_created_at  TIMESTAMP NOT NULL DEFAULT now(),
                      secret_updated_at  TIMESTAMP NOT NULL DEFAULT now(),
                      tenant_id          VARCHAR(255) NOT NULL CONSTRAINT secrets_tenant_fk REFERENCES tenants (tenant_id) ON DELETE CASCADE,
                      secret_username    VARCHAR(255),
                      secret_password    TEXT,
                      secret_hash_algorithm VARCHAR(255),
                      secret_hash        TEXT
                  )
                  """);

      statement.execute("CREATE INDEX IF NOT EXISTS idx_secrets_tenant_id ON secrets (tenant_id)");

      statement.execute(
          """
                  CREATE TABLE IF NOT EXISTS secret_references (
                      secret_reference_id                       VARCHAR(255) NOT NULL CONSTRAINT secret_references_pkey PRIMARY KEY,
                      secret_reference_type                     VARCHAR(255) NOT NULL,
                      secret_reference_name                     VARCHAR(255) NOT NULL,
                      secret_reference_description              TEXT,
                      secret_reference_connector_instance_id    VARCHAR(255) NOT NULL,
                      secret_reference_location                 VARCHAR(255),
                      secret_reference_status                   VARCHAR(255) NOT NULL DEFAULT 'ACTIVE',
                      secret_reference_created_by               VARCHAR(255) CONSTRAINT secret_references_created_by_fk REFERENCES users (user_id) ON DELETE SET NULL,
                      secret_reference_created_at               TIMESTAMP NOT NULL DEFAULT now(),
                      secret_reference_updated_at               TIMESTAMP NOT NULL DEFAULT now(),
                      secret_reference_last_verified_at         TIMESTAMP,
                      tenant_id                                 VARCHAR(255) NOT NULL CONSTRAINT secret_references_tenant_fk REFERENCES tenants (tenant_id) ON DELETE CASCADE,
                      secret_reference_credential_type          VARCHAR(255),
                      secret_reference_credential_auth_method   VARCHAR(255),
                      CONSTRAINT secret_references_name_tenant_uq UNIQUE (secret_reference_name, tenant_id)
                  )
                  """);

      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_secret_references_tenant_id ON secret_references (tenant_id)");

      statement.execute(
          """
                  CREATE TABLE IF NOT EXISTS secret_reference_tags (
                      secret_reference_id  VARCHAR(255) NOT NULL
                          CONSTRAINT secret_reference_tags_ref_fk
                              REFERENCES secret_references (secret_reference_id) ON DELETE CASCADE,
                      tag_id               VARCHAR(255) NOT NULL
                          CONSTRAINT secret_reference_tags_tag_fk
                              REFERENCES tags (tag_id) ON DELETE CASCADE,
                      CONSTRAINT secret_reference_tags_pkey
                          PRIMARY KEY (secret_reference_id, tag_id)
                  )
                  """);
      statement.execute(
          """
              CREATE INDEX IF NOT EXISTS idx_secret_reference_tags_tag_id ON secret_reference_tags (tag_id)
              """);
    }
  }
}
