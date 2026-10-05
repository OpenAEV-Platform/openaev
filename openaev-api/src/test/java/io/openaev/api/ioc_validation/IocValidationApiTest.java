package io.openaev.api.ioc_validation;

import static io.openaev.api.ioc_validation.IocValidationApi.IOC_VALIDATION_URI;
import static io.openaev.api.ioc_validation.IocValidationApi.TENANT_IOC_VALIDATION_URI;
import static io.openaev.api.stix_process.StixApi.TENANT_STIX_URI;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_FILE_NAME_KEY;
import static io.openaev.rest.payload.service.PayloadService.IOC_VALIDATION_RUN_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.ioc_validation.dto.IocValidationSettingsInput;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Agent;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Capability;
import io.openaev.database.model.IocValidationTestKind;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.service.stix.IocValidationBundleParser;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AgentFixture;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.InjectorFixture;
import io.openaev.utils.fixtures.composers.AgentComposer;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Deliberately NOT {@code @Transactional}: the intake endpoint suspends any caller transaction
 * (OpenCTI is never called while a connection is held), so it could not see uncommitted fixtures,
 * and what it records would escape a test rollback. Each test works in its own committed tenant,
 * deleted afterwards together with everything it owns.
 */
@TestPropertySource(properties = "openaev.tenant.active-tables=ioc_validations")
@WithMockUser(isAdmin = true)
@DisplayName("IOC validation API tests")
class IocValidationApiTest extends IntegrationTest {

  private static final String INDICATOR = "indicator--6d2f6bb1-31b1-4b8a-9d36-3b3b3a1f0e11";
  private static final String PLATFORM = "identity--1e2f6bb1-31b1-4b8a-9d36-3b3b3a1f0e22";
  private static final String INTAKE_URI = TENANT_STIX_URI + "/process-ioc-validation";

  @Resource private ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  // No OpenCTI is configured in tests: acknowledgements and status reports become no-ops, and the
  // lifecycle stays pending until a connector is registered.
  @MockitoBean private OpenCTIConnectorService openCTIConnectorService;

  private String tenantId;
  private String otherTenantId;

  @BeforeEach
  void setUp() throws Exception {
    tenantId =
        tenantHelper.createTenantWithCurrentUser("ioc-validation-" + UUID.randomUUID()).getId();
  }

  @AfterEach
  void cleanup() {
    for (String tenant : new String[] {tenantId, otherTenantId}) {
      if (tenant == null) {
        continue;
      }
      // The only children of the rows an approval creates that do not cascade with the tenant.
      jdbc.update(
          "DELETE FROM scenario_mails_reply_to WHERE scenario_id IN "
              + "(SELECT scenario_id FROM scenarios WHERE tenant_id = ?)",
          tenant);
      jdbc.update(
          "DELETE FROM scenarios_documents WHERE scenario_id IN "
              + "(SELECT scenario_id FROM scenarios WHERE tenant_id = ?)",
          tenant);
    }
    tenantHelper.deleteCommittedTenants(tenantId, otherTenantId);
    tenantId = null;
    otherTenantId = null;
    assetGroupComposer.reset();
    endpointComposer.reset();
  }

  private String ctiEvent(String requestId, String observableType, String value, String testKind) {
    return ctiEvent(requestId, observableType, value, testKind, null);
  }

