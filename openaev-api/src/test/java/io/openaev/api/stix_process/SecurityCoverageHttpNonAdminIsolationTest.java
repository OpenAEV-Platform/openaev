package io.openaev.api.stix_process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Inject;
import io.openaev.opencti.connectors.ConnectorBase;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.rest.inject.form.InjectExecutionAction;
import io.openaev.rest.inject.form.InjectExecutionInput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.fixtures.composers.InjectComposer;
import io.openaev.utils.fixtures.composers.InjectStatusComposer;
import io.openaev.utils.fixtures.composers.InjectorContractComposer;
import io.openaev.utils.helpers.InjectTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Non-admin counterpart of SecurityCoverageHttpIsolationTest under security_coverages v2 activation.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=security_coverages")
@WithMockUser(isAdmin = false)
@DisplayName("security_coverages isolation through STIX HTTP endpoint for non-admin")
class SecurityCoverageHttpNonAdminIsolationTest extends IntegrationTest {

  private static final Set<Capability> MANAGE_STIX_BUNDLE = Set.of(Capability.MANAGE_STIX_BUNDLE);
  private static final Set<Capability> INJECT_CAPABILITIES =
      Set.of(Capability.AGENT_RUNTIME_ACCESS);
  private static final String PROCESS_BUNDLE_URI = "/api/tenants/{tenantId}/stix/process-bundle";
  private static final Path STIX_BUNDLE_PATH =
      Path.of("src/test/resources/stix-bundles/security-coverage-without-objects.json");

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private InjectTestHelper injectTestHelper;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;

  @MockitoBean private OpenCTIConnectorService openCTIConnectorService;

  private String tenantA;
  private String tenantB;

  @BeforeEach
  void setUp() throws Exception {
    tenantA = tenantHelper.createTenantWithCapabilities("sec-cov-http-non-admin-a", MANAGE_STIX_BUNDLE).getId();
    tenantB = tenantHelper.createTenantWithCapabilities("sec-cov-http-non-admin-b", MANAGE_STIX_BUNDLE).getId();

    ConnectorBase connector = mock(ConnectorBase.class);
    when(connector.getUrl()).thenReturn("https://opencti.local");
    when(connector.isRegistered()).thenReturn(true);
    when(openCTIConnectorService.getConnectorBase(anyString()))
        .thenAnswer(invocation -> java.util.Optional.of(connector));
  }

  @AfterEach
  void cleanup() {
    if (tenantA != null || tenantB != null) {
      jdbc.update(
          "DELETE FROM security_coverage_send_job WHERE security_coverage_send_job_simulation IN "
              + "(SELECT exercise_id FROM exercises WHERE tenant_id IN (?, ?))",
          tenantA,
          tenantB);
      jdbc.update(
          "DELETE FROM scenario_mails_reply_to WHERE scenario_id IN "
              + "(SELECT scenario_id FROM scenarios WHERE tenant_id IN (?, ?))",
          tenantA,
          tenantB);
      jdbc.update("DELETE FROM exercises WHERE tenant_id IN (?, ?)", tenantA, tenantB);
      jdbc.update("DELETE FROM security_coverages WHERE tenant_id IN (?, ?)", tenantA, tenantB);
      tenantHelper.deleteCommittedTenants(tenantA, tenantB);
    }
    TenantContext.clearCurrentTenant();
  }

  @Test
  @DisplayName("same external id can exist once per tenant for non-admin STIX processing")
  void sameExternalIdCanExistPerTenantForNonAdmin() throws Exception {
    String externalId = "security-coverage--" + UUID.randomUUID();
    String payloadA = loadBundlePayload(externalId, "tenant-a");
    String payloadB = loadBundlePayload(externalId, "tenant-b");

    mvc.perform(
            post(PROCESS_BUNDLE_URI, tenantA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadA)
                .with(csrf()))
        .andExpect(status().isOk());

    mvc.perform(
            post(PROCESS_BUNDLE_URI, tenantB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payloadB)
                .with(csrf()))
        .andExpect(status().isOk());

    Integer countA =
        jdbc.queryForObject(
            "SELECT count(*) FROM security_coverages"
                + " WHERE security_coverage_external_id = ? AND tenant_id = ?",
            Integer.class,
            externalId,
            tenantA);
    Integer countB =
        jdbc.queryForObject(
            "SELECT count(*) FROM security_coverages"
                + " WHERE security_coverage_external_id = ? AND tenant_id = ?",
            Integer.class,
            externalId,
            tenantB);
    Integer total =
        jdbc.queryForObject(
            "SELECT count(*) FROM security_coverages WHERE security_coverage_external_id = ?",
            Integer.class,
            externalId);

    assertEquals(1, countA, "tenant A should own exactly one row for this external id");
    assertEquals(1, countB, "tenant B should own exactly one row for this external id");
    assertEquals(2, total, "same external id must be isolated per tenant");
  }

  private String loadBundlePayload(String externalId, String contentSuffix) throws Exception {
    JsonNode root = mapper.readTree(Files.readString(STIX_BUNDLE_PATH));
    JsonNode stixObjectsNode = root.path("event").path("stix_objects");
    ObjectNode stixBundle =
        (ObjectNode)
            (stixObjectsNode.isTextual()
                ? mapper.readTree(stixObjectsNode.asText())
                : stixObjectsNode.deepCopy());

    ObjectNode coverage = (ObjectNode) stixBundle.withArray("objects").get(0);
    coverage.put("id", externalId);
    coverage.put("name", "Security Coverage " + contentSuffix);
    coverage.put("description", "security coverage " + contentSuffix);

    ((ObjectNode) root.path("event")).put("stix_objects", mapper.writeValueAsString(stixBundle));
    return mapper.writeValueAsString(root);
  }

  private <T> T inTenant(String tenantId, Supplier<T> work) {
    String previousTenant = TenantContext.hasCurrentTenant() ? TenantContext.getCurrentTenant() : null;
    TenantContext.setCurrentTenant(tenantId);
    try {
      return tenantTx.execute(TxCtx.forTenant(tenantId), work);
    } finally {
      if (previousTenant == null) {
        TenantContext.clearCurrentTenant();
      } else {
        TenantContext.setCurrentTenant(previousTenant);
      }
    }
  }
}

