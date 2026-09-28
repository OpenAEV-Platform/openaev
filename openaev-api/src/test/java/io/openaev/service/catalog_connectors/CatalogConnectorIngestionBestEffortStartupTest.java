package io.openaev.service.catalog_connectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The startup catalog ingestion cleans up outdated connector instance configurations once per
 * tenant. If one tenant's cleanup fails, the platform startup must still complete: the failure is
 * logged and the other tenants are cleaned. This pins that best-effort contract on the real stack.
 *
 * <p>The failure is injected the way the runtime can genuinely produce it: a transient database
 * error while reading one tenant's instance configurations ({@code findByConnectorInstanceId}). The
 * scope, both tenants and both instances are real; only that single read is made to fail.
 *
 * <p>Deliberately NOT {@code @Transactional}: the ingestion runs the cleanup through the background
 * transaction primitive, which refuses to open inside an active transaction.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=connector_instances")
@DisplayName("Catalog connector startup cleanup: best effort across tenants")
class CatalogConnectorIngestionBestEffortStartupTest extends IntegrationTest {

  private static final String SLUG = "catalog-startup-besteffort-connector";
  private static final String TITLE = "Catalog startup best-effort connector";

  @Autowired private CatalogConnectorIngestionService catalogConnectorIngestionService;
  @Autowired private CatalogConnectorRepository catalogConnectorRepository;
  @Autowired private ConnectorInstanceRepository connectorInstanceRepository;

  @MockitoSpyBean
  private ConnectorInstanceConfigurationRepository connectorInstanceConfigurationRepository;

  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private String catalogConnectorId;
  private String tenantFailing;
  private String tenantHealthy;
  private String instanceFailing;
  private String instanceHealthy;

  @BeforeEach
  void seedTwoTenantsWithOutdatedConfigurations() {
    jdbc = new JdbcTemplate(dataSource);
    // Defensive: a crashed earlier run could leave the slug behind (unique constraint).
    jdbc.update("DELETE FROM catalog_connectors WHERE catalog_connector_slug = ?", SLUG);

    CatalogConnector connector = new CatalogConnector();
    connector.setTitle(TITLE);
    connector.setSlug(SLUG);
    catalogConnectorId = catalogConnectorRepository.save(connector).getId();

    tenantFailing = seedTenant("catalog-besteffort-failing-" + UUID.randomUUID());
    tenantHealthy = seedTenant("catalog-besteffort-healthy-" + UUID.randomUUID());

    instanceFailing = seedInstanceWithOutdatedConfiguration(tenantFailing);
    instanceHealthy = seedInstanceWithOutdatedConfiguration(tenantHealthy);
  }

  @AfterEach
  void cleanup() {
    for (String instanceId : List.of(instanceFailing, instanceHealthy)) {
      jdbc.update(
          "DELETE FROM connector_instance_configurations WHERE connector_instance_id = ?",
          instanceId);
      jdbc.update("DELETE FROM connector_instances WHERE connector_instance_id = ?", instanceId);
    }
    jdbc.update(
        "DELETE FROM catalog_connectors WHERE catalog_connector_id = ?", catalogConnectorId);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantFailing);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantHealthy);
  }

  @Test
  @DisplayName("one tenant's failing cleanup does not abort startup or the other tenants")
  void oneTenantCleanupFailureDoesNotAbortStartup() throws Exception {
    // The read of the failing tenant's instance configurations throws, as a transient DB error
    // would. The instance is only visible in its own tenant scope, so this fires solely during
    // that tenant's cleanup and leaves the healthy tenant untouched.
    doThrow(new RuntimeException("simulated transient database error"))
        .when(connectorInstanceConfigurationRepository)
        .findByConnectorInstanceId(instanceFailing);

    // The ingested schema defines KEEP_KEY only, so STALE_KEY must be removed where cleanup runs.
    JsonNode catalog = catalogDefiningOnly("KEEP_KEY");

    assertThatCode(() -> catalogConnectorIngestionService.extractCatalog(catalog))
        .as("a single tenant's failing cleanup must not abort the whole startup ingestion")
        .doesNotThrowAnyException();

    assertThat(remainingConfigurationKeys(instanceHealthy))
        .as("the healthy tenant is still cleaned even though another tenant's cleanup failed")
        .containsExactly("KEEP_KEY");

    assertThat(remainingConfigurationKeys(instanceFailing))
        .as("the failing tenant is left as it was: best effort, its cleanup did not run")
        .containsExactly("KEEP_KEY", "STALE_KEY");
  }

  private String seedInstanceWithOutdatedConfiguration(String tenantId) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          ConnectorInstancePersisted instance = new ConnectorInstancePersisted();
          instance.setCatalogConnector(
              entityManager.getReference(CatalogConnector.class, catalogConnectorId));
          instance.setCurrentStatus(ConnectorInstance.CURRENT_STATUS_TYPE.stopped);
          instance.setRequestedStatus(ConnectorInstance.REQUESTED_STATUS_TYPE.stopping);
          instance.setSource(ConnectorInstance.SOURCE.OTHER);
          instance.setTenant(new Tenant(tenantId));
          String id = connectorInstanceRepository.save(instance).getId();

          connectorInstanceConfigurationRepository.save(
              configuration(instance, "KEEP_KEY", "keep"));
          connectorInstanceConfigurationRepository.save(
              configuration(instance, "STALE_KEY", "stale"));
          return id;
        });
  }

  private List<String> remainingConfigurationKeys(String instanceId) {
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
