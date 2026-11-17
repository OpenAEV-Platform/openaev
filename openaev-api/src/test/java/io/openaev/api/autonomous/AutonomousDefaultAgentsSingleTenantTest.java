package io.openaev.api.autonomous;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Setting;
import io.openaev.database.model.TenantSettingKeys;
import io.openaev.database.repository.SettingRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.xtmone.XtmOneClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * A caller of one tenant reaches the default agents endpoint on both routes with no selector: the
 * non-prefixed route with no {@code X-Tenant-Ids} resolves to that single tenant (the fallback
 * selector of a {@code @RequireTenantSelector} context), the same row the tenant path writes. The
 * multi-tenant cases are in {@link AutonomousDefaultAgentsTenantRoutesTest}.
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("autonomous default agents, a caller of a single tenant")
class AutonomousDefaultAgentsSingleTenantTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String BODY = "{\"agent_ids\":[\"agent-of-s\"],\"agent_modes\":{}}";
  private static final String KEY = TenantSettingKeys.AUTONOMOUS_ADDITIONAL_AGENTS.key();

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private SettingRepository settingRepository;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @MockitoBean private XtmOneClient xtmOneClient;

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

  @Test
  @DisplayName("given_noSelectorOnTheNonPrefixedRoute_should_storeTheOnlyTenantsRow")
  void given_noSelectorOnTheNonPrefixedRoute_should_storeTheOnlyTenantsRow() throws Exception {
    // Arrange
    String tenant = tenantHelper.createTenantWithCurrentUser("default-agents-single").getId();
    TenantContext.clearCurrentTenant();

    // Act
    mvc.perform(
            put(AutonomousRunApi.AUTONOMOUS_URI + "/default-agents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .with(csrf()))
        .andExpect(status().isOk());

    // Assert
    assertThat(storedValue(tenant)).contains("agent-of-s");
  }

  @Test
  @DisplayName("given_theTenantPath_should_storeTheSameRow")
  void given_theTenantPath_should_storeTheSameRow() throws Exception {
    // Arrange
    String tenant = tenantHelper.createTenantWithCurrentUser("default-agents-single-path").getId();

    // Act
    mvc.perform(
            put((TENANT_PREFIX + "/autonomous-runs/default-agents").replace("{tenantId}", tenant))
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .with(csrf()))
        .andExpect(status().isOk());

    // Assert
    assertThat(storedValue(tenant)).contains("agent-of-s");
  }
}
