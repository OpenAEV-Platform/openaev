package io.openbas.rest;

import static io.openbas.rest.inject_expectation_trace.InjectExpectationTraceApi.INJECT_EXPECTATION_TRACES_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.model.SecurityPlatform.SECURITY_PLATFORM_TYPE;
import io.openbas.database.repository.*;
import io.openbas.helper.StreamHelper;
import io.openbas.rest.inject_expectation_trace.form.InjectExpectationTraceBulkInsertInput;
import io.openbas.rest.inject_expectation_trace.form.InjectExpectationTraceInput;
import io.openbas.utils.fixtures.AssetFixture;
import io.openbas.utils.fixtures.InjectExpectationFixture;
import io.openbas.utils.fixtures.InjectFixture;
import io.openbas.utils.mockUser.WithMockUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
class InjectExpectationTraceApiTest extends IntegrationTest {

  @Autowired private MockMvc mvc;
  @Autowired private InjectRepository injectRepository;
  @Autowired private CollectorRepository collectorRepository;
  @Autowired private SecurityPlatformRepository securityPlatformRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private InjectExpectationTraceRepository injectExpectationTraceRepository;
  @Autowired private AssetRepository assetRepository;
  @Autowired private ObjectMapper mapper;

  private Collector savedCollector;
  private Inject savedInject;
  private InjectExpectation savedInjectExpectation;
  private Asset savedAsset;
  private SecurityPlatform savedSecurityPlatform;
  private InjectExpectationTrace savedInjectExpectationTrace1;
  private InjectExpectationTrace savedInjectExpectationTrace2;
  private InjectExpectationTrace savedInjectExpectationTrace3Dupe;

  @BeforeEach
  void beforeEach() {
    savedAsset = assetRepository.save(AssetFixture.createDefaultAsset("test"));

    SecurityPlatform sp = new SecurityPlatform();
    sp.setExternalReference(UUID.randomUUID().toString());
    sp.setName("sp-name");
    sp.setSecurityPlatformType(SECURITY_PLATFORM_TYPE.SIEM);
    savedSecurityPlatform = securityPlatformRepository.save(sp);

    Collector collector = new Collector();
    collector.setId(UUID.randomUUID().toString());
    collector.setName("collector-name");
    collector.setSecurityPlatform(savedSecurityPlatform);
    collector.setType("type");
    collector.setExternal(true);
    savedCollector = collectorRepository.save(collector);

    Inject i = InjectFixture.getDefaultInject();
    i.setAssets(List.of(savedAsset));
    savedInject = injectRepository.save(i);

    InjectExpectation ie =
        InjectExpectationFixture.createDetectionInjectExpectation(savedInject, null);
    ie.setAsset(savedAsset);
    savedInjectExpectation = injectExpectationRepository.save(ie);

    InjectExpectationTrace iet1 = new InjectExpectationTrace();
    iet1.setInjectExpectation(savedInjectExpectation);
    iet1.setSecurityPlatform(savedSecurityPlatform);
    iet1.setAlertDate(Instant.now().minus(1, ChronoUnit.SECONDS));
    iet1.setAlertLink("http://test-link.com/1");
    iet1.setAlertName("Test Alert 1");
    savedInjectExpectationTrace1 = injectExpectationTraceRepository.save(iet1);

    InjectExpectationTrace iet2 = new InjectExpectationTrace();
    iet2.setInjectExpectation(savedInjectExpectation);
    iet2.setSecurityPlatform(savedSecurityPlatform);
    iet2.setAlertDate(Instant.now().minus(1, ChronoUnit.SECONDS));
    iet2.setAlertLink("http://test-link.com/2");
    iet2.setAlertName("Test Alert 2");
    savedInjectExpectationTrace2 = injectExpectationTraceRepository.save(iet2);

    // Insert input3 duplicate
    savedInjectExpectationTrace3Dupe = new InjectExpectationTrace();
    savedInjectExpectationTrace3Dupe.setInjectExpectation(savedInjectExpectation);
    savedInjectExpectationTrace3Dupe.setAlertDate(Instant.now());
    savedInjectExpectationTrace3Dupe.setAlertLink("http://fake-link.com/bulk3");
    savedInjectExpectationTrace3Dupe.setSecurityPlatform(savedSecurityPlatform);
    savedInjectExpectationTrace3Dupe.setAlertName("Test Alert Bulk 3 for duplicate test");
    injectExpectationTraceRepository.save(savedInjectExpectationTrace3Dupe);
  }

