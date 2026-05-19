package io.openaev.opencti.config;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class XtmConfigTest {

  @Nested
  @DisplayName("OpenCTI config normalization")
  class OpenCTIConfigNormalization {

    @Test
    void given_tenantScopedProperties_should_keepTenantScopedConfiguration() {
      // Arrange
      XtmConfig xtmConfig = new XtmConfig();
      xtmConfig.setOpencti(
          Map.of(
              "tenant-a",
              Map.of("enable", true, "url", "http://tenant-a", "token", "token-a")));

      // Act
      Map<String, OpenCTIConfig> opencti = xtmConfig.getOpencti();

      // Assert
      assertThat(opencti).containsKey("tenant-a");
      assertThat(opencti.get("tenant-a").getEnable()).isTrue();
      assertThat(opencti.get("tenant-a").getUrl()).isEqualTo("http://tenant-a");
      assertThat(opencti.get("tenant-a").getToken()).isEqualTo("token-a");
    }

    @Test
    void given_legacyPropertiesWithoutTenant_should_fallbackToDefaultTenantConfiguration() {
      // Arrange
      XtmConfig xtmConfig = new XtmConfig();
      xtmConfig.setOpencti(
          Map.of(
              "enable", "true",
              "url", "http://legacy-opencti",
              "token", "legacy-token",
              "api_url", "http://legacy-opencti/graphql"));

      // Act
      Map<String, OpenCTIConfig> opencti = xtmConfig.getOpencti();

      // Assert
      assertThat(opencti).containsKey(DEFAULT_TENANT_UUID);
      OpenCTIConfig defaultTenantConfig = opencti.get(DEFAULT_TENANT_UUID);
      assertThat(defaultTenantConfig.getEnable()).isTrue();
      assertThat(defaultTenantConfig.getUrl()).isEqualTo("http://legacy-opencti");
      assertThat(defaultTenantConfig.getToken()).isEqualTo("legacy-token");
      assertThat(defaultTenantConfig.getApiUrl()).isEqualTo("http://legacy-opencti/graphql");
    }
  }
}

