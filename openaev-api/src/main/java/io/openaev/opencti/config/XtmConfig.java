package io.openaev.opencti.config;

import io.openaev.database.model.Tenant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openaev.xtm")
public class XtmConfig {

  private Map<String, Object> opencti = new HashMap<>();

  /**
   * Supports both property styles: openaev.xtm.opencti.{tenantId}.* (new) and openaev.xtm.opencti.*
   * (legacy).
   */
  public Map<String, OpenCTIConfig> getOpencti() {
    if (opencti == null || opencti.isEmpty()) {
      return Map.of();
    }

    Map<String, OpenCTIConfig> normalized = new HashMap<>();
    OpenCTIConfig legacyConfig = new OpenCTIConfig();
    boolean hasLegacyConfig = false;

    for (Map.Entry<String, Object> entry : opencti.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (value instanceof Map<?, ?> tenantConfigMap) {
        normalized.put(key, mapToOpenCTIConfig(tenantConfigMap));
        continue;
      }

      String stringValue = Objects.toString(value, null);
      hasLegacyConfig |= applyLegacyField(legacyConfig, key, stringValue);
    }

    if (hasLegacyConfig) {
      normalized.putIfAbsent(Tenant.DEFAULT_TENANT_UUID, legacyConfig);
    }

    return normalized;
  }

  public void setOpencti(Map<String, Object> opencti) {
    this.opencti = opencti;
  }

  private OpenCTIConfig mapToOpenCTIConfig(Map<?, ?> input) {
    OpenCTIConfig config = new OpenCTIConfig();
    input.forEach(
        (rawKey, rawValue) -> {
          String key = Objects.toString(rawKey, "");
          String value = Objects.toString(rawValue, null);
          applyLegacyField(config, key, value);
        });
    return config;
  }

  private boolean applyLegacyField(OpenCTIConfig config, String field, String value) {
    return switch (field) {
      case "enable" -> {
        config.setEnable(value != null && Boolean.parseBoolean(value));
        yield true;
      }
      case "url" -> {
        config.setUrl(value);
        yield true;
      }
      case "token" -> {
        config.setToken(value);
        yield true;
      }
      case "api-url", "api_url", "apiUrl" -> {
        config.setApiUrl(value);
        yield true;
      }
      default -> false;
    };
  }
}
