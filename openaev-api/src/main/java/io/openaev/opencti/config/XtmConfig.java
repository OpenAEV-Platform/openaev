package io.openaev.opencti.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Tenant;
import io.openaev.opencrq.config.OpenCRQConfig;
import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openaev.xtm")
public class XtmConfig {

  @Setter private Map<String, Object> opencti = new HashMap<>();

  @Setter private Map<String, Object> opencrq = new HashMap<>();

  @Resource protected ObjectMapper mapper;

  /**
   * Supports both property styles: openaev.xtm.opencti.{tenantId}.* (new) and openaev.xtm.opencti.*
   * (legacy).
   */
  public Map<String, OpenCTIConfig> getOpencti() {
    if (opencti == null || opencti.isEmpty()) {
      return Map.of();
    }

    Map<String, OpenCTIConfig> normalized = new HashMap<>();
    Map<String, Object> legacyFields = new HashMap<>();

    for (Map.Entry<String, Object> entry : opencti.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (value instanceof Map<?, ?> tenantConfigMap) {
        normalized.put(key, mapper.convertValue(tenantConfigMap, OpenCTIConfig.class));
      } else {
        legacyFields.put(key, value);
      }
    }

    if (!legacyFields.isEmpty()) {
      normalized.put(
          Tenant.DEFAULT_TENANT_UUID, mapper.convertValue(legacyFields, OpenCTIConfig.class));
    }

    return normalized;
  }

  /**
   * The OpenCRQ connections, by tenant. Same two property styles as OpenCTI:
   * openaev.xtm.opencrq.{tenantId}.* and, for a single-tenant deployment, openaev.xtm.opencrq.*
   * (the default tenant). A tenant-scoped entry for the default tenant wins over the legacy keys.
   */
  public Map<String, OpenCRQConfig> getOpencrq() {
    if (opencrq == null || opencrq.isEmpty()) {
      return Map.of();
    }
    Map<String, OpenCRQConfig> normalized = new HashMap<>();
    Map<String, Object> legacyFields = new HashMap<>();
    for (Map.Entry<String, Object> entry : opencrq.entrySet()) {
      if (entry.getValue() instanceof Map<?, ?> tenantConfigMap) {
        normalized.put(entry.getKey(), mapper.convertValue(tenantConfigMap, OpenCRQConfig.class));
      } else {
        legacyFields.put(entry.getKey(), entry.getValue());
      }
    }
    if (!legacyFields.isEmpty()) {
      normalized.putIfAbsent(
          Tenant.DEFAULT_TENANT_UUID, mapper.convertValue(legacyFields, OpenCRQConfig.class));
    }
    return normalized;
  }
}
