package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/**
 * Adds the {@code custom_domains} table (customer-owned hostnames used to serve phishing landing
 * pages under a branded URL) and the nullable FK from a phishing landing page to the custom domain
 * it should be served on. Additive, lock-light, idempotent DDL only: {@code CREATE TABLE/INDEX IF
 * NOT EXISTS}, a nullable {@code ADD COLUMN IF NOT EXISTS} (metadata-only on PG 11+), and a guarded
 * FK add.
 */
@Component
public class V6_20260809170000000__Add_custom_domains extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS custom_domains (
              custom_domain_id                 VARCHAR(255) NOT NULL CONSTRAINT custom_domains_pkey PRIMARY KEY,
              custom_domain_hostname           VARCHAR(255) NOT NULL,
              custom_domain_status             VARCHAR(255) NOT NULL DEFAULT 'PENDING',
              custom_domain_verification_token VARCHAR(255) NOT NULL,
              custom_domain_verified_at        TIMESTAMP,
              custom_domain_last_checked_at    TIMESTAMP,
              custom_domain_last_error         TEXT,
              custom_domain_created_at         TIMESTAMP NOT NULL DEFAULT now(),
              custom_domain_updated_at         TIMESTAMP NOT NULL DEFAULT now(),
              tenant_id                        VARCHAR(255) NOT NULL CONSTRAINT custom_domains_tenant_fk REFERENCES tenants (tenant_id) ON DELETE CASCADE
          )
          """);

      // A hostname maps to exactly one tenant so an inbound public request is never ambiguous.
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS idx_custom_domains_hostname_uq ON custom_domains (lower(custom_domain_hostname))");
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_custom_domains_tenant_id ON custom_domains (tenant_id)");

      // Nullable FK: a landing page optionally served on a verified custom domain (SET NULL so
      // deleting a domain simply reverts its pages to the platform domain).
      statement.execute(
          "ALTER TABLE phishing_landing_pages "
              + "ADD COLUMN IF NOT EXISTS phishing_landing_page_custom_domain VARCHAR(255)");
      statement.execute(
          """
          DO $$
          BEGIN
              IF NOT EXISTS (
                  SELECT 1 FROM pg_constraint WHERE conname = 'phishing_landing_pages_custom_domain_fk'
              ) THEN
                  ALTER TABLE phishing_landing_pages
                      ADD CONSTRAINT phishing_landing_pages_custom_domain_fk
                      FOREIGN KEY (phishing_landing_page_custom_domain)
                      REFERENCES custom_domains (custom_domain_id) ON DELETE SET NULL;
              END IF;
          END $$;
          """);
      statement.execute(
          "CREATE INDEX IF NOT EXISTS idx_phishing_landing_pages_custom_domain "
              + "ON phishing_landing_pages (phishing_landing_page_custom_domain)");
    }
  }
}
