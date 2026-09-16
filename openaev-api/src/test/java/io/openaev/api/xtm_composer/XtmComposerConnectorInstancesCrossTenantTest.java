package io.openaev.api.xtm_composer;

import static io.openaev.api.xtm_composer.XtmComposerApi.TENANT_XTMCOMPOSER_URI;
import static io.openaev.api.xtm_composer.XtmComposerApi.XTMCOMPOSER_URI;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_ID;
import static io.openaev.database.model.SettingKeys.XTM_COMPOSER_VERSION;
import static io.openaev.utils.fixtures.CatalogConnectorFixture.createDefaultCatalogConnectorManagedByXtmComposer;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createConnectorInstanceConfiguration;
import static io.openaev.utils.fixtures.ConnectorInstanceFixture.createDefaultConnectorInstance;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.CatalogConnector;
import io.openaev.database.model.ConnectorInstancePersisted;
import io.openaev.service.PlatformSettingsService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.CatalogConnectorComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceComposer;
import io.openaev.utils.fixtures.composers.ConnectorInstanceConfigurationComposer;
import io.openaev.utils.mockUser.WithMockUser;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end proof that {@link XtmComposerApi#getAllConnectorInstances} - the only caller of {@code
 * findConnectorInstancesManagedByComposer} anywhere in the codebase (no background job reaches it)
 * - is deliberately NOT tenant-scoped: it always returns every managed connector instance across
 * ALL tenants, regardless of which route is used (plain, or tenant-prefixed with any tenant path
 * segment). The XTM Composer is a cross-tenant caller by design (one composer instance manages
 * connector instances across every tenant), and it never supplies a tenant selector in practice
 * (confirmed by production request logs: it only ever calls the plain, non-tenant-prefixed {@code
 * XTMCOMPOSER_URI} route). See the endpoint's javadoc for the full rationale, including why forcing
 * a {@code TxCtx}/tenant selector here would be wrong.
 *
 * <p>The XTM Composer registration itself ({@code XTM_COMPOSER_ID} et al.) is a single
 * platform-level row shared by every tenant (see {@link PlatformSettingsService#saveSettings},
 * backed by the dual-scope {@code Setting} entity with a null {@code tenant_id}).
 *
 * <p>Seeding goes through the composers (not a raw {@code INSERT}) because the entity is still on
 * v1 at this stage of the activation, same rationale as {@code ConnectorInstanceHttpIsolationTest}.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("XTM Composer connector-instances list: deliberately cross-tenant")
class XtmComposerConnectorInstancesCrossTenantTest extends IntegrationTest {

  private static final String COMPOSER_ID = "xtm-composer-cross-tenant-test";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private PlatformSettingsService platformSettingsService;
  @Autowired private CatalogConnectorComposer catalogConnectorComposer;
  @Autowired private ConnectorInstanceComposer connectorInstanceComposer;
  @Autowired private ConnectorInstanceConfigurationComposer connectorInstanceConfigurationComposer;

  private String tenantA;
  private String tenantB;
  private String instanceA;
  private String instanceB;

  @BeforeEach
  void seedTwoTenantsWithOneManagedInstanceEachAndOnePlatformComposer() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("xtm-cross-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("xtm-cross-b").getId();

    // The XTM Composer registration is genuinely platform-level: one row, no tenant context
    // needed to write it (see the class javadoc).
    Map<String, String> composerSettings = new HashMap<>();
    composerSettings.put(XTM_COMPOSER_ID.key(), COMPOSER_ID);
    composerSettings.put(XTM_COMPOSER_VERSION.key(), "composer-version-test");
    platformSettingsService.saveSettings(composerSettings);

    instanceA = seedManagedInstance(tenantA, "xtm-cross-connector-a");
    instanceB = seedManagedInstance(tenantB, "xtm-cross-connector-b");
  }

  private String seedManagedInstance(String tenantId, String connectorName) throws Exception {
    String previousTenantId = TenantContext.getCurrentTenant();
    TenantContext.setCurrentTenant(tenantId);
    try {
      CatalogConnector catalogConnector =
          catalogConnectorComposer
              .forCatalogConnector(createDefaultCatalogConnectorManagedByXtmComposer(connectorName))
              .persist()
              .get();
      ConnectorInstancePersisted instance =
          connectorInstanceComposer
              .forConnectorInstance(createDefaultConnectorInstance())
              .withCatalogConnector(catalogConnectorComposer.forCatalogConnector(catalogConnector))
              .withConnectorInstanceConfiguration(
                  connectorInstanceConfigurationComposer.forConnectorInstanceConfiguration(
                      createConnectorInstanceConfiguration("key", "value")))
              .persist()
              .get();
      entityManager.flush();
      return instance.getId();
    } finally {
      TenantContext.setCurrentTenant(previousTenantId);
    }
  }

  @Test
  @DisplayName("under the plain, non-tenant-prefixed route: both tenants' instances are listed")
  void underPlainRouteBothTenantsInstancesAreListed() throws Exception {
    String response =
        mvc.perform(
                get(XTMCOMPOSER_URI + "/{xtmComposerId}/connector-instances", COMPOSER_ID)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThatJson(response)
        .inPath("[*].connector_instance_id")
        .isArray()
        .containsExactlyInAnyOrder(instanceA, instanceB);
  }

  @Test
  @DisplayName(
      "under tenant A's path: still both tenants' instances are listed - the tenant path segment"
          + " does not narrow this endpoint's scope")
  void underTenantAPathBothTenantsInstancesAreListed() throws Exception {
    String response =
        mvc.perform(
                get(
                    TENANT_XTMCOMPOSER_URI + "/{xtmComposerId}/connector-instances",
                    tenantA,
                    COMPOSER_ID))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThatJson(response)
        .inPath("[*].connector_instance_id")
        .isArray()
        .containsExactlyInAnyOrder(instanceA, instanceB);
  }
}