  private String ctiEvent(
      String requestId, String observableType, String value, String testKind, String fileName) {
    ObjectNode request = mapper.createObjectNode();
    request.put("type", IocValidationBundleParser.REQUEST_TYPE);
    request.put("id", IocValidationBundleParser.REQUEST_TYPE + "--" + requestId);
    request.put("name", "Validation of " + value);
    request.put("requested_by", "Analyst");
    request.putArray("test_kinds").add(testKind);
    ObjectNode ioc = request.putArray("iocs").addObject();
    ioc.put("indicator_ref", INDICATOR);
    ioc.put("observable_type", observableType);
    ioc.put("value", value);
    ioc.put("test_kind", testKind);
    if (fileName != null) {
      ioc.put("file_name", fileName);
    }
    ObjectNode pair = request.putArray("pairs").addObject();
    pair.put("indicator_ref", INDICATOR);
    pair.put("platform_ref", PLATFORM);
    pair.put("deployed_on_ref", "relationship--" + UUID.randomUUID());
    ObjectNode platform = mapper.createObjectNode();
    platform.put("type", "identity");
    platform.put("id", PLATFORM);
    platform.put("name", "Microsoft Defender");
    ObjectNode bundle = mapper.createObjectNode();
    bundle.put("type", "bundle");
    bundle.put("id", "bundle--" + UUID.randomUUID());
    ArrayNode objects = bundle.putArray("objects");
    objects.add(request);
    objects.add(platform);

    ObjectNode event = mapper.createObjectNode();
    event.putObject("internal").put("work_id", "work_" + UUID.randomUUID());
    ObjectNode eventNode = event.putObject("event");
    eventNode.put("stix_objects", bundle.toString());
    eventNode.put("entity_id", requestId);
    eventNode.put("entity_type", "Ioc-Validation-Request");
    return event.toString();
  }

  private MockHttpServletRequestBuilder intake(String tenant, String body) {
    return post(INTAKE_URI, tenant)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .with(csrf());
  }

