package io.openaev.api.autonomous;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller table of the default agents endpoints on the non-prefixed route when the request names
 * no tenant (no {@code X-Tenant-Ids}): which tenant row each shape of caller reads and writes, or
 * the refusal it gets. Each shape is pinned for the read and for the write.
 *
 * <table>
 *   <caption>No selector, no header</caption>
 *   <tr><th>Caller</th><th>GET</th><th>PUT</th></tr>
 *   <tr><td>one non-default tenant</td><td>that tenant's row</td><td>that tenant's row</td></tr>
 *   <tr><td>the default tenant and B</td><td>the default tenant's row</td><td>the default tenant's row</td></tr>
 *   <tr><td>B and C, not the default tenant</td><td>400 TENANT_SELECTOR_REQUIRED</td><td>400, no row</td></tr>
 *   <tr><td>no membership</td><td>400 TENANT_SELECTOR_REQUIRED</td><td>400, no row</td></tr>
 * </table>
 */
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("autonomous default agents, the callers that name no tenant")
class AutonomousDefaultAgentsNoSelectorCallersTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  private static final String PLAIN = AutonomousRunApi.AUTONOMOUS_URI + "/default-agents";
  private static final String KEY = TenantSettingKeys.AUTONOMOUS_ADDITIONAL_AGENTS.key();
  private static final String BODY = "{\"agent_ids\":[\"written\"],\"agent_modes\":{}}";

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private SettingRepository settingRepository;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;
  @MockitoBean private XtmOneClient xtmOneClient;

  @BeforeEach
  void startWithTheEmptyThreadLocalOfProduction() {
    TenantContext.clearCurrentTenant();
  }

  @AfterEach
  void clearAmbientTenant() {
    TenantContext.clearCurrentTenant();
  }

  private String createTenant(String name) throws Exception {
    String id = tenantHelper.createTenantWithCurrentUser(name).getId();
    TenantContext.clearCurrentTenant();
    assertThat(TenantContext.hasCurrentTenant()).isFalse();
    return id;
  }

  private void seed(String tenantId, String agent) {
    Setting row = new Setting(KEY, "[\"" + agent + "\"]");
    row.setTenant(entityManager.getReference(Tenant.class, tenantId));
    settingRepository.save(row);
    entityManager.flush();
  }

  private String storedValue(String tenantId) {
    entityManager.flush();
    entityManager.clear();
    return settingRepository
        .findByKeyAndTenantId(KEY, tenantId)
        .map(Setting::getValue)
        .orElse(null);
  }

  private ResultActions read() throws Exception {
    return mvc.perform(get(PLAIN).accept(MediaType.APPLICATION_JSON));
  }

  private ResultActions write() throws Exception {
    return mvc.perform(
        put(PLAIN).contentType(MediaType.APPLICATION_JSON).content(BODY).with(csrf()));
  }

  @Nested
  @DisplayName("a caller of one non-default tenant")
  class OneNonDefaultTenant {

    @Test
    @DisplayName("given_noSelector_should_readThatTenantsRow")
    void given_noSelector_should_readThatTenantsRow() throws Exception {
      // Arrange
      String tenantB = createTenant("no-selector-one-b");
      seed(tenantB, "agent-of-b");
      seed(Tenant.DEFAULT_TENANT_UUID, "agent-of-default");

      // Act + Assert
      String body =
          read().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      assertThat(body).contains("agent-of-b").doesNotContain("agent-of-default");
    }

    @Test
    @DisplayName("given_noSelector_should_writeThatTenantsRow")
    void given_noSelector_should_writeThatTenantsRow() throws Exception {
      // Arrange
      String tenantB = createTenant("no-selector-one-b-write");

      // Act
      write().andExpect(status().isOk());

      // Assert
      assertThat(storedValue(tenantB)).contains("written");
      assertThat(storedValue(Tenant.DEFAULT_TENANT_UUID)).isNull();
    }
  }

  @Nested
  @DisplayName("a caller of the default tenant and one other")
  class DefaultTenantAndOne {

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given_noSelector_should_readTheDefaultTenantsRow")
    void given_noSelector_should_readTheDefaultTenantsRow() throws Exception {
      // Arrange
      String tenantB = createTenant("no-selector-default-b");
      seed(tenantB, "agent-of-b");
      seed(Tenant.DEFAULT_TENANT_UUID, "agent-of-default");

      // Act + Assert
      String body =
          read().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
      assertThat(body).contains("agent-of-default").doesNotContain("agent-of-b");
    }

    @Test
    @WithMockUser(isAdmin = true, autoJoinDefaultTenant = true)
    @DisplayName("given_noSelector_should_writeTheDefaultTenantsRow")
    void given_noSelector_should_writeTheDefaultTenantsRow() throws Exception {
      // Arrange
      String tenantB = createTenant("no-selector-default-b-write");

      // Act
      write().andExpect(status().isOk());

      // Assert
      assertThat(storedValue(Tenant.DEFAULT_TENANT_UUID)).contains("written");
      assertThat(storedValue(tenantB)).isNull();
    }
  }

  @Nested
  @DisplayName("a caller of two tenants, neither of them the default one")
  class TwoTenantsWithoutTheDefaultOne {

    @Test
    @DisplayName("given_noSelector_should_refuseTheReadAsAMissingSelector")
    void given_noSelector_should_refuseTheReadAsAMissingSelector() throws Exception {
      // Arrange
      createTenant("no-selector-b");
      createTenant("no-selector-c");

      // Act + Assert
      read()
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("TENANT_SELECTOR_REQUIRED"));
    }

    @Test
    @DisplayName("given_noSelector_should_refuseTheWriteAndCreateNoRow")
    void given_noSelector_should_refuseTheWriteAndCreateNoRow() throws Exception {
      // Arrange
      String tenantB = createTenant("no-selector-b-write");
      String tenantC = createTenant("no-selector-c-write");

      // Act
      write().andExpect(status().isBadRequest());

      // Assert
      assertThat(storedValue(tenantB)).isNull();
      assertThat(storedValue(tenantC)).isNull();
      assertThat(storedValue(Tenant.DEFAULT_TENANT_UUID)).isNull();
    }
  }

  @Nested
  @DisplayName("a caller with no tenant membership")
  class NoMembership {

    @Test
    @DisplayName("given_noSelector_should_refuseTheReadAsAMissingSelector")
    void given_noSelector_should_refuseTheReadAsAMissingSelector() throws Exception {
      // Act + Assert
      read()
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("TENANT_SELECTOR_REQUIRED"));
    }

    @Test
    @DisplayName("given_noSelector_should_refuseTheWriteAndCreateNoRow")
    void given_noSelector_should_refuseTheWriteAndCreateNoRow() throws Exception {
      // Act
      write().andExpect(status().isBadRequest());

      // Assert
      assertThat(storedValue(Tenant.DEFAULT_TENANT_UUID)).isNull();
    }
  }
}