  @DisplayName("Create an inject expectation trace for a collector")
  @Test
  @WithMockUser(isAdmin = true)
  void createInjectExpectationTraceForCollector_Success() throws Exception {
    // --PREPARE--
    InjectExpectationTraceInput input = new InjectExpectationTraceInput();
    input.setInjectExpectationId(savedInjectExpectation.getId());
    input.setAlertDate(Instant.now());
    input.setAlertLink("http://fake-link.com");
    input.setSourceId(savedCollector.getId());
    input.setAlertName("Test Alert");

    // --EXECUTE--
    String response =
        mvc.perform(
                post(INJECT_EXPECTATION_TRACES_URI)
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(
        savedInjectExpectation.getId(),
        JsonPath.read(response, "$.inject_expectation_trace_expectation"));
    assertEquals(
        savedSecurityPlatform.getId(),
        JsonPath.read(response, "$.inject_expectation_trace_source_id"));
  }

  @DisplayName("Get the traces for a collector")
  @Test
  @WithMockUser(isAdmin = true)
  void getInjectExpectationTracesForCollector() throws Exception {
    // --EXECUTE--
    String response =
        mvc.perform(
                get(INJECT_EXPECTATION_TRACES_URI
                        + "?injectExpectationId="
                        + savedInjectExpectation.getId()
                        + "&sourceId="
                        + savedCollector.getId())
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    ObjectMapper objectMapper = mapper.copy();
    String savedInjectExpectationTrace1Json =
        mapper.writeValueAsString(savedInjectExpectationTrace1);
    String savedInjectExpectationTrace2Json =
        mapper.writeValueAsString(savedInjectExpectationTrace2);
    String savedInjectExpectationTrace3DupeJson =
        mapper.writeValueAsString(savedInjectExpectationTrace3Dupe);
    assertThatJson(response)
        .when(IGNORING_ARRAY_ORDER)
        .isArray()
        .containsAll(
            List.of(
                savedInjectExpectationTrace1Json,
                savedInjectExpectationTrace2Json,
                savedInjectExpectationTrace3DupeJson));
  }

  @DisplayName("Count expectation traces for a collector")
  @Test
  @WithMockUser(isAdmin = true)
  void countInjectExpectationTracesForCollector() throws Exception {
    // --EXECUTE--
    String response =
        mvc.perform(
                get(INJECT_EXPECTATION_TRACES_URI
                        + "/count?injectExpectationId="
                        + savedInjectExpectation.getId()
                        + "&sourceId="
                        + savedSecurityPlatform.getExternalReference()
                        + "&expectationResultSourceType=collector")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(3, Integer.parseInt(response));
  }

  @DisplayName(
      "Count expectation traces for other source than a collector, with an ivalid sourceId given")
  @Test
  @WithMockUser(isAdmin = true)
  void countInjectExpectationTracesForOthers_0() throws Exception {
    // --EXECUTE--
    String response =
        mvc.perform(
                get(INJECT_EXPECTATION_TRACES_URI
                        + "/count?injectExpectationId="
                        + savedInjectExpectation.getId()
                        + "&sourceId="
                        + savedSecurityPlatform.getExternalReference()
                        + "&expectationResultSourceType=other")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(0, Integer.parseInt(response));
  }

  @DisplayName(
      "Count expectation traces for other source than a collector, with a valid sourceId given")
  @Test
  @WithMockUser(isAdmin = true)
  void countInjectExpectationTracesForOthers() throws Exception {
    // --EXECUTE--
    String response =
        mvc.perform(
                get(INJECT_EXPECTATION_TRACES_URI
                        + "/count?injectExpectationId="
                        + savedInjectExpectation.getId()
                        + "&sourceId="
                        + savedSecurityPlatform.getId()
                        + "&expectationResultSourceType=other")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // --ASSERT--
    assertEquals(3, Integer.parseInt(response));
  }

  @DisplayName("Bulk insert of 1 inject expectation trace for a collector")
  @Test
  @WithMockUser(isAdmin = true)
  void bulkInsertInjectExpectationTraceForCollector_Success() throws Exception {
    // --PREPARE--
    InjectExpectationTraceInput input = new InjectExpectationTraceInput();
    input.setInjectExpectationId(savedInjectExpectation.getId());
    input.setAlertDate(Instant.now());
    input.setAlertLink("http://fake-link.com");
    input.setSourceId(savedCollector.getId());
    input.setAlertName("Test Alert Bulk");

    InjectExpectationTraceBulkInsertInput inputBulk = new InjectExpectationTraceBulkInsertInput();
    inputBulk.setExpectationTraces(List.of(input));

    // --EXECUTE--
    mvc.perform(
            post(INJECT_EXPECTATION_TRACES_URI + "/bulk")
                .content(asJsonString(inputBulk))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    // --ASSERT--
    List<InjectExpectationTrace> results =
        StreamHelper.fromIterable(
            injectExpectationTraceRepository.findAll(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.and(criteriaBuilder.like(root.get("alertName"), "%Bulk%"))));
    assertFalse(results.isEmpty());
    assertEquals(2, results.size());
    assertTrue(
        results.stream()
            .anyMatch(
                injectExpectationTrace ->
                    savedInjectExpectationTrace3Dupe
                        .getAlertName()
                        .equals(injectExpectationTrace.getAlertName())));
    assertTrue(
        results.stream()
            .anyMatch(
                injectExpectationTrace ->
                    input.getAlertName().equals(injectExpectationTrace.getAlertName())));
  }

  @DisplayName("Bulk insert of multiple inject expectation trace for a collector")
  @Test
  @WithMockUser(isAdmin = true)
  void bulkInsertMultipleInjectExpectationTraceForCollector_Success() throws Exception {
    // --PREPARE--
    InjectExpectationTraceInput input = new InjectExpectationTraceInput();
    input.setInjectExpectationId(savedInjectExpectation.getId());
    input.setAlertDate(Instant.now());
    input.setAlertLink("http://fake-link.com/bulk");
    input.setSourceId(savedCollector.getId());
    input.setAlertName("Test Alert Bulk");

    InjectExpectationTraceInput input2 = new InjectExpectationTraceInput();
    input2.setInjectExpectationId(savedInjectExpectation.getId());
    input2.setAlertDate(Instant.now());
    input2.setAlertLink("http://fake-link.com/bulk2");
    input2.setSourceId(savedCollector.getId());
    input2.setAlertName("Test Alert Bulk 2");

    InjectExpectationTraceBulkInsertInput inputBulk = new InjectExpectationTraceBulkInsertInput();
    inputBulk.setExpectationTraces(List.of(input, input2));

    // --EXECUTE--
    mvc.perform(
            post(INJECT_EXPECTATION_TRACES_URI + "/bulk")
                .content(asJsonString(inputBulk))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    // --ASSERT--
    List<InjectExpectationTrace> results =
        StreamHelper.fromIterable(
            injectExpectationTraceRepository.findAll(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.and(criteriaBuilder.like(root.get("alertName"), "%Bulk%"))));
    assertFalse(results.isEmpty());
    assertEquals(3, results.size());
  }

  @DisplayName("Bulk insert inject expectation traces for a collector with duplicates")
  @Test
  @WithMockUser(isAdmin = true)
  void bulkInsertInjectExpectationTraceForCollector_SuccessWithDuped() throws Exception {
    // --PREPARE--
    InjectExpectationTraceInput input = new InjectExpectationTraceInput();
    input.setInjectExpectationId(savedInjectExpectation.getId());
    input.setAlertDate(Instant.now());
    input.setAlertLink("http://fake-link.com/bulk");
    input.setSourceId(savedCollector.getId());
    input.setAlertName("Test Alert Bulk");

    InjectExpectationTraceInput input2 = new InjectExpectationTraceInput();
    input2.setInjectExpectationId(savedInjectExpectation.getId());
    input2.setAlertDate(Instant.now());
    input2.setAlertLink("http://fake-link.com/bulk2");
    input2.setSourceId(savedCollector.getId());
    input2.setAlertName("Test Alert Bulk 2");

    InjectExpectationTraceInput input3 = new InjectExpectationTraceInput();
    input3.setInjectExpectationId(savedInjectExpectationTrace3Dupe.getInjectExpectation().getId());
    input3.setAlertDate(savedInjectExpectationTrace3Dupe.getAlertDate());
    input3.setAlertLink(savedInjectExpectationTrace3Dupe.getAlertLink());
    input3.setSourceId(savedInjectExpectationTrace3Dupe.getSecurityPlatform().getId());
    input3.setAlertName(savedInjectExpectationTrace3Dupe.getAlertName());

    InjectExpectationTraceBulkInsertInput inputBulk = new InjectExpectationTraceBulkInsertInput();
    inputBulk.setExpectationTraces(List.of(input, input2, input3));

    // --EXECUTE--
    mvc.perform(
            post(INJECT_EXPECTATION_TRACES_URI + "/bulk")
                .content(asJsonString(inputBulk))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    // --ASSERT--
    List<InjectExpectationTrace> results =
        StreamHelper.fromIterable(
            injectExpectationTraceRepository.findAll(
                (root, query, criteriaBuilder) ->
                    criteriaBuilder.and(criteriaBuilder.like(root.get("alertName"), "%Bulk%"))));
    assertFalse(results.isEmpty());
    assertEquals(3, results.size());
    assertEquals(
        1,
        results.stream()
            .filter(
                injectExpectationTrace ->
                    injectExpectationTrace
                        .getAlertName()
                        .equals(savedInjectExpectationTrace3Dupe.getAlertName()))
            .count());
  }
}
