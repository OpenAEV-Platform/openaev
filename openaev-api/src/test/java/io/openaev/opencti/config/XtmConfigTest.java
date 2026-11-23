package io.openaev.opencti.config;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.opencrq.config.OpenCRQConfig;
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
      xtmConfig.mapper = new ObjectMapper();
      xtmConfig.setOpencti(
          Map.of("tenant-a", Map.of("enable", true, "url", "http://tenant-a", "token", "token-a")));

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
      xtmConfig.mapper = new ObjectMapper();
      xtmConfig.setOpencti(
          Map.of(
              "enable", "true",
              "url", "http://legacy-opencti",
              "token", "legacy-token",
              "api-url", "http://legacy-opencti/graphql"));

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

  @Nested
  @DisplayName("OpenCRQ config normalization")
  class OpenCRQConfigNormalization {

    @Test
    void given_tenantScopedProperties_should_keepTenantScopedConfiguration() {
      XtmConfig xtmConfig = new XtmConfig();
      xtmConfig.mapper = new ObjectMapper();
      xtmConfig.setOpencrq(Map.of("tenant-a", Map.of("url", "http://ocrq-a", "token", "ogrc_a")));

      Map<String, OpenCRQConfig> opencrq = xtmConfig.getOpencrq();

      assertThat(opencrq).containsOnlyKeys("tenant-a");
      assertThat(opencrq.get("tenant-a").getUrl()).isEqualTo("http://ocrq-a");
      // url and token without an enable flag: connected by default.
      assertThat(opencrq.get("tenant-a").isUsable()).isTrue();
    }

    @Test
    void given_legacyPropertiesWithoutTenant_should_fallbackToDefaultTenantConfiguration() {
      XtmConfig xtmConfig = new XtmConfig();
      xtmConfig.mapper = new ObjectMapper();
      xtmConfig.setOpencrq(Map.of("url", "http://legacy-ocrq", "token", "ogrc_legacy"));

      Map<String, OpenCRQConfig> opencrq = xtmConfig.getOpencrq();

      assertThat(opencrq).containsOnlyKeys(DEFAULT_TENANT_UUID);
      OpenCRQConfig config = opencrq.get(DEFAULT_TENANT_UUID);
      assertThat(config.getUrl()).isEqualTo("http://legacy-ocrq");
      assertThat(config.getApiUrl()).isEqualTo("http://legacy-ocrq/trpc");
      assertThat(config.isUsable()).isTrue();
    }

    @Test
    void given_disabledConnection_should_notBeUsable() {
      XtmConfig xtmConfig = new XtmConfig();
      xtmConfig.mapper = new ObjectMapper();
      xtmConfig.setOpencrq(
          Map.of("tenant-a", Map.of("enable", "false", "url", "http://ocrq", "token", "ogrc")));

      assertThat(xtmConfig.getOpencrq().get("tenant-a").isUsable()).isFalse();
    }
  }
}
