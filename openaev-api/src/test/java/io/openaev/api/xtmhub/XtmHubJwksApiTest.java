package io.openaev.api.xtmhub;

import static io.openaev.api.xtmhub.XtmHubApi.TENANT_XTMHUB_JWKS_URI;
import static io.openaev.database.model.SettingKeys.XTM_HUB_KEY_VERSION;
import static io.openaev.utils.fixtures.tenants.TenantFixture.getTenant;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.config.ProductionActiveTables;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Setting;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.SettingRepository;
import io.openaev.database.repository.TenantRepository;
import io.openaev.service.tenants.TenantService;
import io.openaev.utils.fixtures.tenants.TenantComposer;
import io.openaev.utilstest.WithoutTenantScope;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * XTM Hub fetches a tenant's public key with no credentials, with the production active tables: the
 * key version is a platform row of {@code parameters}, read with no tenant scope.
 */
@WithoutTenantScope
@DisplayName("XTM Hub JWKS endpoint")
class XtmHubJwksApiTest extends IntegrationTest {

  @DynamicPropertySource
  static void activeTables(DynamicPropertyRegistry registry) {
    registry.add(
        "openaev.tenant.active-tables", () -> String.join(",", ProductionActiveTables.names()));
  }

  @Autowired private MockMvc mvc;
  @Autowired private SettingRepository settingRepository;
  @Autowired private TenantScopedTransaction tenantScopedTransaction;
  @Autowired private TenantComposer tenantComposer;
  @Autowired private TenantService tenantService;
  @Autowired private TenantRepository tenantRepository;

  private Optional<String> previousVersion;

  @BeforeEach
  void rememberVersion() {
    previousVersion =
        settingRepository
            .findByKeyAndTenantIsNull(XTM_HUB_KEY_VERSION.key())
            .map(Setting::getValue);
  }

  @AfterEach
  void restoreVersion() {
    storeVersion(previousVersion);
  }

  private void storeVersion(Optional<String> version) {
    tenantScopedTransaction.executeAsPlatformWrite(
        TxCtx.forTenant(Tenant.DEFAULT_TENANT_UUID),
        () -> {
          Optional<Setting> current =
              settingRepository.findByKeyAndTenantIsNull(XTM_HUB_KEY_VERSION.key());
          if (version.isEmpty()) {
            current.ifPresent(settingRepository::delete);
            return;
          }
          Setting setting =
              current.orElseGet(() -> new Setting(XTM_HUB_KEY_VERSION.key(), version.get()));
          setting.setValue(version.get());
          settingRepository.save(setting);
        });
  }

  private String fetchJwks(String tenantId) throws Exception {
    return mvc.perform(get(TENANT_XTMHUB_JWKS_URI, tenantId).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Nested
  @DisplayName("Without credentials")
  class WithoutCredentials {

    @Test
    @DisplayName("serves the public key of the tenant only")
    void given_anonymousCaller_should_servePublicKeyOnly() throws Exception {
      // Act
      String response = fetchJwks(Tenant.DEFAULT_TENANT_UUID);

      // Assert
      assertThatJson(response).node("keys").isArray().hasSize(1);
      assertThatJson(response)
          .node("keys[0]")
          .isObject()
          .containsOnlyKeys("kty", "crv", "alg", "use", "kid", "x");
      assertThatJson(response).node("keys[0].kty").isEqualTo("OKP");
      assertThatJson(response).node("keys[0].crv").isEqualTo("Ed25519");
    }

    @Test
    @DisplayName("serves the key of the stored version")
    void given_storedVersion_should_serveThatVersionsKey() throws Exception {
      // Arrange
      storeVersion(Optional.of("1"));
      String versionOneKid = JsonPath.read(fetchJwks(Tenant.DEFAULT_TENANT_UUID), "$.keys[0].kid");
      storeVersion(Optional.of("2"));

      // Act
      String versionTwoKid = JsonPath.read(fetchJwks(Tenant.DEFAULT_TENANT_UUID), "$.keys[0].kid");

      // Assert
      assertThat(versionTwoKid).isNotEqualTo(versionOneKid);
    }

    @Test
    @DisplayName("gives no key to an unknown tenant")
    void given_unknownTenant_should_refuse() throws Exception {
      // Act & Assert
      mvc.perform(get(TENANT_XTMHUB_JWKS_URI, "no-such-tenant").accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("gives no key to a deleted tenant")
    void given_deletedTenant_should_refuse() throws Exception {
      // Arrange
      Tenant tenant =
          tenantComposer.forTenant(getTenant(UUID.randomUUID().toString())).persist().get();
      try {
        fetchJwks(tenant.getId());
        tenantService.softDelete(tenant.getId());

        // Act & Assert
        mvc.perform(get(TENANT_XTMHUB_JWKS_URI, tenant.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
      } finally {
        tenantRepository.deleteById(tenant.getId());
      }
    }
  }
}
