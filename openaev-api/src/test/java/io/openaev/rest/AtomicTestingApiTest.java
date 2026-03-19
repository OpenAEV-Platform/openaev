package io.openaev.rest;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectStatusRepository;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@TestInstance(PER_CLASS)
@Transactional
public class AtomicTestingApiTest extends IntegrationTest {

  public static final String ATOMIC_TESTINGS_URI = "/api/atomic-testings";

  static Inject INJECT_WITH_STATUS_AND_COMMAND_LINES;
  static Inject INJECT_WITHOUT_STATUS;
  static InjectStatus INJECT_STATUS;
  static InjectorContract INJECTOR_CONTRACT;
  static Document DOCUMENT;

  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private ExecutorFixture executorFixture;

  @Autowired private MockMvc mvc;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectStatusRepository injectStatusRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private ObjectMapper mapper;
  @Autowired private InjectorContractFixture injectorContractFixture;

  @BeforeEach
  void before() {
    INJECTOR_CONTRACT = injectorContractFixture.getWellKnownSingleEmailContract();
    Inject injectWithoutPayload = InjectFixture.getInjectForEmailContract(INJECTOR_CONTRACT);
    INJECT_WITHOUT_STATUS = injectRepository.save(injectWithoutPayload);

    Inject injectWithPayload = InjectFixture.getInjectForEmailContract(INJECTOR_CONTRACT);
    INJECT_WITH_STATUS_AND_COMMAND_LINES = injectRepository.save(injectWithPayload);
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    injectStatus.setInject(injectWithPayload);
    INJECT_STATUS = injectStatusRepository.save(injectStatus);

    DOCUMENT = documentRepository.save(DocumentFixture.getDocumentJpeg());
  }

  private InjectComposer.Composer getAtomicTestingWrapper(
      @Nullable InjectStatus injectStatus, @Nullable Executor executor) {
    InjectStatus injectStatusToSet =
        (injectStatus == null) ? InjectStatusFixture.createDraftInjectStatus() : injectStatus;
    Executor executorToRun = (executor == null) ? executorFixture.getDefaultExecutor() : executor;
    return injectComposer
        .forInject(InjectFixture.getDefaultInject())
        .withEndpoint(
            endpointComposer
                .forEndpoint(EndpointFixture.createEndpoint())
                .withAgent(
                    agentComposer.forAgent(AgentFixture.createDefaultAgentSession(executorToRun))))
        .withInjectStatus(injectStatusComposer.forInjectStatus(injectStatusToSet));
  }

