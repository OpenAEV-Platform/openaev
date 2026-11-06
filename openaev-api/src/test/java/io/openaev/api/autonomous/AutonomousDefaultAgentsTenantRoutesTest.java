package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.repository.SettingRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.xtmone.XtmOneClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The default additional agents of the autonomous run are a tenant row of {@code parameters}. Both
 * routes of the endpoint (the tenant path and the non-prefixed one with {@code X-Tenant-Ids}) read
 * and write the row of the tenant the request's scope names, never the ambient default tenant; a
 * caller of two tenants who names none is refused.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("autonomous default agents resolve their tenant from the request scope")
class AutonomousDefaultAgentsTenantRoutesTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String PATH = "/default-agents";
  private static final String SCOPED = TENANT_PREFIX + "/autonomous-runs" + PATH;
  private static final String PLAIN = AutonomousRunApi.AUTONOMOUS_URI + PATH;
  private static final String BODY = "{\"agent_ids\":[\"agent-of-b\"],\"agent_modes\":{}}";
  private static final String KEY = TenantSettingKeys.AUTONOMOUS_ADDITIONAL_AGENTS.key();

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private SettingRepository settingRepository;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @MockitoBean private XtmOneClient xtmOneClient;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void createTwoTenantsNeitherOfThemTheDefault() throws Exception {
    tenantA = tenantHelper.createTenantWithCurrentUser("default-agents-a").getId();
    tenantB = tenantHelper.createTenantWithCurrentUser("default-agents-b").getId();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private String storedValue(String tenantId) {
    entityManager.flush();
    entityManager.clear();
    return settingRepository
        .findByKeyAndTenantId(KEY, tenantId)
        .map(Setting::getValue)
        .orElse(null);
  }

  @Nested
  @DisplayName("tenant path")
  class TenantPath {

    @Test
    @DisplayName("given_tenantBsPath_should_storeAndReadTenantBsRow")
    void given_tenantBsPath_should_storeAndReadTenantBsRow() throws Exception {
      // Act
      mvc.perform(
              put(SCOPED.replace("{tenantId}", tenantB))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY)
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertThat(storedValue(tenantB)).contains("agent-of-b");
      assertThat(storedValue(Tenant.DEFAULT_TENANT_UUID)).isNull();
      assertThat(storedValue(tenantA)).isNull();
    }
  }

  @Nested
  @DisplayName("X-Tenant-Ids header route")
  class HeaderRoute {

    @Test
    @DisplayName("given_headerNamingB_should_storeTenantBsRowNotTheDefaultTenants")
    void given_headerNamingB_should_storeTenantBsRowNotTheDefaultTenants() throws Exception {
      // Arrange: production reaches the non-prefixed route with an empty thread-local
      TenantContext.clearCurrentTenant();
      assertThat(TenantContext.hasCurrentTenant()).isFalse();

      // Act
      mvc.perform(
              put(PLAIN)
                  .header("X-Tenant-Ids", tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY)
                  .with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertThat(storedValue(tenantB)).contains("agent-of-b");
      assertThat(storedValue(Tenant.DEFAULT_TENANT_UUID)).isNull();
    }

    @Test
    @DisplayName("given_headerNamingB_should_readTenantBsRow")
    void given_headerNamingB_should_readTenantBsRow() throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();
      Setting row = new Setting(KEY, "[\"agent-of-b\"]");
      row.setTenant(entityManager.getReference(Tenant.class, tenantB));
      settingRepository.save(row);
      entityManager.flush();

      // Act + Assert
      String body =
          mvc.perform(get(PLAIN).header("X-Tenant-Ids", tenantB).accept(MediaType.APPLICATION_JSON))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(body).contains("agent-of-b");
    }

    @Test
    @DisplayName("given_twoTenantsAndNoSelector_should_refuseTheWriteAndCreateNoRow")
    void given_twoTenantsAndNoSelector_should_refuseTheWriteAndCreateNoRow() throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();

      // Act + Assert: the caller belongs to A and B but not to the default tenant
      mvc.perform(put(PLAIN).contentType(MediaType.APPLICATION_JSON).content(BODY).with(csrf()))
          .andExpect(status().isBadRequest());
      assertThat(storedValue(Tenant.DEFAULT_TENANT_UUID)).isNull();
      assertThat(storedValue(tenantA)).isNull();
      assertThat(storedValue(tenantB)).isNull();
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given_aCallerOfTheDefaultTenantAndNoSelector_should_storeTheDefaultTenantsRow")
    void given_aCallerOfTheDefaultTenantAndNoSelector_should_storeTheDefaultTenantsRow()
        throws Exception {
      // Arrange: the caller belongs to A, B and the default tenant, and names none
      TenantContext.clearCurrentTenant();

      // Act
      mvc.perform(put(PLAIN).contentType(MediaType.APPLICATION_JSON).content(BODY).with(csrf()))
          .andExpect(status().isOk());

      // Assert
      assertThat(storedValue(Tenant.DEFAULT_TENANT_UUID)).contains("agent-of-b");
      assertThat(storedValue(tenantA)).isNull();
      assertThat(storedValue(tenantB)).isNull();
    }

    @Test
    @DisplayName("given_aHeaderNamingTwoTenants_should_refuseTheWriteAndCreateNoRow")
    void given_aHeaderNamingTwoTenants_should_refuseTheWriteAndCreateNoRow() throws Exception {
      // Arrange
      TenantContext.clearCurrentTenant();

      // Act + Assert
      mvc.perform(
              put(PLAIN)
                  .header("X-Tenant-Ids", tenantA + "," + tenantB)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(BODY)
                  .with(csrf()))
          .andExpect(status().isBadRequest());
      assertThat(storedValue(tenantA)).isNull();
      assertThat(storedValue(tenantB)).isNull();
    }
  }
}
