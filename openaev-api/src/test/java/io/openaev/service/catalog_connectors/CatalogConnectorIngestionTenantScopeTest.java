package io.openaev.service.catalog_connectors;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ConnectorInstanceConfiguration;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.CatalogConnectorRepository;
import io.openaev.database.repository.ConnectorInstanceConfigurationRepository;
import io.openaev.database.repository.ConnectorInstanceRepository;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * The startup catalog ingestion cleans up outdated connector instance configurations. {@code
 * connector_instances} is tenant-scoped, so read without a scope it returns no row and the cleanup
 * silently does nothing. This pins the cleanup on the real stack: an outdated configuration of an
 * instance in a non-default tenant must be removed within that tenant's scope.
 *
 * <p>Deliberately NOT {@code @Transactional}: the ingestion runs the cleanup through the background
 * transaction primitive, which refuses to open inside an active transaction. Seeding and cleanup
 * run in auto-committed JDBC and one primitive-scoped write.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=connector_instances")
@DisplayName("Catalog connector startup cleanup: per-tenant scope")
class CatalogConnectorIngestionTenantScopeTest extends IntegrationTest {

  private static final String SLUG = "catalog-startup-cleanup-connector";
  private static final String TITLE = "Catalog startup cleanup connector";

  @Autowired private CatalogConnectorIngestionService catalogConnectorIngestionService;
  @Autowired private CatalogConnectorRepository catalogConnectorRepository;
  @Autowired private ConnectorInstanceRepository connectorInstanceRepository;

  @Autowired
  private ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String tenantB;
  private String catalogConnectorId;
  private String instanceId;

  @BeforeEach
  void seedNonDefaultTenantInstance() {
    jdbc = new JdbcTemplate(dataSource);
    // Defensive: a crashed earlier run could leave the slug behind (unique constraint).
    jdbc.update("DELETE FROM catalog_connectors WHERE catalog_connector_slug = ?", SLUG);

    tenantB = seedTenant("catalog-cleanup-b-" + UUID.randomUUID());

    CatalogConnector connector = new CatalogConnector();
    connector.setTitle(TITLE);
    connector.setSlug(SLUG);
    catalogConnectorId = catalogConnectorRepository.save(connector).getId();

    tenantTx.execute(
        TxCtx.forTenant(tenantB),
        () -> {
          ConnectorInstancePersisted instance = new ConnectorInstancePersisted();
          instance.setCatalogConnector(
              entityManager.getReference(CatalogConnector.class, catalogConnectorId));
          instance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
          instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
          instance.setSource(ConnectorInstance.SOURCE.OTHER);
          instance.setTenant(new Tenant(tenantB));
          instanceId = connectorInstanceRepository.save(instance).getId();

          connectorInstanceConfigurationRepository.save(
              configuration(instance, "KEEP_KEY", "keep"));
          connectorInstanceConfigurationRepository.save(
              configuration(instance, "STALE_KEY", "stale"));
        });
  }

  @AfterEach
  void cleanup() {
    jdbc.update(
        "DELETE FROM connector_instance_configurations WHERE connector_instance_id = ?",
        instanceId);
    jdbc.update("DELETE FROM connector_instances WHERE connector_instance_id = ?", instanceId);
    jdbc.update(
        "DELETE FROM catalog_connectors WHERE catalog_connector_id = ?", catalogConnectorId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantB);
  }

  @Test
  @DisplayName("an outdated configuration of a non-default tenant's instance is cleaned at startup")
  void cleanupRemovesOutdatedConfigurationInNonDefaultTenant() throws Exception {
    // The ingested schema defines KEEP_KEY only, so STALE_KEY is no longer part of the schema and
    // must be removed from the tenant B instance; KEEP_KEY stays.
    JsonNode catalog = catalogDefiningOnly("KEEP_KEY");

    List<CatalogConnector> saved = catalogConnectorIngestionService.extractCatalog(catalog);

    assertThat(saved).as("the catalog connector definition is still ingested").hasSize(1);
    assertThat(remainingConfigurationKeys())
        .as("the outdated STALE_KEY must be cleaned within the non-default tenant's scope")
        .containsExactly("KEEP_KEY");
  }

  private List<String> remainingConfigurationKeys() {
    return jdbc.queryForList(
        "SELECT connector_instance_configuration_key FROM connector_instance_configurations"
            + " WHERE connector_instance_id = ? ORDER BY connector_instance_configuration_key",
        String.class,
        instanceId);
  }

  private ConnectorInstanceConfiguration configuration(
      ConnectorInstancePersisted instance, String key, String value) {
    return ConnectorInstanceConfiguration.builder()
        .key(key)
        .value(TextNode.valueOf(value))
        .connectorInstance(instance)
        .isEncrypted(false)
        .build();
  }

  private JsonNode catalogDefiningOnly(String key) throws Exception {
    String json =
        """
        {
          "contracts": [
            {
              "title": "%s",
              "slug": "%s",
              "config_schema": {
                "properties": {
                  "%s": { "type": "string" }
                },
                "required": []
              }
            }
          ]
        }
        """
            .formatted(TITLE, SLUG, key);
    return new ObjectMapper().readTree(json);
  }

  private String seedTenant(String name) {
    String id = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name, tenant_created_at, tenant_updated_at)"
            + " VALUES (?, ?, now(), now())",
        id,
        name);
    return id;
  }
}
