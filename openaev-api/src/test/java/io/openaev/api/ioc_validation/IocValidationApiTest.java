package io.openaev.api.ioc_validation;

import static io.openaev.api.ioc_validation.IocValidationApi.IOC_VALIDATION_URI;
import static io.openaev.api.ioc_validation.IocValidationApi.TENANT_IOC_VALIDATION_URI;
import static io.openaev.api.stix_process.StixApi.STIX_URI;
import static io.openaev.api.stix_process.StixApi.TENANT_STIX_URI;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
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
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.IocValidationStatus;
import io.openaev.database.repository.IocValidationRepository;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.service.stix.IocValidationBundleParser;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.EndpointFixture;
import io.openaev.utils.fixtures.composers.AssetGroupComposer;
import io.openaev.utils.fixtures.composers.EndpointComposer;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=ioc_validations")
@WithMockUser(isAdmin = true)
@DisplayName("IOC validation API tests")
class IocValidationApiTest extends IntegrationTest {

  private static final String INDICATOR = "indicator--6d2f6bb1-31b1-4b8a-9d36-3b3b3a1f0e11";
  private static final String PLATFORM = "identity--1e2f6bb1-31b1-4b8a-9d36-3b3b3a1f0e22";

  @Resource private ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  @Autowired private IocValidationRepository iocValidationRepository;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private TenantIsolationTestHelper tenantHelper;

  // No OpenCTI is configured in tests: acknowledgements and status reports become no-ops, and the
  // lifecycle stays pending until a connector is registered.
  @MockitoBean private OpenCTIConnectorService openCTIConnectorService;
  @Autowired private TenantScopedTransaction tenantTx;

  /** ioc_validations is tenant-active: direct repository reads need an explicit scope. */
  private <T> T scoped(Supplier<T> read) {
    return tenantTx.execute(TxCtx.allTenants(), read);
  }

