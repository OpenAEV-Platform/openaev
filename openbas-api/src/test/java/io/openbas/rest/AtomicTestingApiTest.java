package io.openbas.rest;

import static io.openbas.injectors.email.EmailContract.EMAIL_DEFAULT;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.repository.InjectRepository;
import io.openbas.database.repository.InjectStatusRepository;
import io.openbas.database.repository.InjectorContractRepository;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import io.openbas.utils.mockUser.WithMockAdminUser;
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

  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private ExecutorFixture executorFixture;

  @Autowired private MockMvc mvc;
  @Autowired private InjectRepository injectRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private InjectStatusRepository injectStatusRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private ObjectMapper mapper;

  @BeforeEach
  void before() {
    INJECTOR_CONTRACT = injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow();
    Inject injectWithoutPayload = InjectFixture.getInjectForEmailContract(INJECTOR_CONTRACT);
    INJECT_WITHOUT_STATUS = injectRepository.save(injectWithoutPayload);

    Inject injectWithPayload = InjectFixture.getInjectForEmailContract(INJECTOR_CONTRACT);
    INJECT_WITH_STATUS_AND_COMMAND_LINES = injectRepository.save(injectWithPayload);
    InjectStatus injectStatus = InjectStatusFixture.createPendingInjectStatus();
    injectStatus.setInject(injectWithPayload);
    INJECT_STATUS = injectStatusRepository.save(injectStatus);
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
  @WithMockAdminUser
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
  @WithMockAdminUser
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
  @DisplayName("Duplicate and delete an atomic testing")
  @WithMockAdminUser
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
  @WithMockAdminUser
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
  @WithMockAdminUser
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
  @WithMockAdminUser
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
  }

  @Test
  @DisplayName("Get the payload of an atomic testing")
  @WithMockAdminUser
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
  @WithMockAdminUser
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

        List<InjectExpectationResult> resultSet2 =
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
                    .score(40.0)
                    .sourceName("test SIEM")
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
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(20.0)
                    .sourceName("test SIEM")
                    .result("Meh...")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(40.0)
                    .sourceName("test SIEM")
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
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(20.0)
                    .sourceName("test SIEM")
                    .result("Meh...")
                    .build());

        List<InjectExpectationResult> detectionResultSet2 =
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
                    .score(40.0)
                    .sourceName("test SIEM")
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
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(17.0)
                    .sourceName("test SIEM")
                    .result("Meh...")
                    .build());

        List<InjectExpectationResult> preventionResultSet2 =
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
                    .score(32.0)
                    .sourceName("test SIEM")
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
                    .result("Success")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(20.0)
                    .sourceName("test SIEM")
                    .result("Meh...")
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(40.0)
                    .sourceName("test SIEM")
                    .result("Meh better...")
                    .build());

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
                    .build(),
                InjectExpectationResult.builder()
                    .sourceId("siem id")
                    .sourceType("security-platform")
                    .score(32.0)
                    .sourceName("test SIEM")
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
    }
  }
}
