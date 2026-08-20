package io.openaev.api.stix_process;

import static io.openaev.api.stix_process.StixApi.TENANT_STIX_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.DnsResolution;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Non-admin counterpart of {@link StixApiTenantIsolationTest}: the injector-contract scoping fix
 * must not depend on RBAC being bypassed by {@code isAdmin = true}. Runs as a non-admin holding
 * only {@code MANAGE_STIX_BUNDLE} in the tenant.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=injectors")
@WithMockUser(isAdmin = false)
@DisplayName("StixApi scopes the payload-supporting injector lookup for a non-admin caller")
class StixApiNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> MANAGE_STIX_BUNDLE = Set.of(Capability.MANAGE_STIX_BUNDLE);

  @Resource protected ObjectMapper mapper;

  @Autowired private MockMvc mvc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectRepository injectRepository;

  // Same reasoning as StixApiTenantIsolationTest: the real bean only exposes connectors
  // configured at context startup, so stub one for the freshly-seeded tenant.
  @MockitoBean private OpenCTIConnectorService openCTIConnectorService;

  private String tenantA;
  private JsonNode stixSecurityCoverageWithDomainName;

  @BeforeEach
  void seedTenantWithPayloadSupportingInjector() throws Exception {
    tenantA =
        tenantHelper
            .createTenantWithCapabilities("stix-nonadmin-iso-a", MANAGE_STIX_BUNDLE)
            .getId();
    stixSecurityCoverageWithDomainName =
        loadJsonWithStixObjects(
            "src/test/resources/stix-bundles/security-coverage-with-domain-name.json");

    ConnectorBase connectorBase = mock(ConnectorBase.class);
    when(connectorBase.getUrl()).thenReturn("http://localhost/opencti");
    when(openCTIConnectorService.getConnectorBase(eq(tenantA)))
        .thenReturn(Optional.of(connectorBase));
  }

  @Test
  @DisplayName(
      "a non-admin processing a bundle with DNS indicators under tenant A's path links the DNS"
          + " resolution payload's contract to A's injector")
  void nonAdminProcessingBundleUnderTenantPathLinksDnsResolutionContractToTenantInjector()
      throws Exception {
    String createdResponse =
        mvc.perform(
                post(TENANT_STIX_URI + "/process-bundle", tenantA)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(stixSecurityCoverageWithDomainName))
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String scenarioId = JsonPath.read(createdResponse, "$.scenarioId");
    Scenario createdScenario = scenarioRepository.findById(scenarioId).orElseThrow();
    Set<Inject> injects = injectRepository.findByScenarioId(createdScenario.getId());

    assertThat(injects)
        .as("DNS resolution indicators in the bundle must become injects, not be silently dropped")
        .anyMatch(
            inject ->
                inject.getPayload().isPresent()
                    && inject.getPayload().get() instanceof DnsResolution);
  }

  private JsonNode loadJsonWithStixObjects(String filePath) throws Exception {
    String rawJson = IOUtils.toString(new FileInputStream(filePath), StandardCharsets.UTF_8);
    JsonNode rootNode = mapper.readTree(rawJson);

    JsonNode eventNode = rootNode.get("event");
    if (eventNode != null && eventNode.has("stix_objects")) {
      JsonNode stixObjectsNode = eventNode.get("stix_objects");
      if (!stixObjectsNode.isTextual()) {
        ((ObjectNode) eventNode).put("stix_objects", mapper.writeValueAsString(stixObjectsNode));
      }
    }
    return rootNode;
  }
}
