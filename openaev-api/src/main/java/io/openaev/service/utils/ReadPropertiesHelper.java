package io.openaev.service.utils;

import static io.openaev.config.security.SecurityService.OPENAEV_PROVIDER_PATH_PREFIX;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Tenant;
import io.openaev.sso.GroupMapping;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadPropertiesHelper {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String TENANT_ID_SUFFIX = ".tenant_id";
  private static final String USER_SCOPE_SUFFIX = ".user_scope";

  private final Environment env;

  public String resolveProviderTenantId(String registrationId) {
    if (registrationId == null || registrationId.isBlank()) {
      return Tenant.DEFAULT_TENANT_UUID;
    }
    String configuredProviderTenantId =
        env.getProperty(
            OPENAEV_PROVIDER_PATH_PREFIX + registrationId + TENANT_ID_SUFFIX, String.class, "");
    if (configuredProviderTenantId == null || configuredProviderTenantId.isBlank()) {
      return Tenant.DEFAULT_TENANT_UUID;
    }
    return configuredProviderTenantId;
  }

  public String resolveProviderUserScope(String registrationId) {
    if (registrationId == null || registrationId.isBlank()) {
      return "{tenant}";
    }
    return env.getProperty(
        OPENAEV_PROVIDER_PATH_PREFIX + registrationId + USER_SCOPE_SUFFIX,
        String.class,
        "{tenant}");
  }

  @SuppressWarnings("unchecked")
  public List<String> getProviderPropertyAsList(
      @NotBlank final String registrationId, final String property) {
    String propertyKey = OPENAEV_PROVIDER_PATH_PREFIX + registrationId + "." + property;
    return env.getProperty(propertyKey, List.class, new ArrayList<String>());
  }

  public List<GroupMapping> safeParseMappings(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      return OBJECT_MAPPER.readValue(json, new TypeReference<>() {});
    } catch (IOException e) {
      log.error("Failed to parse group mappings: {}", e.getMessage(), e);
      return List.of();
    }
  }
}
