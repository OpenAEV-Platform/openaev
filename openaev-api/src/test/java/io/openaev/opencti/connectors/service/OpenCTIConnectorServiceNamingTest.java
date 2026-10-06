package io.openaev.opencti.connectors.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.TenantRepository;
import io.openaev.opencti.config.OpenCTIConfig;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.impl.IocValidationConnector;
import io.openaev.opencti.service.OpenCTIService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Name of the IOC validation connector in OpenCTI")
class OpenCTIConnectorServiceNamingTest {

  private final XtmConfig xtmConfig = mock(XtmConfig.class);
  private final OpenCTIService openCTIService = mock(OpenCTIService.class);
  private final TenantRepository tenantRepository = mock(TenantRepository.class);
  private final OpenCTIConnectorService service =
      new OpenCTIConnectorService(
          xtmConfig, mock(OpenAEVConfig.class), openCTIService, tenantRepository);

  private static OpenCTIConfig enabledConfig() {
    OpenCTIConfig config = new OpenCTIConfig();
    config.setEnable(true);
    config.setUrl("https://opencti.example.com");
    config.setToken("token");
    return config;
  }

  private static Optional<Tenant> tenant(String id, String name) {
    Tenant tenant = new Tenant();
    tenant.setId(id);
    tenant.setName(name);
    return Optional.of(tenant);
  }

  private List<String> registeredIocValidationNames() throws Exception {
    List<String> names = new ArrayList<>();
    doAnswer(
            invocation -> {
              ConnectorBase connector = invocation.getArgument(0);
              if (connector instanceof IocValidationConnector) {
                names.add(connector.getName());
              }
              return null;
            })
        .when(openCTIService)
        .registerConnector(any());
    service.registerOrPingAllConnectors();
    return names;
  }

  @Test
  @DisplayName("one tenant connected to OpenCTI: no tenant suffix")
  void given_oneTenant_should_nameWithoutSuffix() throws Exception {
    when(xtmConfig.getOpencti()).thenReturn(Map.of(Tenant.DEFAULT_TENANT_UUID, enabledConfig()));
    service.initializeConnectors();

    assertThat(registeredIocValidationNames()).containsExactly(IocValidationConnector.NAME);
    verifyNoInteractions(tenantRepository);
  }

  @Test
  @DisplayName("several tenants connected to OpenCTI: each connector carries its tenant name")
  void given_severalTenants_should_nameAfterEachTenant() throws Exception {
    Map<String, OpenCTIConfig> configs = new LinkedHashMap<>();
    configs.put("tenant-a", enabledConfig());
    configs.put("tenant-b", enabledConfig());
    configs.put("tenant-c", enabledConfig());
    when(xtmConfig.getOpencti()).thenReturn(configs);
    when(tenantRepository.findById("tenant-a")).thenReturn(tenant("tenant-a", "Acme"));
    when(tenantRepository.findById("tenant-b")).thenReturn(tenant("tenant-b", "Globex"));
    // A tenant row that cannot be read keeps a stable, distinct name
    when(tenantRepository.findById("tenant-c")).thenReturn(Optional.empty());
    service.initializeConnectors();

    assertThat(registeredIocValidationNames())
        .containsExactlyInAnyOrder(
            IocValidationConnector.NAME + " - Acme",
            IocValidationConnector.NAME + " - Globex",
            IocValidationConnector.NAME + " - tenant-c");
  }
}
