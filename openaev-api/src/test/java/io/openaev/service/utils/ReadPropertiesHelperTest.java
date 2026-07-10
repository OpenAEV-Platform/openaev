package io.openaev.service.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.database.model.Tenant;
import io.openaev.sso.GroupMapping;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

@ExtendWith(MockitoExtension.class)
class ReadPropertiesHelperTest {

  @Mock private Environment env;

  @Test
  @DisplayName("resolveProviderTenantId should return default tenant when registration is blank")
  void resolveProviderTenantId_shouldReturnDefaultWhenRegistrationBlank() {
    ReadPropertiesHelper helper = new ReadPropertiesHelper(env);

    String tenantId = helper.resolveProviderTenantId("");

    assertThat(tenantId).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  @DisplayName("resolveProviderTenantId should return default tenant when property is blank")
  void resolveProviderTenantId_shouldReturnDefaultWhenPropertyBlank() {
    ReadPropertiesHelper helper = new ReadPropertiesHelper(env);
    when(env.getProperty("openaev.provider.microsoft.tenant_id", String.class, "")).thenReturn("");

    String tenantId = helper.resolveProviderTenantId("microsoft");

    assertThat(tenantId).isEqualTo(Tenant.DEFAULT_TENANT_UUID);
  }

  @Test
  @DisplayName("resolveProviderTenantId should return configured tenant when present")
  void resolveProviderTenantId_shouldReturnConfiguredValue() {
    ReadPropertiesHelper helper = new ReadPropertiesHelper(env);
    when(env.getProperty("openaev.provider.microsoft.tenant_id", String.class, ""))
        .thenReturn("tenant-123");

    String tenantId = helper.resolveProviderTenantId("microsoft");

    assertThat(tenantId).isEqualTo("tenant-123");
  }

  @Test
  @DisplayName("resolveProviderUserScope should return tenant default when registration is blank")
  void resolveProviderUserScope_shouldReturnDefaultWhenRegistrationBlank() {
    ReadPropertiesHelper helper = new ReadPropertiesHelper(env);

    String userScope = helper.resolveProviderUserScope("");

    assertThat(userScope).isEqualTo("{tenant}");
  }

  @Test
  @DisplayName("resolveProviderUserScope should read configured user scope")
  void resolveProviderUserScope_shouldReadConfiguredValue() {
    ReadPropertiesHelper helper = new ReadPropertiesHelper(env);
    when(env.getProperty("openaev.provider.microsoft.user_scope", String.class, "{tenant}"))
        .thenReturn("{platform,tenant}");

    String userScope = helper.resolveProviderUserScope("microsoft");

    assertThat(userScope).isEqualTo("{platform,tenant}");
  }

  @Test
  @DisplayName("getProviderPropertyAsList should return configured list value")
  void getProviderPropertyAsList_shouldReturnConfiguredList() {
    ReadPropertiesHelper helper = new ReadPropertiesHelper(env);
    when(env.getProperty("openaev.provider.oidc.roles_path", List.class, new ArrayList<String>()))
        .thenReturn(List.of("roles"));

    List<String> result = helper.getProviderPropertyAsList("oidc", "roles_path");

    assertThat(result).isEqualTo(List.of("roles"));
    verify(env)
        .getProperty("openaev.provider.oidc.roles_path", List.class, new ArrayList<String>());
  }

  @Test
  @DisplayName("safeParseMappings should parse valid json mappings")
  void safeParseMappings_shouldParseValidJson() {
    ReadPropertiesHelper helper = new ReadPropertiesHelper(env);

    List<GroupMapping> mappings =
        helper.safeParseMappings(
            "[{\"idpGroup\":\"A\",\"userGroup\":\"GROUP_A\",\"autoCreate\":true}]");

    assertThat(mappings).hasSize(1);
    assertThat(mappings.getFirst().getIdpGroup()).isEqualTo("A");
    assertThat(mappings.getFirst().getUserGroup()).isEqualTo("GROUP_A");
    assertThat(mappings.getFirst().isAutoCreate()).isTrue();
  }

  @Test
  @DisplayName("safeParseMappings should return empty list on invalid json")
  void safeParseMappings_shouldReturnEmptyOnInvalidJson() {
    ReadPropertiesHelper helper = new ReadPropertiesHelper(env);

    List<GroupMapping> mappings = helper.safeParseMappings("{invalid");

    assertThat(mappings).isEmpty();
  }
}
