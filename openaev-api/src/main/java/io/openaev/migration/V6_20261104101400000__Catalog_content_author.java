package io.openaev.migration;

import java.sql.Statement;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

/** Catalogue content is authored by its publisher organization, Filigran when none is given. */
@Component
public class V6_20261104101400000__Catalog_content_author extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    try (Statement statement = context.getConnection().createStatement()) {
      statement.execute(
          """
          UPDATE catalog_items SET catalog_item_author = 'Filigran'
          WHERE catalog_item_author IS NULL OR btrim(catalog_item_author) = '';
          """);
      statement.execute(
          """
          CREATE TEMP TABLE catalog_authors ON COMMIT DROP AS
          SELECT p.payload_id AS content_id, 'PAYLOAD' AS content_type, p.tenant_id,
                 ci.catalog_item_author AS author
          FROM payloads p
          JOIN payload_identities i ON i.identity_id = p.identity_id AND i.managed
          JOIN catalog_items ci ON ci.tenant_id = p.tenant_id
            AND ci.catalog_item_type = 'PAYLOAD'
            AND ci.catalog_item_repository = i.payload_repository
            AND ci.catalog_item_external_id = i.payload_external_id
            AND ci.catalog_item_version = p.payload_version
          WHERE p.payload_author_organization IS NULL
          UNION
          SELECT DISTINCT ON (s.scenario_id) s.scenario_id, 'SCENARIO', s.tenant_id,
                 ci.catalog_item_author
          FROM scenarios s
          JOIN catalog_items ci ON ci.tenant_id = s.tenant_id
            AND ci.catalog_item_type = 'SCENARIO'
            AND ci.catalog_item_repository = s.scenario_catalog_repository
            AND ci.catalog_item_external_id = s.scenario_catalog_id
          WHERE s.scenario_author_organization IS NULL AND s.scenario_author_user IS NULL;
          """);
      statement.execute(
          """
          INSERT INTO organizations (organization_id, organization_name, tenant_id)
          SELECT gen_random_uuid()::text, a.author, a.tenant_id
          FROM (SELECT DISTINCT author, tenant_id FROM catalog_authors) a
          WHERE NOT EXISTS (
            SELECT 1 FROM organizations o
            WHERE lower(o.organization_name) = lower(a.author) AND o.tenant_id = a.tenant_id);
          """);
      statement.execute(
          """
          UPDATE payloads p SET payload_author_user = NULL, payload_author_team = NULL,
            payload_author_organization = (
              SELECT min(o.organization_id) FROM organizations o
              WHERE lower(o.organization_name) = lower(a.author) AND o.tenant_id = a.tenant_id)
          FROM catalog_authors a
          WHERE a.content_type = 'PAYLOAD' AND a.content_id = p.payload_id;
          """);
      statement.execute(
          """
          UPDATE scenarios s SET scenario_author_organization = (
              SELECT min(o.organization_id) FROM organizations o
              WHERE lower(o.organization_name) = lower(a.author) AND o.tenant_id = a.tenant_id)
          FROM catalog_authors a
          WHERE a.content_type = 'SCENARIO' AND a.content_id = s.scenario_id;
          """);
    }
  }
}