  @Test
  @DisplayName("Find an atomic testing without status")
  @WithMockUser(isAdmin = true)
  void findAnAtomicTestingWithoutStatus() throws Exception {
    String response =
        mvc.perform(
                get(ATOMIC_TESTINGS_URI + "/" + INJECT_WITHOUT_STATUS.getId())
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // -- ASSERT --
    assertNotNull(response);
    assertEquals(INJECT_WITHOUT_STATUS.getId(), JsonPath.read(response, "$.inject_id"));
  }

  @Test
  @DisplayName(
      "Find an atomic testing with status, command lines and expectation Results from inject content")
  @WithMockUser(isAdmin = true)
  void findAnAtomicTestingWithStatusAndCommandLines() throws Exception {
    String url = ATOMIC_TESTINGS_URI + "/" + INJECT_WITH_STATUS_AND_COMMAND_LINES.getId();
    String expectedInjectId = INJECT_WITH_STATUS_AND_COMMAND_LINES.getId();
    String expectedExpectationsJson =
        """
        [
          {
            "type": "HUMAN_RESPONSE",
            "avgResult": "UNKNOWN",
            "distribution": []
          }
        ]
        """;

    String response =
        mvc.perform(get(url).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertNotNull(response, "Response should not be null");

    String actualInjectId = JsonPath.read(response, "$.inject_id");
    assertEquals(expectedInjectId, actualInjectId);

    Object actualExpectations = JsonPath.read(response, "$.inject_expectation_results");
    String actualExpectationsJson = mapper.writeValueAsString(actualExpectations);

    // Match Expectation results
    assertEquals(
        mapper.readTree(expectedExpectationsJson), mapper.readTree(actualExpectationsJson));
  }

  @Test
  @DisplayName("Create and upadte an atomic testing")
  @WithMockUser(isAdmin = true)
  void createAndUpdateAnAtomicTesting() throws Exception {
    String response =
        mvc.perform(
                post(ATOMIC_TESTINGS_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content(
                        asJsonString(InjectFixture.createAtomicTesting("test", DOCUMENT.getId()))))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertNotNull(response);
    String newInjectId = JsonPath.read(response, "$.inject_id");
    response =
        mvc.perform(get(ATOMIC_TESTINGS_URI + "/" + newInjectId).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(newInjectId, JsonPath.read(response, "$.inject_id"));
    assertEquals("test", JsonPath.read(response, "$.inject_title"));
    List<String> documentIds = JsonPath.read(response, "$.injects_documents");
    assertEquals(1, documentIds.size());

    response =
        mvc.perform(
                put(ATOMIC_TESTINGS_URI + "/" + newInjectId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content(asJsonString(InjectFixture.createAtomicTesting("test2", null))))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertNotNull(response);
    response =
        mvc.perform(get(ATOMIC_TESTINGS_URI + "/" + newInjectId).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(newInjectId, JsonPath.read(response, "$.inject_id"));
    assertEquals("test2", JsonPath.read(response, "$.inject_title"));
    documentIds = JsonPath.read(response, "$.injects_documents");
    assertEquals(0, documentIds.size());
  }

  @Test
  @DisplayName("Duplicate and delete an atomic testing")
  @WithMockUser(isAdmin = true)
  void duplicateAndDeleteAtomicTesting() throws Exception {
    // Duplicate
    String response =
        mvc.perform(
                post(ATOMIC_TESTINGS_URI + "/" + INJECT_WITHOUT_STATUS.getId() + "/duplicate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertNotNull(response);
    // Assert duplicate
    String newInjectId = JsonPath.read(response, "$.inject_id");
    response =
        mvc.perform(get(ATOMIC_TESTINGS_URI + "/" + newInjectId).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertEquals(newInjectId, JsonPath.read(response, "$.inject_id"));
    // Delete
    mvc.perform(delete(ATOMIC_TESTINGS_URI + "/" + newInjectId).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
    // Assert delete
    mvc.perform(get(ATOMIC_TESTINGS_URI + "/" + newInjectId).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError());
  }

  @Test
  @DisplayName("Launch an Atomic Testing")
  @WithMockUser(isAdmin = true)
  void launchAtomicTesting() throws Exception {
    Inject atomicTesting = getAtomicTestingWrapper(null, null).persist().get();

    entityManager.flush();
    entityManager.clear();

    mvc.perform(post(ATOMIC_TESTINGS_URI + "/" + atomicTesting.getId() + "/launch"))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.inject_status.status_name").value("QUEUING"));
  }

  @Test
  @DisplayName("Relaunch an Atomic Testing")
  @WithMockUser(isAdmin = true)
  void relaunchAtomicTesting() throws Exception {
    Inject atomicTesting =
        getAtomicTestingWrapper(InjectStatusFixture.createQueuingInjectStatus(), null)
            .persist()
            .get();

    String relaunchedInject =
        mvc.perform(post(ATOMIC_TESTINGS_URI + "/" + atomicTesting.getId() + "/relaunch"))
            .andExpect(status().is2xxSuccessful())
            .andExpect(jsonPath("$.inject_status.status_name").value("QUEUING"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String relaunchedInjectId = JsonPath.read(relaunchedInject, "$.inject_id");
    mvc.perform(get(ATOMIC_TESTINGS_URI + "/" + atomicTesting.getId()))
        .andExpect(status().is4xxClientError());
    mvc.perform(get(ATOMIC_TESTINGS_URI + "/" + relaunchedInjectId))
        .andExpect(status().is2xxSuccessful());
  }

  @Nested
  @DisplayName("Lock Atomic testing EE feature")
  @WithMockUser(isAdmin = true)
  class LockAtomicTestingEEFeature {

    @Test
    @DisplayName("Throw license restricted error when launch with crowdstrike")
    void given_crowdstrike_should_not_LaunchAtomicTesting() throws Exception {
      Inject atomicTesting =
          getAtomicTestingWrapper(null, executorFixture.getCrowdstrikeExecutor()).persist().get();

      mvc.perform(post(ATOMIC_TESTINGS_URI + "/" + atomicTesting.getId() + "/launch"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Throw license restricted error when relaunch with Tanium")
    void given_tanium_should_not_relaunchAtomicTesting() throws Exception {
      Inject atomicTesting =
          getAtomicTestingWrapper(
                  InjectStatusFixture.createQueuingInjectStatus(),
                  executorFixture.getTaniumExecutor())
              .persist()
              .get();

      mvc.perform(post(ATOMIC_TESTINGS_URI + "/" + atomicTesting.getId() + "/relaunch"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }

    @Test
    @DisplayName("Throw license restricted error when relaunch with Sentinel One")
    void given_sentinelone_should_not_relaunchAtomicTesting() throws Exception {
      Inject atomicTesting =
          getAtomicTestingWrapper(
                  InjectStatusFixture.createQueuingInjectStatus(),
                  executorFixture.getSentineloneExecutor())
              .persist()
              .get();

      mvc.perform(post(ATOMIC_TESTINGS_URI + "/" + atomicTesting.getId() + "/relaunch"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.message").value("LICENSE_RESTRICTION"));
    }
  }

  @Test
  @DisplayName("Get the payload of an atomic testing")
  @WithMockUser(isAdmin = true)
  void findPayloadOutputByInjectId() throws Exception {
    String response =
        mvc.perform(
                get(ATOMIC_TESTINGS_URI
                        + "/"
                        + INJECT_WITH_STATUS_AND_COMMAND_LINES.getId()
                        + "/payload")
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // -- ASSERT --
    assertEquals("", response);
  }

  @Nested
  @DisplayName("Expectation results computation")
  @WithMockUser(isAdmin = true)
  public class ExpectationResultsComputation {

    @Nested
    @DisplayName("When target has multiple sets of expectations and must be merged")
    public class WhenTargetHasMultipleSetsOfExpectationsAndMerged {

      @Test
      @DisplayName("Merged expectations have superset of results of all expectations of same type")
      public void mergedExpectationsHaveSupersetOfExpectationsAndMerged() throws Exception {
        List<InjectExpectationResult> resultSet1 =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .score(100.0)
                    .sourceName("test collector")
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(20.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh...")
                    .build());

        List<InjectExpectationResult> resultSet2 =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(100.0)
                    .sourceName("test collector")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(40.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh better...")
                    .build());

        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        InjectExpectation detection1 =
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                InjectExpectation.EXPECTATION_TYPE.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        detection1.setResults(resultSet1);
        InjectExpectation detection2 =
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                InjectExpectation.EXPECTATION_TYPE.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        detection2.setResults(resultSet2);
        InjectComposer.Composer injectWrapper =
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withEndpoint(endpointWrapper)
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(detection1)
                        .withEndpoint(endpointWrapper))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(detection2)
                        .withEndpoint(endpointWrapper));

        injectWrapper.persist();

        entityManager.flush();
        entityManager.flush();

        String response =
            mvc.perform(
                    get(ATOMIC_TESTINGS_URI
                            + "/"
                            + injectWrapper.get().getId()
                            + "/target_results/"
                            + endpointWrapper.get().getId()
                            + "/types/ASSETS/merged")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<InjectExpectationResult> expectedSuperset =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(100.0)
                    .sourceName("test collector")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(20.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh...")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(40.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh better...")
                    .build());

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("[0].inject_expectation_results")
            .isEqualTo(mapper.writeValueAsString(expectedSuperset));
        assertThatJson(response).node("[0].inject_expectation_score").isEqualTo("100.0");
      }

      @Test
      @DisplayName("Merged expectations are separated by type")
      public void mergedExpectationsAreSeparatedByType() throws Exception {

        // DETECTION
        List<InjectExpectationResult> detectionResultSet1 =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(100.0)
                    .sourceName("test collector")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(20.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh...")
                    .build());

        List<InjectExpectationResult> detectionResultSet2 =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(100.0)
                    .sourceName("test collector")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(40.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh better...")
                    .build());

        InjectExpectation detection1 =
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                InjectExpectation.EXPECTATION_TYPE.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        detection1.setResults(detectionResultSet1);
        detection1.setExpectedScore(100.0);
        InjectExpectation detection2 =
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                InjectExpectation.EXPECTATION_TYPE.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        detection2.setResults(detectionResultSet2);
        detection2.setExpectedScore(100.0);

        // PREVENTION

        List<InjectExpectationResult> preventionResultSet1 =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(0.0)
                    .sourceName("test collector")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(17.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh...")
                    .build());

        List<InjectExpectationResult> preventionResultSet2 =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(0.0)
                    .sourceName("test collector")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(32.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh better...")
                    .build());

        InjectExpectation prevention1 =
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                InjectExpectation.EXPECTATION_TYPE.PREVENTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        prevention1.setResults(preventionResultSet1);
        prevention1.setExpectedScore(100.0);
        InjectExpectation prevention2 =
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                InjectExpectation.EXPECTATION_TYPE.PREVENTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        prevention2.setResults(preventionResultSet2);
        prevention2.setExpectedScore(100.0);

        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        InjectComposer.Composer injectWrapper =
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withEndpoint(endpointWrapper)
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(detection1)
                        .withEndpoint(endpointWrapper))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(detection2)
                        .withEndpoint(endpointWrapper))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(prevention1)
                        .withEndpoint(endpointWrapper))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(prevention2)
                        .withEndpoint(endpointWrapper));

        injectWrapper.persist();

        entityManager.flush();
        entityManager.flush();

        String response =
            mvc.perform(
                    get(ATOMIC_TESTINGS_URI
                            + "/"
                            + injectWrapper.get().getId()
                            + "/target_results/"
                            + endpointWrapper.get().getId()
                            + "/types/ASSETS/merged")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<InjectExpectationResult> expectedDetectionSuperset =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(100.0)
                    .sourceName("test collector")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(20.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh...")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(40.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh better...")
                    .build());

        List<InjectExpectationResult> expectedPreventionSuperset =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(0.0)
                    .sourceName("test collector")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(17.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh...")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(32.0)
                    .sourceName("test SIEM")
                    .sourcePlatform(SecurityPlatform.SECURITY_PLATFORM_TYPE.EDR.name())
                    .result("Meh better...")
                    .build());

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("[1].inject_expectation_results")
            .isEqualTo(mapper.writeValueAsString(expectedDetectionSuperset));
        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("[0].inject_expectation_results")
            .isEqualTo(mapper.writeValueAsString(expectedPreventionSuperset));
        assertThatJson(response).node("[1].inject_expectation_score").isEqualTo("100.0");
        // assertThatJson(response).node("[0].inject_expectation_score").isEqualTo("0.0");
      }

      @Test
      @DisplayName("Merged expectations agents from an asset")
      public void mergedExpectationsAgentsFromAsset() throws Exception {

        // DETECTION
        List<InjectExpectationResult> detectionResultSet1 =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(100.0)
                    .sourceName("test collector")
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(20.0)
                    .sourceName("test SIEM")
                    .result("Meh...")
                    .build());

        InjectExpectation detection1 =
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                InjectExpectation.EXPECTATION_TYPE.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        detection1.setResults(detectionResultSet1);
        detection1.setExpectedScore(100.0);

        // PREVENTION
        List<InjectExpectationResult> preventionResultSet1 =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(0.0)
                    .sourceName("test collector")
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(17.0)
                    .sourceName("test SIEM")
                    .result("Meh...")
                    .build());

        InjectExpectation prevention1 =
            InjectExpectationFixture.createExpectationWithTypeAndStatus(
                InjectExpectation.EXPECTATION_TYPE.PREVENTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS);
        prevention1.setResults(preventionResultSet1);
        prevention1.setExpectedScore(100.0);

        AgentComposer.Composer agentWrapper =
            agentComposer.forAgent(AgentFixture.createDefaultAgentService());
        EndpointComposer.Composer endpointWrapper =
            endpointComposer
                .forEndpoint(EndpointFixture.createEndpoint())
                .withAgent(agentWrapper)
                .persist();
        InjectComposer.Composer injectWrapper =
            injectComposer
                .forInject(InjectFixture.getDefaultInject())
                .withEndpoint(endpointWrapper)
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(detection1)
                        .withEndpoint(endpointWrapper)
                        .withAgent(agentWrapper))
                .withExpectation(
                    injectExpectationComposer
                        .forExpectation(prevention1)
                        .withEndpoint(endpointWrapper)
                        .withAgent(agentWrapper));

        injectWrapper.persist();

        entityManager.flush();
        entityManager.flush();

        String responseDetection =
            mvc.perform(
                    get(ATOMIC_TESTINGS_URI
                            + "/"
                            + injectWrapper.get().getId()
                            + "/target_results/"
                            + endpointWrapper.get().getId()
                            + "/asset_with_agents?expectationType="
                            + InjectExpectation.EXPECTATION_TYPE.DETECTION)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<InjectExpectationResult> expectedDetectionSuperset =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(100.0)
                    .sourceName("test collector")
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(20.0)
                    .sourceName("test SIEM")
                    .result("Meh...")
                    .build());

        assertThatJson(responseDetection)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("[0].inject_expectation_results")
            .isEqualTo(mapper.writeValueAsString(expectedDetectionSuperset));
        assertThatJson(responseDetection)
            .node("[0].inject_expectation_type")
            .isEqualTo(InjectExpectation.EXPECTATION_TYPE.DETECTION.name());
        assertThatJson(responseDetection).node("[0].inject_expectation_score").isEqualTo("100.0");

        String responsePrevention =
            mvc.perform(
                    get(ATOMIC_TESTINGS_URI
                            + "/"
                            + injectWrapper.get().getId()
                            + "/target_results/"
                            + endpointWrapper.get().getId()
                            + "/asset_with_agents?expectationType="
                            + InjectExpectation.EXPECTATION_TYPE.PREVENTION)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<InjectExpectationResult> expectedPreventionSuperset =
            List.of(
                InjectExpectationResult.builder()
                    .sourceId("collector id")
                    .sourceType("collector")
                    .score(0.0)
                    .sourceName("test collector")
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(17.0)
                    .sourceName("test SIEM")
                    .result("Meh...")
                    .build());

        assertThatJson(responsePrevention)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("[0].inject_expectation_results")
            .isEqualTo(mapper.writeValueAsString(expectedPreventionSuperset));
        assertThatJson(responsePrevention)
            .node("[0].inject_expectation_type")
            .isEqualTo(InjectExpectation.EXPECTATION_TYPE.PREVENTION.name());
        assertThatJson(responsePrevention).node("[0].inject_expectation_score").isEqualTo("100.0");
      }
    }
  }
}