  private String ctiEvent(String requestId, String observableType, String value, String testKind) {
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

  private String receive(String uriTemplate, String body, Object... uriVariables) throws Exception {
    String response =
        mvc.perform(
                post(uriTemplate + "/process-ioc-validation", uriVariables)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(response, "$.iocValidationId");
  }

  private String receiveDnsRequest() throws Exception {
    return receive(
        STIX_URI,
        ctiEvent(
            UUID.randomUUID().toString(), "Domain-Name", "evil.example.com", "dns_resolution"));
  }

  @Nested
  @DisplayName("Request intake from OpenCTI")
  class Intake {

    @Test
    @DisplayName("records the request awaiting approval with the planned test")
    void given_request_should_recordAwaitingApproval() throws Exception {
      String id = receiveDnsRequest();

      String response =
          mvc.perform(get(IOC_VALIDATION_URI + "/" + id))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

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
              STIX_URI,
              ctiEvent(
                  UUID.randomUUID().toString(), "IPv4-Addr", "203.0.113.7", "network_traffic"));

      IocValidation validation = scoped(() -> iocValidationRepository.findById(id).orElseThrow());
      assertThat(validation.getIocs())
          .singleElement()
          .satisfies(
              ioc -> {
                assertThat(ioc.getTestKind()).isNull();
                assertThat(ioc.getMessage()).contains("not allowed");
              });
    }

    @Test
    @DisplayName("keeps one record when OpenCTI re-dispatches the same request")
    void given_replay_should_keepExistingRecord() throws Exception {
      String requestId = UUID.randomUUID().toString();
      String body = ctiEvent(requestId, "Domain-Name", "evil.example.com", "dns_resolution");

      String first = receive(STIX_URI, body);
      String second = receive(STIX_URI, body);

      assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("acknowledges a malformed request without recording anything")
    void given_malformedBundle_should_answerOkWithoutRecord() throws Exception {
      long before = scoped(iocValidationRepository::count);
      ObjectNode event = mapper.createObjectNode();
      event.putObject("internal").put("work_id", "work_" + UUID.randomUUID());
      event.putObject("event").put("stix_objects", "{\"type\":\"bundle\",\"objects\":[]}");

      mvc.perform(
              post(STIX_URI + "/process-ioc-validation")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(event.toString())
                  .with(csrf()))
          .andExpect(status().isOk());

      long after = scoped(iocValidationRepository::count);
      assertThat(after).isEqualTo(before);
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
                  post(IOC_VALIDATION_URI + "/" + id + "/reject")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content("{\"ioc_validation_reason\":\"Out of the test window\"}")
                      .with(csrf()))
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
      mvc.perform(
              post(IOC_VALIDATION_URI + "/" + id + "/reject")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{}")
                  .with(csrf()))
          .andExpect(status().isOk());

      mvc.perform(post(IOC_VALIDATION_URI + "/" + id + "/approve").with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuses an approval without asset group to run the tests on")
    void given_noAssetGroup_should_refuseApproval() throws Exception {
      String id = receiveDnsRequest();

      mvc.perform(post(IOC_VALIDATION_URI + "/" + id + "/approve").with(csrf()))
          .andExpect(status().isBadRequest());
      assertThat(scoped(() -> iocValidationRepository.findById(id).orElseThrow()).getStatus())
          .isEqualTo(IocValidationStatus.AWAITING_APPROVAL);
    }

    @Test
    @DisplayName("an approval builds and launches the validation simulation")
    void given_assetGroup_should_launchSimulation() throws Exception {
      AssetGroup assetGroup =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("IOC validation targets"))
              .withAsset(endpointComposer.forEndpoint(EndpointFixture.createEndpoint()))
              .persist()
              .get();
      mvc.perform(
              put(IOC_VALIDATION_URI + "/settings")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      mapper.writeValueAsString(
                          new io.openaev.api.ioc_validation.dto.IocValidationSettingsInput(
                              List.of(
                                  io.openaev.database.model.IocValidationTestKind.DNS_RESOLUTION),
                              "",
                              "",
                              443,
                              assetGroup.getId())))
                  .with(csrf()))
          .andExpect(status().isOk());
      String id = receiveDnsRequest();

      String response =
          mvc.perform(post(IOC_VALIDATION_URI + "/" + id + "/approve").with(csrf()))
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
  }

  @Nested
  @DisplayName("Safety settings")
  class Settings {

    @Test
    @DisplayName("defaults to DNS resolution only on port 443")
    void given_defaults_should_allowDnsOnly() throws Exception {
      String response =
          mvc.perform(get(IOC_VALIDATION_URI + "/settings"))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((List<String>) JsonPath.read(response, "$.ioc_validation_allowed_test_kinds"))
          .containsExactly("DNS_RESOLUTION");
      assertThat((Integer) JsonPath.read(response, "$.ioc_validation_network_port")).isEqualTo(443);
    }

    @Test
    @DisplayName("refuses HTTP HEAD tests without egress proxy")
    void given_httpHeadWithoutProxy_should_refuse() throws Exception {
      mvc.perform(
              put(IOC_VALIDATION_URI + "/settings")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"ioc_validation_allowed_test_kinds\":[\"DNS_RESOLUTION\",\"HTTP_HEAD\"],"
                          + "\"ioc_validation_network_port\":443}")
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("refuses a sinkhole that is not an IP address")
    void given_invalidSinkhole_should_refuse() throws Exception {
      mvc.perform(
              put(IOC_VALIDATION_URI + "/settings")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"ioc_validation_allowed_test_kinds\":[\"NETWORK_TRAFFIC\"],"
                          + "\"ioc_validation_sinkhole_address\":\"sinkhole.internal\","
                          + "\"ioc_validation_network_port\":443}")
                  .with(csrf()))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("stores a consistent widened allow-list")
    void given_validSettings_should_store() throws Exception {
      String response =
          mvc.perform(
                  put(IOC_VALIDATION_URI + "/settings")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(
                          "{\"ioc_validation_allowed_test_kinds\":[\"DNS_RESOLUTION\",\"HTTP_HEAD\",\"NETWORK_TRAFFIC\"],"
                              + "\"ioc_validation_http_proxy_url\":\"http://proxy.internal:3128\","
                              + "\"ioc_validation_sinkhole_address\":\"192.0.2.10\","
                              + "\"ioc_validation_network_port\":8443}")
                      .with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertThat((List<String>) JsonPath.read(response, "$.ioc_validation_allowed_test_kinds"))
          .containsExactlyInAnyOrder("DNS_RESOLUTION", "HTTP_HEAD", "NETWORK_TRAFFIC");
      assertThat((String) JsonPath.read(response, "$.ioc_validation_sinkhole_address"))
          .isEqualTo("192.0.2.10");
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
                  post(IOC_VALIDATION_URI + "/search")
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
    @DisplayName("a request received for tenant A is not visible from another tenant")
    void given_requestOfTenantA_should_notBeVisibleFromDefaultTenant() throws Exception {
      String tenantA = tenantHelper.createTenantWithCurrentUser("ioc-validation-a").getId();
      String id =
          receive(
              TENANT_STIX_URI,
              ctiEvent(
                  UUID.randomUUID().toString(),
                  "Domain-Name",
                  "evil.example.com",
                  "dns_resolution"),
              tenantA);

      mvc.perform(get(TENANT_IOC_VALIDATION_URI + "/{id}", tenantA, id)).andExpect(status().isOk());
      mvc.perform(get(IOC_VALIDATION_URI + "/" + id)).andExpect(status().isNotFound());
      mvc.perform(post(IOC_VALIDATION_URI + "/" + id + "/approve").with(csrf()))
          .andExpect(status().is4xxClientError());
    }
  }
}