  private String receive(String tenant, String body) throws Exception {
    String response =
        mvc.perform(intake(tenant, body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.iocValidationId");
  }

  private String receive(String body) throws Exception {
    return receive(tenantId, body);
  }

  private String receiveDnsRequest() throws Exception {
    return receive(
        ctiEvent(
            UUID.randomUUID().toString(), "Domain-Name", "evil.example.com", "dns_resolution"));
  }

  private String validation(String id) throws Exception {
    return mvc.perform(get(TENANT_IOC_VALIDATION_URI + "/{id}", tenantId, id))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private MockHttpServletRequestBuilder decide(String id, String decision) {
    return post(TENANT_IOC_VALIDATION_URI + "/{id}/" + decision, tenantId, id).with(csrf());
  }

  private MockHttpServletRequestBuilder putSettings(String body) {
    return put(TENANT_IOC_VALIDATION_URI + "/settings", tenantId)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .with(csrf());
  }

  private long recordCount() {
    return jdbc.queryForObject(
        "SELECT COUNT(*) FROM ioc_validations WHERE tenant_id = ?", Long.class, tenantId);
  }

  @Nested
  @DisplayName("Request intake from OpenCTI")
  class Intake {

    @Test
    @DisplayName("records the request awaiting approval with the planned test")
    void given_request_should_recordAwaitingApproval() throws Exception {
      allowTestKindOnValidationTargets(IocValidationTestKind.DNS_RESOLUTION);
      String id = receiveDnsRequest();

      String response = validation(id);

      assertThat((String) JsonPath.read(response, "$.ioc_validation_status"))
          .isEqualTo("AWAITING_APPROVAL");
      assertThat((String) JsonPath.read(response, "$.ioc_validation_iocs[0].ioc_test_kind"))
          .isEqualTo("DNS_RESOLUTION");
      assertThat((String) JsonPath.read(response, "$.ioc_validation_pairs[0].pair_platform_name"))
          .isEqualTo("Microsoft Defender");
      assertThat((String) JsonPath.read(response, "$.ioc_validation_requested_by"))
          .isEqualTo("Analyst");
    }

    @Test
    @DisplayName("skips test kinds the tenant does not allow, before anyone approves")
    void given_notAllowedKind_should_previewSkip() throws Exception {
      String id =
          receive(
              ctiEvent(
                  UUID.randomUUID().toString(), "IPv4-Addr", "203.0.113.7", "network_traffic"));

      String response = validation(id);
      assertThat((List<Object>) JsonPath.read(response, "$.ioc_validation_iocs")).hasSize(1);
      assertThat((String) JsonPath.read(response, "$.ioc_validation_iocs[0].ioc_test_kind"))
          .isNull();
      assertThat((String) JsonPath.read(response, "$.ioc_validation_iocs[0].ioc_message"))
          .contains("do not allow this test (Network traffic)");
    }

    @Test
    @DisplayName("keeps one record when OpenCTI re-dispatches the same request")
    void given_replay_should_keepExistingRecord() throws Exception {
      String requestId = UUID.randomUUID().toString();
      String body = ctiEvent(requestId, "Domain-Name", "evil.example.com", "dns_resolution");

      String first = receive(body);
      String second = receive(body);

      assertThat(second).isEqualTo(first);
      assertThat(recordCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("keeps one record when OpenCTI delivers the same request concurrently")
    void given_concurrentReplays_should_keepOneRecord() throws Exception {
      String body =
          ctiEvent(
              UUID.randomUUID().toString(), "Domain-Name", "evil.example.com", "dns_resolution");
      SecurityContext security = TestSecurityContextHolder.getContext();
      CountDownLatch start = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(4);
      try {
        List<Future<String>> deliveries = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
          deliveries.add(
              pool.submit(
                  () -> {
                    TestSecurityContextHolder.setContext(security);
                    try {
                      start.await();
                      return receive(body);
                    } finally {
                      TestSecurityContextHolder.clearContext();
                    }
                  }));
        }
        start.countDown();
        Set<String> ids = new HashSet<>();
        for (Future<String> delivery : deliveries) {
          ids.add(delivery.get(60, TimeUnit.SECONDS));
        }

        assertThat(ids).hasSize(1);
        assertThat(recordCount()).isEqualTo(1);
      } finally {
        pool.shutdownNow();
      }
    }

    @Test
    @DisplayName("acknowledges a malformed request without recording anything")
    void given_malformedBundle_should_answerOkWithoutRecord() throws Exception {
      ObjectNode event = mapper.createObjectNode();
      event.putObject("internal").put("work_id", "work_" + UUID.randomUUID());
      event.putObject("event").put("stix_objects", "{\"type\":\"bundle\",\"objects\":[]}");

      mvc.perform(intake(tenantId, event.toString())).andExpect(status().isOk());

      assertThat(recordCount()).isZero();
    }

    @Test
    @DisplayName(
        "answers OK to an event without work or STIX objects, which OpenCTI would redeliver")
    void given_malformedEvent_should_answerOkWithoutRecord() throws Exception {
      ObjectNode noWork = mapper.createObjectNode();
      noWork.putObject("internal").put("work_id", " ");
      noWork.putObject("event").put("stix_objects", "{\"type\":\"bundle\",\"objects\":[]}");
      String workId = "work_" + UUID.randomUUID();
      ObjectNode noObjects = mapper.createObjectNode();
      noObjects.putObject("internal").put("work_id", workId);
      noObjects.putObject("event").put("entity_id", UUID.randomUUID().toString());
      ObjectNode empty = mapper.createObjectNode();

      for (ObjectNode event : List.of(noWork, noObjects, empty)) {
        mvc.perform(intake(tenantId, event.toString())).andExpect(status().isOk());
      }
      // No event at all: a JSON null and an empty body
      mvc.perform(intake(tenantId, "null")).andExpect(status().isOk());
      mvc.perform(intake(tenantId, "")).andExpect(status().isOk());

      assertThat(recordCount()).isZero();
      verify(openCTIConnectorService)
          .acknowledgeProcessedOfIocValidation(
              eq(workId), contains("no STIX objects"), eq(true), anyString());
      verify(openCTIConnectorService, never())
          .acknowledgeReceivedOfIocValidation(eq(" "), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("Mandatory decision")
  class Decision {

    @Test
    @DisplayName("rejects a waiting request with the reason")
    void given_waitingRequest_should_reject() throws Exception {
      String id = receiveDnsRequest();

      String response =
          mvc.perform(
                  decide(id, "reject")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"ioc_validation_reason\":\"Out of the test window\"}"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((String) JsonPath.read(response, "$.ioc_validation_status")).isEqualTo("REJECTED");
      assertThat((String) JsonPath.read(response, "$.ioc_validation_status_message"))
          .contains("Out of the test window");
    }

    @Test
    @DisplayName("refuses a second decision")
    void given_rejectedRequest_should_refuseApproval() throws Exception {
      String id = receiveDnsRequest();
      mvc.perform(decide(id, "reject").contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isOk());

      mvc.perform(decide(id, "approve")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuses an approval without asset group to run the tests on")
    void given_noAssetGroup_should_refuseApproval() throws Exception {
      String id = receiveDnsRequest();

      mvc.perform(decide(id, "approve")).andExpect(status().isBadRequest());
      assertThat((String) JsonPath.read(validation(id), "$.ioc_validation_status"))
          .isEqualTo("AWAITING_APPROVAL");
    }

    @Test
    @DisplayName("refuses an approval when no endpoint of the asset group has an active agent")
    void given_noActiveAgent_should_refuseApproval() throws Exception {
      injectorFixture.getWellKnownOaevImplantInjector();
      AssetGroup assetGroup = validationTargets(AgentFixture.createInactiveAgent());
      allow(List.of(IocValidationTestKind.DNS_RESOLUTION), assetGroup);
      String id = receiveDnsRequest();

      mvc.perform(decide(id, "approve")).andExpect(status().isBadRequest());

      assertThat((String) JsonPath.read(validation(id), "$.ioc_validation_status"))
          .isEqualTo("AWAITING_APPROVAL");
    }

    @Test
    @DisplayName("an approval never runs a test the operator was shown as skipped")
    void given_settingsWidenedAfterIntake_should_keepSkippedTest() throws Exception {
      injectorFixture.getWellKnownOaevImplantInjector();
      AssetGroup assetGroup = validationTargets();
      allow(List.of(IocValidationTestKind.DNS_RESOLUTION), assetGroup);
      String id =
          receive(
              ctiEvent(
                  UUID.randomUUID().toString(), "IPv4-Addr", "203.0.113.7", "network_traffic"));
      // Allowed only after the request was shown with its network test skipped
      allow(
          List.of(IocValidationTestKind.DNS_RESOLUTION, IocValidationTestKind.NETWORK_TRAFFIC),
          assetGroup);

      mvc.perform(decide(id, "approve")).andExpect(status().isBadRequest());

      String response = validation(id);
      assertThat((String) JsonPath.read(response, "$.ioc_validation_status"))
          .isEqualTo("AWAITING_APPROVAL");
      assertThat((String) JsonPath.read(response, "$.ioc_validation_iocs[0].ioc_test_kind"))
          .isNull();
      assertThat((List<String>) JsonPath.read(response, "$.ioc_validation_iocs[0].ioc_inject_ids"))
          .isNullOrEmpty();
    }

    @Test
    @DisplayName("an approval refuses a test whose target changed since the request was shown")
    void given_sinkholeRemovedAfterIntake_should_refuseApproval() throws Exception {
      injectorFixture.getWellKnownOaevImplantInjector();
      AssetGroup assetGroup = validationTargets();
      List<IocValidationTestKind> kinds = List.of(IocValidationTestKind.NETWORK_TRAFFIC);
      mvc.perform(
              putSettings(
                  mapper.writeValueAsString(
                      new IocValidationSettingsInput(
                          kinds, "", "192.0.2.53", 443, assetGroup.getId()))))
          .andExpect(status().isOk());
      String id =
          receive(
              ctiEvent(
                  UUID.randomUUID().toString(), "IPv4-Addr", "203.0.113.7", "network_traffic"));
      // Shown as a connection to the sinkhole, would now connect to the IOC itself
      allow(kinds, assetGroup);

      mvc.perform(decide(id, "approve")).andExpect(status().isBadRequest());

      String response = validation(id);
      assertThat((String) JsonPath.read(response, "$.ioc_validation_status"))
          .isEqualTo("AWAITING_APPROVAL");
      assertThat((List<String>) JsonPath.read(response, "$.ioc_validation_iocs[0].ioc_inject_ids"))
          .isNullOrEmpty();
    }

    @Test
    @DisplayName("an approval builds and launches the validation simulation")
    void given_assetGroup_should_launchSimulation() throws Exception {
      allowTestKindOnValidationTargets(IocValidationTestKind.DNS_RESOLUTION);
      String id = receiveDnsRequest();

      String response =
          mvc.perform(decide(id, "approve"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((String) JsonPath.read(response, "$.ioc_validation_status")).isEqualTo("RUNNING");
      assertThat((String) JsonPath.read(response, "$.ioc_validation_scenario_id")).isNotBlank();
      assertThat((String) JsonPath.read(response, "$.ioc_validation_simulation_id")).isNotBlank();
      assertThat((List<String>) JsonPath.read(response, "$.ioc_validation_iocs[0].ioc_inject_ids"))
          .isNotEmpty();
    }

    @Test
    @DisplayName("a file drop runs in a directory owned by its inject, with an up-to-date payload")
    void given_fileDrop_should_runInADirectoryOwnedByTheInject() throws Exception {
      allowTestKindOnValidationTargets(IocValidationTestKind.FILE_DROP);

      String firstInject = approveFileDrop();
      String payloadId = payloadOf(firstInject);
      assertThat(runOf(firstInject)).matches("[0-9a-f]{32}");
      assertThat(commandOf(payloadId)).contains("openaev-ioc-validation-");

      // A payload still carrying an earlier template (command, cleanup, arguments without the run
      // and an injector contract without its field) is brought back to the current one
      jdbc.update(
          "UPDATE payloads SET command_content = ?, payload_cleanup_command = ?,"
              + " payload_arguments = ?::jsonb WHERE payload_id = ?",
          "Set-Content -Path (Join-Path ([System.IO.Path]::GetTempPath()) #{"
              + IOC_VALIDATION_FILE_NAME_KEY
              + "}) -Value 'legacy'",
          "Remove-Item -Force -Path legacy",
          "[{\"type\":\"text\",\"key\":\""
              + IOC_VALIDATION_FILE_NAME_KEY
              + "\",\"default_value\":\"benign.txt\"}]",
          payloadId);
      jdbc.update(
          "UPDATE injectors_contracts SET injector_contract_content = ?"
              + " WHERE injector_contract_payload = ?",
          withoutRunField(contractContentOf(payloadId)),
          payloadId);
      assertThat(argumentKeysOf(payloadId)).doesNotContain(IOC_VALIDATION_RUN_KEY);
      assertThat(contractFieldKeysOf(payloadId)).doesNotContain(IOC_VALIDATION_RUN_KEY);

      String secondInject = approveFileDrop();

      assertThat(payloadOf(secondInject)).isEqualTo(payloadId);
      assertThat(commandOf(payloadId)).contains("openaev-ioc-validation-");
      assertThat(argumentKeysOf(payloadId))
          .contains(IOC_VALIDATION_FILE_NAME_KEY, IOC_VALIDATION_RUN_KEY);
      assertThat(contractFieldKeysOf(payloadId)).contains(IOC_VALIDATION_RUN_KEY);
      assertThat(runOf(secondInject)).isNotEqualTo(runOf(firstInject));
    }

    @Test
    @DisplayName("an injector contract gone stale alone is repaired before the next validation")
    void given_staleContractOfACurrentPayload_should_repairTheContract() throws Exception {
      allowTestKindOnValidationTargets(IocValidationTestKind.FILE_DROP);

      String firstInject = approveFileDrop();
      String payloadId = payloadOf(firstInject);
      String command = commandOf(payloadId);
      // Only the contract drifts: the payload keeps the current template
      jdbc.update(
          "UPDATE injectors_contracts SET injector_contract_content = ?"
              + " WHERE injector_contract_payload = ?",
          withoutRunField(contractContentOf(payloadId)),
          payloadId);
      assertThat(contractFieldKeysOf(payloadId)).doesNotContain(IOC_VALIDATION_RUN_KEY);

      String secondInject = approveFileDrop();

      assertThat(payloadOf(secondInject)).isEqualTo(payloadId);
      assertThat(commandOf(payloadId)).isEqualTo(command);
      assertThat(contractFieldKeysOf(payloadId)).contains(IOC_VALIDATION_RUN_KEY);
      assertThat(runOf(secondInject)).matches("[0-9a-f]{32}").isNotEqualTo(runOf(firstInject));
    }

    private String contractContentOf(String payloadId) {
      return jdbc.queryForObject(
          "SELECT injector_contract_content FROM injectors_contracts"
              + " WHERE injector_contract_payload = ?",
          String.class,
          payloadId);
    }

    private List<String> contractFieldKeysOf(String payloadId) throws Exception {
      List<String> keys = new ArrayList<>();
      mapper
          .readTree(contractContentOf(payloadId))
          .path("fields")
          .forEach(field -> keys.add(field.path("key").asText()));
      return keys;
    }

    private String withoutRunField(String contractContent) throws Exception {
      ObjectNode contract = (ObjectNode) mapper.readTree(contractContent);
      ArrayNode fields = mapper.createArrayNode();
      contract
          .path("fields")
          .forEach(
              field -> {
                if (!IOC_VALIDATION_RUN_KEY.equals(field.path("key").asText())) {
                  fields.add(field);
                }
              });
      contract.set("fields", fields);
      return mapper.writeValueAsString(contract);
    }

    private List<String> argumentKeysOf(String payloadId) {
      return jdbc.queryForList(
          "SELECT jsonb_array_elements(payload_arguments::jsonb) ->> 'key' FROM payloads"
              + " WHERE payload_id = ?",
          String.class,
          payloadId);
    }

    private String approveFileDrop() throws Exception {
      String id =
          receive(
              ctiEvent(
                  UUID.randomUUID().toString(),
                  "StixFile",
                  "275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f",
                  "file_drop",
                  "invoice.pdf"));
      String response =
          mvc.perform(decide(id, "approve"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      List<String> injectIds = JsonPath.read(response, "$.ioc_validation_iocs[0].ioc_inject_ids");
      assertThat(injectIds).hasSize(1);
      return injectIds.get(0);
    }

    private String payloadOf(String injectId) {
      return jdbc.queryForObject(
          "SELECT c.injector_contract_payload FROM injects i JOIN injectors_contracts c"
              + " ON c.injector_contract_id = i.inject_injector_contract"
              + " AND c.tenant_id = i.tenant_id WHERE i.inject_id = ?",
          String.class,
          injectId);
    }

    private String runOf(String injectId) {
      return jdbc.queryForObject(
          "SELECT inject_content::jsonb ->> ? FROM injects WHERE inject_id = ?",
          String.class,
          IOC_VALIDATION_RUN_KEY,
          injectId);
    }

    private String commandOf(String payloadId) {
      return jdbc.queryForObject(
          "SELECT command_content FROM payloads WHERE payload_id = ?", String.class, payloadId);
    }
  }

  private AssetGroup validationTargets() {
    return validationTargets(AgentFixture.createDefaultAgentService());
  }

  private AssetGroup validationTargets(Agent agent) {
    TenantContext.setCurrentTenant(tenantId);
    try {
      return assetGroupComposer
          .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("IOC validation targets"))
          .withAsset(
              endpointComposer
                  .forEndpoint(EndpointFixture.createEndpoint())
                  .withAgent(agentComposer.forAgent(agent)))
          .persist()
          .get();
    } finally {
      TenantContext.clearCurrentTenant();
    }
  }

  private void allow(List<IocValidationTestKind> kinds, AssetGroup assetGroup) throws Exception {
    mvc.perform(
            putSettings(
                mapper.writeValueAsString(
                    new IocValidationSettingsInput(kinds, "", "", 443, assetGroup.getId()))))
        .andExpect(status().isOk());
  }

  private void allowTestKindOnValidationTargets(IocValidationTestKind kind) throws Exception {
    injectorFixture.getWellKnownOaevImplantInjector();
    allow(List.of(kind), validationTargets());
  }

  @Nested
  @DisplayName("Safety settings")
  class Settings {

    @Test
    @DisplayName("defaults to no allowed test kind, on port 443")
    void given_defaults_should_allowNothing() throws Exception {
      String response =
          mvc.perform(get(TENANT_IOC_VALIDATION_URI + "/settings", tenantId))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((List<String>) JsonPath.read(response, "$.ioc_validation_allowed_test_kinds"))
          .isEmpty();
      assertThat((Integer) JsonPath.read(response, "$.ioc_validation_network_port")).isEqualTo(443);
    }

    @Test
    @DisplayName("refuses HTTP HEAD tests without egress proxy")
    void given_httpHeadWithoutProxy_should_refuse() throws Exception {
      mvc.perform(
              putSettings(
                  "{\"ioc_validation_allowed_test_kinds\":[\"DNS_RESOLUTION\",\"HTTP_HEAD\"],"
                      + "\"ioc_validation_network_port\":443}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuses an egress proxy URL carrying credentials")
    void given_proxyWithCredentials_should_refuse() throws Exception {
      mvc.perform(
              putSettings(
                  "{\"ioc_validation_allowed_test_kinds\":[\"DNS_RESOLUTION\",\"HTTP_HEAD\"],"
                      + "\"ioc_validation_http_proxy_url\":\"https://user:secret@proxy.internal:3128\","
                      + "\"ioc_validation_network_port\":443}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuses a sinkhole that is not an IP address")
    void given_invalidSinkhole_should_refuse() throws Exception {
      mvc.perform(
              putSettings(
                  "{\"ioc_validation_allowed_test_kinds\":[\"NETWORK_TRAFFIC\"],"
                      + "\"ioc_validation_sinkhole_address\":\"sinkhole.internal\","
                      + "\"ioc_validation_network_port\":443}"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("stores a consistent widened allow-list")
    void given_validSettings_should_store() throws Exception {
      String response =
          mvc.perform(
                  putSettings(
                      "{\"ioc_validation_allowed_test_kinds\":[\"DNS_RESOLUTION\",\"HTTP_HEAD\",\"NETWORK_TRAFFIC\"],"
                          + "\"ioc_validation_http_proxy_url\":\"http://proxy.internal:3128\","
                          + "\"ioc_validation_sinkhole_address\":\"192.0.2.10\","
                          + "\"ioc_validation_network_port\":8443}"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((List<String>) JsonPath.read(response, "$.ioc_validation_allowed_test_kinds"))
          .containsExactlyInAnyOrder("DNS_RESOLUTION", "HTTP_HEAD", "NETWORK_TRAFFIC");
      assertThat((String) JsonPath.read(response, "$.ioc_validation_sinkhole_address"))
          .isEqualTo("192.0.2.10");
    }

    private AssetGroup persistAssetGroup(String name) {
      return persistAssetGroup(tenantId, name);
    }

    private AssetGroup persistAssetGroup(String tenant, String name) {
      TenantContext.setCurrentTenant(tenant);
      try {
        return assetGroupComposer
            .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup(name))
            .persist()
            .get();
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }

    private String assetGroupOptions(String tenant, String searchText) throws Exception {
      return mvc.perform(
              get(TENANT_IOC_VALIDATION_URI + "/settings/asset-group-options", tenant)
                  .param("searchText", searchText))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    @Test
    @DisplayName("lists the asset groups by name, the configured one first")
    void given_assetGroups_should_listThemConfiguredFirst() throws Exception {
      AssetGroup configured = persistAssetGroup("Configured targets");
      AssetGroup matching = persistAssetGroup("IOC validation targets");
      persistAssetGroup("Unrelated group");
      mvc.perform(
              putSettings(
                  mapper.writeValueAsString(
                      new IocValidationSettingsInput(
                          List.of(IocValidationTestKind.DNS_RESOLUTION),
                          "",
                          "",
                          443,
                          configured.getId()))))
          .andExpect(status().isOk());

      String response = assetGroupOptions(tenantId, "validation");

      assertThat((List<String>) JsonPath.read(response, "$[*].id"))
          .containsExactly(configured.getId(), matching.getId());
      assertThat((String) JsonPath.read(response, "$[1].label"))
          .isEqualTo("IOC validation targets");
    }

    @Test
    @WithMockUser
    @DisplayName("lists the asset groups with the settings access only")
    void given_settingsAccessOnly_should_listAssetGroups() throws Exception {
      otherTenantId =
          tenantHelper
              .createTenantWithCapabilities(
                  "ioc-validation-" + UUID.randomUUID(), Set.of(Capability.ACCESS_TENANT_SETTINGS))
              .getId();
      AssetGroup group = persistAssetGroup(otherTenantId, "IOC validation targets");

      String response = assetGroupOptions(otherTenantId, "");

      assertThat((List<String>) JsonPath.read(response, "$[*].id")).contains(group.getId());
    }

    @Test
    @DisplayName("does not list the asset groups of another tenant")
    void given_otherTenant_should_notListAssetGroups() throws Exception {
      AssetGroup group = persistAssetGroup("IOC validation targets");
      otherTenantId =
          tenantHelper.createTenantWithCurrentUser("ioc-validation-" + UUID.randomUUID()).getId();

      String response = assetGroupOptions(otherTenantId, "");

      assertThat((List<String>) JsonPath.read(response, "$[*].id")).doesNotContain(group.getId());
    }
  }

  @Nested
  @DisplayName("Search")
  class Search {

    @Test
    @DisplayName("lists the received requests")
    void given_requests_should_search() throws Exception {
      String id = receiveDnsRequest();

      String response =
          mvc.perform(
                  post(TENANT_IOC_VALIDATION_URI + "/search", tenantId)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"page\":0,\"size\":50}")
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((List<String>) JsonPath.read(response, "$.content[*].ioc_validation_id"))
          .contains(id);
    }
  }

  @Nested
  @DisplayName("Tenant isolation")
  class TenantIsolation {

    @Test
    @DisplayName("a request received for one tenant is not visible from another tenant")
    void given_requestOfOneTenant_should_notBeVisibleFromAnotherTenant() throws Exception {
      otherTenantId =
          tenantHelper.createTenantWithCurrentUser("ioc-validation-" + UUID.randomUUID()).getId();
      String id = receiveDnsRequest();

      mvc.perform(get(TENANT_IOC_VALIDATION_URI + "/{id}", tenantId, id))
          .andExpect(status().isOk());
      mvc.perform(get(TENANT_IOC_VALIDATION_URI + "/{id}", otherTenantId, id))
          .andExpect(status().isNotFound());
      mvc.perform(post(TENANT_IOC_VALIDATION_URI + "/{id}/approve", otherTenantId, id).with(csrf()))
          .andExpect(status().is4xxClientError());
      mvc.perform(get(IOC_VALIDATION_URI + "/" + id).header("X-Tenant-Ids", otherTenantId))
          .andExpect(status().isNotFound());
    }
  }
}
