package io.openaev.rest.inject;

import static io.openaev.config.SessionHelper.currentUser;
import static io.openaev.database.model.ExerciseStatus.RUNNING;
import static io.openaev.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_END_DATE;
import static io.openaev.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_START_DATE;
import static io.openaev.database.model.InjectorContract.*;
import static io.openaev.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openaev.rest.atomic_testing.AtomicTestingApi.ATOMIC_TESTING_URI;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openaev.rest.inject.InjectApi.INJECT_URI;
import static io.openaev.rest.inject.service.ExecutableInjectService.formatMultilineCommand;
import static io.openaev.rest.inject.service.ExecutableInjectService.replaceCmdVariables;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.InjectFixture.getInjectForEmailContract;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.execution.ExecutableInject;
import io.openaev.executors.Executor;
import io.openaev.injector_contract.ContractTargetedProperty;
import io.openaev.injector_contract.fields.ContractFieldType;
import io.openaev.integration.Manager;
import io.openaev.integration.impl.injectors.email.EmailInjectorIntegrationFactory;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.rest.atomic_testing.form.ExecutionTraceOutput;
import io.openaev.rest.atomic_testing.form.InjectStatusOutput;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exercise.service.ExerciseService;
import io.openaev.rest.helper.queue.BatchQueueService;
import io.openaev.rest.inject.form.*;
import io.openaev.rest.inject.service.BatchingInjectStatusService;
import io.openaev.rest.inject.service.InjectStatusService;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TargetType;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.helpers.InjectTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utilstest.KeepRabbit;
import jakarta.annotation.Resource;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.persistence.EntityManager;
import jakarta.servlet.ServletException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.javacrumbs.jsonunit.core.Option;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ResourceUtils;

@ExtendWith(MockitoExtension.class)
@Transactional
class InjectApiTest extends IntegrationTest {

  static Exercise EXERCISE;
  static Scenario SCENARIO;
  static Document DOCUMENT1;
  static Document DOCUMENT2;
  static Team TEAM;
  static Agent AGENT;
  @Resource protected ObjectMapper mapper;
  @Autowired private MockMvc mvc;
  @Autowired private EntityManager entityManager;
  @Autowired private ScenarioService scenarioService;
  @Autowired private ExerciseService exerciseService;
  @SpyBean private InjectStatusService injectStatusService;

  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DetectionRemediationComposer detectionRemediationComposer;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;
  @Autowired private DomainComposer domainComposer;

  @Autowired private ExerciseRepository exerciseRepository;
  @Autowired private AgentRepository agentRepository;
  @SpyBean private Executor executor;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private ScenarioRepository scenarioRepository;
  @Autowired private InjectRepository injectRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private CommunicationRepository communicationRepository;
  @Autowired private InjectExpectationRepository injectExpectationRepository;
  @Autowired private TeamRepository teamRepository;
  @Autowired private FindingRepository findingRepository;
  @Autowired private UserRepository userRepository;
  @Resource private ObjectMapper objectMapper;
  @MockBean private JavaMailSender javaMailSender;

  @Autowired private InjectTestHelper injectTestHelper;
  @Autowired private InjectExpectationComposer injectExpectationComposer;
  @Autowired private InjectorContractFixture injectorContractFixture;
  @Autowired private EmailInjectorIntegrationFactory emailInjectorIntegrationFactory;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;

  @BeforeEach
  void beforeEach() throws Exception {
    new Manager(List.of(emailInjectorIntegrationFactory, openaevInjectorIntegrationFactory))
        .monitorIntegrations();

    Scenario scenario = new Scenario();
    scenario.setName("Scenario name");
    scenario.setFrom("test@test.com");
    scenario.setReplyTos(new ArrayList<>(List.of("test@test.com")));
    SCENARIO = scenarioService.createScenario(scenario);

    Exercise exercise = new Exercise();
    exercise.setName("Exercise name");
    exercise.setStart(Instant.now());
    exercise.setFrom("test@test.com");
    exercise.setReplyTos(new ArrayList<>(List.of("test@test.com")));
    exercise.setStatus(RUNNING);
    EXERCISE = exerciseService.createExercise(exercise);

    Document document1 = new Document();
    document1.setName("Document 1");
    document1.setType("image");
    Document document2 = new Document();
    document2.setName("Document 2");
    document2.setType("pdf");
    DOCUMENT1 = documentRepository.save(document1);
    DOCUMENT2 = documentRepository.save(document2);

    Team team = new Team();
    team.setName("team");
    TEAM = teamRepository.save(team);

    Endpoint endpoint = EndpointFixture.createEndpoint();
    Endpoint endpointSaved = endpointRepository.save(endpoint);
    Agent agent = AgentFixture.createDefaultAgentService();
    agent.setAsset(endpointSaved);
    AGENT = agentRepository.save(agent);

    domainComposer.reset();
  }

  // BULK DELETE
  @DisplayName("Delete list of injects for scenario")
  @Test
  @WithMockUser(isAdmin = true)
  void deleteInjectsForScenarioTest() throws Exception {
    // -- PREPARE --
    Inject injectForScenario1 = new Inject();
    injectForScenario1.setTitle("Inject for scenario 1");
    injectForScenario1.setCreatedAt(Instant.now());
    injectForScenario1.setUpdatedAt(Instant.now());
    injectForScenario1.setDependsDuration(5L);

    injectForScenario1.setInjectorContract(
        injectorContractFixture.getWellKnownSingleEmailContract());
    injectForScenario1.setScenario(SCENARIO);
    Inject createdInject = injectRepository.save(injectForScenario1);

    InjectDocument injectDocument4 = new InjectDocument();
    injectDocument4.setInject(createdInject);
    injectDocument4.setDocument(DOCUMENT2);
    createdInject.setDocuments(new ArrayList<>(List.of(injectDocument4)));
    createdInject = injectRepository.save(injectForScenario1);

    injectExpectationRepository.save(
        InjectExpectationFixture.createArticleInjectExpectation(TEAM, createdInject));

    // -- ASSERT --
    assertTrue(
        injectRepository.existsByIdWithoutLoading(createdInject.getId()),
        "The inject should exist from the database");
    assertFalse(
        injectRepository.findByScenarioId(SCENARIO.getId()).isEmpty(),
        "There should be injects for the scenario in the database");
    assertFalse(
        injectExpectationRepository
            .findAllByInjectAndTeam(createdInject.getId(), TEAM.getId())
            .isEmpty(),
        "There should be expectations for the scenario in the database");

    // -- PREPARE --
    InjectBulkProcessingInput input = new InjectBulkProcessingInput();
    input.setInjectIDsToProcess(List.of(createdInject.getId()));
    input.setSimulationOrScenarioId(SCENARIO.getId());

    // -- EXECUTE --
    mvc.perform(
            delete(INJECT_URI).content(asJsonString(input)).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    // -- ASSERT --
    assertFalse(
        injectRepository.existsById(createdInject.getId()),
        "The inject should be deleted from the database");
    assertTrue(
        scenarioRepository.existsById(SCENARIO.getId()),
        "The scenario should still exist in the database");
    assertTrue(
        injectRepository.findByScenarioId(SCENARIO.getId()).isEmpty(),
        "There should be no injects for the scenario in the database");
    assertTrue(
        documentRepository.existsById(DOCUMENT2.getId()),
        "The document should still exist in the database");
    assertTrue(
        injectExpectationRepository
            .findAllByInjectAndTeam(createdInject.getId(), TEAM.getId())
            .isEmpty(),
        "There should be no expectations related to the inject in the database");
  }

  // -- EXERCISES --

  @DisplayName("Add an inject for simulation")
  @Test
  @WithMockUser(isAdmin = true)
  void addInjectForSimulationTest() throws Exception {
    // -- PREPARE --
    InjectInput input = new InjectInput();
    input.setTitle("Test inject");
    input.setInjectorContract(EMAIL_DEFAULT);
    input.setDependsDuration(0L);

    // -- EXECUTE --
    String response =
        mvc.perform(
                post(EXERCISE_URI + "/" + EXERCISE.getId() + "/injects")
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals("Test inject", JsonPath.read(response, "$.inject_title"));
  }

  @DisplayName("Update inject for simulation")
  @Test
  @WithMockUser(isAdmin = true)
  void updateInjectForSimulationTest() throws Exception {
    // -- PREPARE --
    InjectInput injectInput = new InjectInput();
    injectInput.setTitle("Test inject");
    injectInput.setDependsDuration(0L);
    Inject inject = injectInput.toInject(injectorContractFixture.getWellKnownSingleEmailContract());
    Inject savedInject = injectRepository.save(inject);

    Inject injectToUpdate = injectRepository.findById(savedInject.getId()).orElseThrow();
    InjectInput input = new InjectInput();
    String injectTitle = "A new title";
    input.setTitle(injectTitle);
    input.setDependsDuration(inject.getDependsDuration());

    // -- EXECUTE --
    String response =
        mvc.perform(
                put(INJECT_URI + "/" + EXERCISE.getId() + "/" + injectToUpdate.getId())
                    .content(asJsonString(input))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals(injectTitle, JsonPath.read(response, "$.inject_title"));
  }

  @DisplayName("Execute an email inject for exercise")
  @Test
  @WithMockUser(isAdmin = true)
  void executeEmailInjectForExerciseTest() throws Exception {
    // -- PREPARE --
    InjectorContract injectorContract = injectorContractFixture.getWellKnownSingleEmailContract();
    Inject inject = getInjectForEmailContract(injectorContract);
    User user = userRepository.findById(currentUser().getId()).orElseThrow();
    DirectInjectInput input = new DirectInjectInput();
    input.setTitle(inject.getTitle());
    input.setDescription(inject.getDescription());
    input.setInjectorContract(inject.getInjectorContract().orElseThrow().getId());
    input.setUserIds(List.of(user.getId()));
    ObjectNode content = objectMapper.createObjectNode();
    content.set("subject", objectMapper.convertValue("Subject", JsonNode.class));
    content.set("body", objectMapper.convertValue("Test body", JsonNode.class));
    content.set("expectationType", objectMapper.convertValue("none", JsonNode.class));
    input.setContent(content);

    MockMultipartFile inputJson =
        new MockMultipartFile(
            "input", null, "application/json", objectMapper.writeValueAsString(input).getBytes());

    // Getting a test file
    File testFile = ResourceUtils.getFile("classpath:xls-test-files/test_file_1.xlsx");
    InputStream in = new FileInputStream(testFile);
    MockMultipartFile fileJson =
        new MockMultipartFile("file", "my-awesome-file.xls", "application/xlsx", in.readAllBytes());

    // Mock the behavior of JavaMailSender
    ArgumentCaptor<MimeMessage> mimeMessageArgumentCaptor =
        ArgumentCaptor.forClass(MimeMessage.class);
    doNothing().when(javaMailSender).send(ArgumentMatchers.any(SimpleMailMessage.class));
    when(javaMailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

    // -- EXECUTE --
    String response =
        mvc.perform(
                multipart(EXERCISE_URI + "/" + EXERCISE.getId() + "/inject")
                    .file(inputJson)
                    .file(fileJson))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals("SUCCESS", JsonPath.read(response, "$.status_name"));
    ArgumentCaptor<ExecutableInject> executableInjectCaptor =
        ArgumentCaptor.forClass(ExecutableInject.class);
    verify(executor).execute(executableInjectCaptor.capture());

    verify(javaMailSender).send(mimeMessageArgumentCaptor.capture());
    assertEquals("Subject", mimeMessageArgumentCaptor.getValue().getSubject());

    // -- THEN ---
    userRepository.delete(user);
  }

  @DisplayName("Execute an email inject for exercise with no team")
  @Test
  @WithMockUser(isAdmin = true)
  void executeEmailInjectForExerciseWithNoTeam() throws Exception {
    // -- PREPARE --
    InjectorContract injectorContract = injectorContractFixture.getWellKnownSingleEmailContract();
    Inject inject = getInjectForEmailContract(injectorContract);

    DirectInjectInput input = new DirectInjectInput();
    input.setTitle(inject.getTitle());
    input.setDescription(inject.getDescription());
    input.setInjectorContract(inject.getInjectorContract().orElseThrow().getId());
    ObjectNode content = objectMapper.createObjectNode();
    content.set("subject", objectMapper.convertValue("Subject", JsonNode.class));
    content.set("body", objectMapper.convertValue("Test body", JsonNode.class));
    content.set("expectationType", objectMapper.convertValue("none", JsonNode.class));
    input.setContent(content);

    MockMultipartFile inputJson =
        new MockMultipartFile(
            "input", null, "application/json", objectMapper.writeValueAsString(input).getBytes());

    // -- EXECUTE --
    String response =
        mvc.perform(multipart(EXERCISE_URI + "/" + EXERCISE.getId() + "/inject").file(inputJson))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertEquals("ERROR", JsonPath.read(response, "$.status_name"));
    assertEquals(
        "Email needs at least one user",
        JsonPath.read(response, "$.status_traces[0].execution_message"));
  }

  @DisplayName("Execute an email inject for exercise with no content")
  @Test
  @WithMockUser(isAdmin = true)
  void executeEmailInjectForExerciseWithNoContentTest() throws Exception {
    // -- PREPARE --
    InjectorContract injectorContract = injectorContractFixture.getWellKnownSingleEmailContract();
    Inject inject = getInjectForEmailContract(injectorContract);

    DirectInjectInput input = new DirectInjectInput();
    input.setTitle(inject.getTitle());
    input.setDescription(inject.getDescription());
    input.setInjectorContract(inject.getInjectorContract().orElseThrow().getId());

    MockMultipartFile inputJson =
        new MockMultipartFile(
            "input", null, "application/json", objectMapper.writeValueAsString(input).getBytes());

    // -- EXECUTION --
    String response =
        mvc.perform(multipart(EXERCISE_URI + "/" + EXERCISE.getId() + "/inject").file(inputJson))
            .andExpect(status().is2xxSuccessful())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // -- ASSERT --
    assertNotNull(response);
    assertTrue(JsonPath.read(response, "$.status_traces").toString().contains("Inject is empty"));
  }

  // -- BULK DELETE --

  @DisplayName("Delete list of inject for exercise")
  @Test
  @WithMockUser(isAdmin = true)
  void deleteInjectsForExerciseTest() throws Exception {
    // -- PREPARE --
    Inject injectForExercise1 = new Inject();
    injectForExercise1.setTitle("Inject for exercise 1");
    injectForExercise1.setCreatedAt(Instant.now());
    injectForExercise1.setUpdatedAt(Instant.now());
    injectForExercise1.setDependsDuration(1L);
    injectForExercise1.setExercise(EXERCISE);

    Inject injectForExercise2 = new Inject();
    injectForExercise2.setTitle("Inject for exercise 2");
    injectForExercise2.setCreatedAt(Instant.now());
    injectForExercise2.setUpdatedAt(Instant.now());
    injectForExercise2.setDependsDuration(2L);
    injectForExercise2.setExercise(EXERCISE);

    Inject createdInject1 = injectRepository.save(injectForExercise1);
    Inject createdInject2 = injectRepository.save(injectForExercise2);

    InjectDocument injectDocument1 = new InjectDocument();
    injectDocument1.setInject(createdInject1);
    injectDocument1.setDocument(DOCUMENT1);

    InjectDocument injectDocument2 = new InjectDocument();
    injectDocument2.setInject(createdInject1);
    injectDocument2.setDocument(DOCUMENT2);

    InjectDocument injectDocument3 = new InjectDocument();
    injectDocument3.setInject(createdInject2);
    injectDocument3.setDocument(DOCUMENT1);

    createdInject1.setDocuments(new ArrayList<>(List.of(injectDocument1, injectDocument2)));
    createdInject2.setDocuments(new ArrayList<>(List.of(injectDocument3)));

    injectRepository.save(createdInject1);
    injectRepository.save(createdInject2);

    Communication communication = new Communication();
    communication.setInject(createdInject1);
    communication.setIdentifier("messageId");
    communication.setFrom("test@test.com");
    communication.setTo("test@test.com");
    communication.setSentAt(Instant.now());
    communication.setReceivedAt(Instant.now());
    Communication createdCommunication = communicationRepository.save(communication);

    injectExpectationRepository.save(
        InjectExpectationFixture.createPreventionInjectExpectation(createdInject1, AGENT));
    injectExpectationRepository.save(
        InjectExpectationFixture.createDetectionInjectExpectation(createdInject1, AGENT));
    injectExpectationRepository.save(
        InjectExpectationFixture.createManualInjectExpectation(TEAM, createdInject2));

    // -- ASSERT --
    assertTrue(
        injectRepository.existsById(createdInject1.getId()),
        "The inject should exist from the database");
    assertFalse(
        injectRepository.findByExerciseId(EXERCISE.getId()).isEmpty(),
        "There should be injects for the exercise in the database");
    assertEquals(1, communicationRepository.findByInjectId(createdInject1.getId()).size());
    assertEquals(
        2,
        injectExpectationRepository
            .findAllByInjectAndAgent(createdInject1.getId(), AGENT.getId())
            .size());
    assertEquals(
        1,
        injectExpectationRepository
            .findAllByInjectAndTeam(createdInject2.getId(), TEAM.getId())
            .size());

    // -- PREPARE --
    InjectBulkProcessingInput input = new InjectBulkProcessingInput();
    input.setInjectIDsToProcess(List.of(createdInject1.getId(), createdInject2.getId()));
    input.setSimulationOrScenarioId(EXERCISE.getId());

    // -- EXECUTE --
    mvc.perform(
            delete(INJECT_URI).content(asJsonString(input)).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());

    // -- ASSERT --
    assertFalse(
        injectRepository.existsById(createdInject1.getId()),
        "The inject should be deleted from the database");
    assertFalse(
        injectRepository.existsById(createdInject2.getId()),
        "The inject should be deleted from the database");
    assertTrue(
        exerciseRepository.existsById(EXERCISE.getId()),
        "The exercise should still exist in the database");
    assertTrue(
        injectRepository.findByExerciseId(EXERCISE.getId()).isEmpty(),
        "There should be no injects for the exercise in the database");
    assertTrue(
        documentRepository.existsById(DOCUMENT1.getId()),
        "The document should still exist in the database");
    assertFalse(
        communicationRepository.existsById(createdCommunication.getId()),
        "The communication should be deleted from the database");
    assertTrue(
        injectExpectationRepository
            .findAllByInjectAndTeam(createdInject1.getId(), TEAM.getId())
            .isEmpty(),
        "There should be no expectations related to the inject in the database");
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @Transactional
  @DisplayName("Retrieving executable payloads injects")
  @KeepRabbit
  class RetrievingExecutablePayloadInject {

    @DisplayName("Get encoded command payload with arguments")
    @Test
    void getExecutablePayloadInjectWithArguments() throws Exception {
      // -- PREPARE --
      PayloadPrerequisite prerequisite = new PayloadPrerequisite();
      prerequisite.setGetCommand("cd ./src");
      prerequisite.setExecutor("bash");
      Command payloadCommand =
          PayloadFixture.createCommand(
              "bash", "echo command name #{arg_value}", List.of(prerequisite), "echo cleanup cmd");

      String argValue = "Hello world";
      Map<String, Object> payloadArguments = new HashMap<>();
      payloadArguments.put("arg_value", argValue);

      Inject injectSaved =
          injectComposer
              .forInject(InjectFixture.createInjectWithPayloadArg(payloadArguments))
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(InjectorFixture.createDefaultPayloadInjector())
                      .withPayload(
                          payloadComposer
                              .forPayload(payloadCommand)
                              .withDomain(
                                  domainComposer.forDomain(DomainFixture.getRandomDomain()))))
              .persist()
              .get();

      // TODO: the setup should allow for this not be stubbed
      doNothing()
          .when(injectStatusService)
          .addStartImplantExecutionTraceByInject(any(), any(), any(), any());

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECT_URI + "/" + injectSaved.getId() + "/fakeId/executable-payload")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertNotNull(response);
      // Verify prerequisite command
      String expectedPrerequisiteCmdEncoded =
          Base64.getEncoder().encodeToString(prerequisite.getGetCommand().getBytes());
      assertEquals(
          expectedPrerequisiteCmdEncoded,
          JsonPath.read(response, "$.payload_prerequisites[0].get_command"));

      // Verify cleanup command
      String expectedCleanupCmdEncoded =
          Base64.getEncoder().encodeToString(payloadCommand.getCleanupCommand().getBytes());
      assertEquals(expectedCleanupCmdEncoded, JsonPath.read(response, "$.payload_cleanup_command"));

      // Verify command
      String cmdToExecute = payloadCommand.getContent().replace("#{arg_value}", argValue);
      String expectedCmdEncoded = Base64.getEncoder().encodeToString(cmdToExecute.getBytes());
      assertEquals(expectedCmdEncoded, JsonPath.read(response, "$.command_content"));
    }

    @DisplayName("Should replace by asset IDs given Targeted asset argument")
    @Test
    void given_targetedAssetArgument_should_replaceByAssetIDs() throws Exception {
      // -- PREPARE --
      String command =
          "echo separatebyspace : #{asset-separate-by-space} separatebycoma : #{asset-separate-by-comma}";
      Command payloadCommand = PayloadFixture.createCommand("bash", command, null, null);
      PayloadArgument targetedAssetArgument =
          PayloadFixture.createPayloadArgument(
              "asset-separate-by-space", ContractFieldType.TargetedAsset, "hostname", "-u");
      PayloadArgument targetedAssetArgument2 =
          PayloadFixture.createPayloadArgument(
              "asset-separate-by-comma", ContractFieldType.TargetedAsset, "seen_ip", ",");
      payloadCommand.setArguments(List.of(targetedAssetArgument, targetedAssetArgument2));

      InjectorContract injectorContract = InjectorContractFixture.createDefaultInjectorContract();
      InjectorContractFixture.addTargetedAssetFields(
          injectorContract, "asset-separate-by-space", ContractTargetedProperty.hostname);
      InjectorContractFixture.addTargetedAssetFields(
          injectorContract, "asset-separate-by-comma", ContractTargetedProperty.seen_ip);

      // Create two endpoints
      Endpoint endpoint1 = EndpointFixture.createEndpoint();
      endpoint1.setHostname("endpoint1-hostname");
      String[] endpoint1IP = {"233.152.15.205"};
      endpoint1.setIps(endpoint1IP);
      endpoint1.setSeenIp("seen-ip-endpoint1");
      EndpointComposer.Composer endpointWrapper1 =
          endpointComposer.forEndpoint(endpoint1).persist();

      Endpoint endpoint2 = EndpointFixture.createEndpoint();
      endpoint2.setHostname("endpoint2-hostname");
      String[] endpoint2IP = {"253.110.186.71"};
      endpoint2.setIps(endpoint2IP);
      endpoint2.setSeenIp("seen-ip-endpoint2");
      EndpointComposer.Composer endpointWrapper2 =
          endpointComposer.forEndpoint(endpoint2).persist();

      Map<String, Object> payloadArguments = new HashMap<>();
      payloadArguments.put(
          "asset-separate-by-space",
          List.of(endpointWrapper1.get().getId(), endpointWrapper2.get().getId()));
      payloadArguments.put(
          "asset-separate-by-comma",
          List.of(endpointWrapper1.get().getId(), endpointWrapper2.get().getId()));
      payloadArguments.put(
          CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY + "-asset-separate-by-space", "local_ip");
      payloadArguments.put(
          CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR + "-asset-separate-by-space", " ");

      Inject injectSaved =
          injectComposer
              .forInject(InjectFixture.createInjectWithPayloadArg(payloadArguments))
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(injectorContract)
                      .withInjector(InjectorFixture.createDefaultPayloadInjector())
                      .withPayload(
                          payloadComposer
                              .forPayload(payloadCommand)
                              .withDomain(
                                  domainComposer.forDomain(DomainFixture.getRandomDomain()))))
              .withEndpoint(endpointWrapper1)
              .withEndpoint(endpointWrapper2)
              .persist()
              .get();

      doNothing()
          .when(injectStatusService)
          .addStartImplantExecutionTraceByInject(any(), any(), any(), any());

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECT_URI + "/" + injectSaved.getId() + "/fakeId/executable-payload")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertNotNull(response);

      // Verify command
      String cmdToExecute =
          command
              .replace("#{asset-separate-by-space}", "233.152.15.205 253.110.186.71")
              .replace("#{asset-separate-by-comma}", "seen-ip-endpoint2,seen-ip-endpoint1");
      String expectedCmdEncoded = Base64.getEncoder().encodeToString(cmdToExecute.getBytes());
      assertEquals(expectedCmdEncoded, JsonPath.read(response, "$.command_content"));
    }

    @DisplayName("Should set start date signature when calling RetrievingExecutablePayload")
    @Test
    void calling_RetrievingExecutablePayload_should_setStartDateSignature() throws Exception {
      // -- PREPARE --
      AgentComposer.Composer agentWrapper =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectStatus(
                  injectStatusComposer.forInjectStatus(
                      InjectStatusFixture.createPendingInjectStatus()))
              .withEndpoint(
                  endpointComposer
                      .forEndpoint(EndpointFixture.createEndpoint())
                      .withAgent(agentWrapper))
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(InjectorFixture.createDefaultPayloadInjector())
                      .withPayload(
                          payloadComposer
                              .forPayload(PayloadFixture.createDefaultCommand())
                              .withDomain(
                                  domainComposer.forDomain(DomainFixture.getRandomDomain()))))
              .withExpectation(
                  injectExpectationComposer
                      .forExpectation(
                          InjectExpectationFixture.createDefaultDetectionInjectExpectation())
                      .withAgent(agentWrapper))
              .persist();

      entityManager.flush();
      entityManager.clear();

      // -- EXECUTE --
      mvc.perform(
              get(INJECT_URI
                      + "/"
                      + injectWrapper.get().getId()
                      + "/"
                      + agentWrapper.get().getId()
                      + "/executable-payload")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      entityManager.flush();
      entityManager.clear();

      // -- ASSERT --
      List<InjectExpectation> injectExpectationSaved =
          injectExpectationRepository.findAllByInjectAndAgent(
              injectWrapper.get().getId(), agentWrapper.get().getId());

      assertThat(injectExpectationSaved)
          .first()
          .satisfies(
              expectation ->
                  assertThat(
                          expectation.getSignatures().stream()
                              .filter(
                                  s -> EXPECTATION_SIGNATURE_TYPE_START_DATE.equals(s.getType())))
                      .hasSize(1));
    }

    @DisplayName("Get obfuscate command")
    @Test
    void getExecutableObfuscatePayloadInject() throws Exception {
      // -- PREPARE --
      Command payloadCommand =
          PayloadFixture.createCommand("psh", "echo Hello World", List.of(), "echo cleanup cmd");

      Map<String, Object> payloadArguments = new HashMap<>();
      payloadArguments.put("obfuscator", "base64");

      Inject injectSaved =
          injectComposer
              .forInject(InjectFixture.createInjectWithPayloadArg(payloadArguments))
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(
                          InjectorContractFixture.createPayloadInjectorContractWithObfuscator(
                              payloadCommand.getExecutor()))
                      .withInjector(InjectorFixture.createDefaultPayloadInjector())
                      .withPayload(
                          payloadComposer
                              .forPayload(payloadCommand)
                              .withDomain(
                                  domainComposer.forDomain(DomainFixture.getRandomDomain()))))
              .persist()
              .get();
      doNothing()
          .when(injectStatusService)
          .addStartImplantExecutionTraceByInject(any(), any(), any(), any());

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECT_URI + "/" + injectSaved.getId() + "/fakeagentID/executable-payload")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertNotNull(response);

      // Verify command
      byte[] utf16Bytes = payloadCommand.getContent().getBytes(StandardCharsets.UTF_16LE);
      String base64 = Base64.getEncoder().encodeToString(utf16Bytes);
      String cmdToExecute = String.format("powershell -Enc %s", base64);

      String expectedCmdEncoded = Base64.getEncoder().encodeToString(cmdToExecute.getBytes());
      assertEquals(expectedCmdEncoded, JsonPath.read(response, "$.command_content"));
    }

    @DisplayName("Get plain text command for cmd")
    @Test
    void getExecutableCmdPayloadInject() throws Exception {
      // -- PREPARE --
      Command payloadCommand =
          PayloadFixture.createCommand("cmd", "echo Hello World", List.of(), "echo cleanup cmd");

      Map<String, Object> payloadArguments = new HashMap<>();
      payloadArguments.put("obfuscator", "plain-text");

      Inject injectSaved =
          injectComposer
              .forInject(InjectFixture.createInjectWithPayloadArg(payloadArguments))
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(
                          InjectorContractFixture.createPayloadInjectorContractWithObfuscator(
                              payloadCommand.getExecutor()))
                      .withInjector(InjectorFixture.createDefaultPayloadInjector())
                      .withPayload(
                          payloadComposer
                              .forPayload(payloadCommand)
                              .withDomain(
                                  domainComposer.forDomain(DomainFixture.getRandomDomain()))))
              .persist()
              .get();
      doNothing()
          .when(injectStatusService)
          .addStartImplantExecutionTraceByInject(any(), any(), any(), any());

      // -- EXECUTE --
      String response =
          mvc.perform(
                  get(INJECT_URI + "/" + injectSaved.getId() + "/fakeagentID/executable-payload")
                      .accept(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // -- ASSERT --
      assertNotNull(response);

      // Verify command
      // We still need to encode in base64, because the command is encoded to be sent to the implant
      // But the content is not encoded at execution, it's only for data transfer
      String computedCommand = replaceCmdVariables(payloadCommand.getContent());
      computedCommand = formatMultilineCommand(computedCommand);
      String expectedCmdEncoded = Base64.getEncoder().encodeToString(computedCommand.getBytes());
      assertEquals(expectedCmdEncoded, JsonPath.read(response, "$.command_content"));
    }

    @DisplayName("Should not get base64 command for cmd")
    @Test
    void shouldNotGetExecutableCmdPayloadInject() throws Exception {
      // -- PREPARE --
      Command payloadCommand =
          PayloadFixture.createCommand("cmd", "echo Hello World", List.of(), "echo cleanup cmd");

      Map<String, Object> payloadArguments = new HashMap<>();
      payloadArguments.put("obfuscator", "base64");

      Inject injectSaved =
          injectComposer
              .forInject(InjectFixture.createInjectWithPayloadArg(payloadArguments))
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(
                          InjectorContractFixture.createPayloadInjectorContractWithObfuscator(
                              payloadCommand.getExecutor()))
                      .withInjector(InjectorFixture.createDefaultPayloadInjector())
                      .withPayload(
                          payloadComposer
                              .forPayload(payloadCommand)
                              .withDomain(
                                  domainComposer.forDomain(DomainFixture.getRandomDomain()))))
              .persist()
              .get();
      doNothing()
          .when(injectStatusService)
          .addStartImplantExecutionTraceByInject(any(), any(), any(), any());

      // -- EXECUTE --
      assertThrows(
          ServletException.class,
          () -> {
            mvc.perform(
                get(INJECT_URI + "/" + injectSaved.getId() + "/fakeagentID/executable-payload")
                    .accept(MediaType.APPLICATION_JSON));
          });
    }
  }

  @Nested
  @Transactional
  @WithMockUser(isAdmin = true)
  @DisplayName("Inject Execution Callback Handling (simulating a request from an implant)")
  @KeepRabbit
  class handleInjectExecutionCallback {

    private Inject getPendingInjectWithAssets() {
      return injectTestHelper.getPendingInjectWithAssets(
          injectComposer,
          injectorContractComposer,
          endpointComposer,
          agentComposer,
          injectStatusComposer);
    }

    private void performAgentlessCallbackRequest(String injectId, InjectExecutionInput input)
        throws Exception {
      mvc.perform(
              post(INJECT_URI + "/execution/callback/" + injectId)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    private void performCallbackRequest(String agentId, String injectId, InjectExecutionInput input)
        throws Exception {
      mvc.perform(
              post(INJECT_URI + "/execution/" + agentId + "/callback/" + injectId)
                  .content(asJsonString(input))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    /**
     * Builds a pending inject backed by a Command payload that carries the given output parser, and
     * returns {@code [inject, agentId]}.
     */
    private Object[] buildInjectWithOutputParser(OutputParser outputParser) throws Exception {
      Domain domain = injectTestHelper.forceSaveDomain(DomainFixture.getRandomDomain());
      Command command =
          PayloadFixture.createCommand(
              "bash", "echo test", null, null, new HashSet<>(Set.of(domain)));
      command.setOutputParsers(Set.of(outputParser));
      Payload payloadSaved = injectTestHelper.forceSavePayload(command);

      Injector injector = InjectorFixture.createDefaultPayloadInjector();
      injectTestHelper.forceSaveInjector(injector);

      InjectorContract injectorContract =
          InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
              injector, payloadSaved, List.of());
      injectorContract.setContent(injectorContract.getConvertedContent().toString());
      InjectorContract injectorContractSaved =
          injectTestHelper.forceSaveInjectorContract(injectorContract);

      Inject inject = getPendingInjectWithAssets();
      inject.setInjectorContract(injectorContractSaved);
      injectTestHelper.forceSaveInject(inject);

      String agentId = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
      return new Object[] {inject, agentId};
    }

    /** Wraps stdout content in the expected JSON envelope used by the implant callback. */
    private InjectExecutionInput buildStdoutInput(String stdoutContent) {
      InjectExecutionInput input = new InjectExecutionInput();
      input.setMessage("{\"stdout\":\"" + stdoutContent + "\"}");
      input.setAction(InjectExecutionAction.command_execution);
      input.setStatus("SUCCESS");
      return input;
    }

    @Nested
    @DisplayName("Action Handling:")
    @KeepRabbit
    class ActionHandlingTest {

      @DisplayName("Should add trace when process is not finished")
      @Test
      void shouldAddTraceWhenProcessNotFinished() throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        String logMessage = "First log received";
        input.setMessage(logMessage);
        input.setAction(InjectExecutionAction.command_execution);
        input.setStatus("SUCCESS");
        Inject inject = getPendingInjectWithAssets();

        entityManager.flush();

        // -- EXECUTE --
        String agentId = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
        performCallbackRequest(agentId, inject.getId(), input);

        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  Optional<Inject> injectSaved = injectRepository.findById(inject.getId());
                  if (injectSaved.isEmpty()) {
                    return false;
                  }
                  Optional<InjectStatus> injectStatusSaved = injectSaved.get().getStatus();
                  return injectStatusSaved
                      .filter(injectStatus -> !injectStatus.getTraces().isEmpty())
                      .isPresent();
                });

        // -- ASSERT --
        Inject injectSaved = injectRepository.findById(inject.getId()).orElseThrow();
        InjectStatus injectStatusSaved = injectSaved.getStatus().orElseThrow();
        assertEquals(ExecutionStatus.PENDING, injectStatusSaved.getName());
        assertEquals(1, injectStatusSaved.getTraces().size());
        assertEquals(
            ExecutionTraceStatus.SUCCESS, injectStatusSaved.getTraces().getFirst().getStatus());
        assertEquals(
            ExecutionTraceAction.EXECUTION, injectStatusSaved.getTraces().getFirst().getAction());
        assertEquals(logMessage, injectStatusSaved.getTraces().getFirst().getMessage());
      }

      @DisplayName(
          "Should add trace and compute agent status when one of two agents finishes execution")
      @Test
      void shouldAddTraceAndComputeAgentStatusWhenOneAgentFinishes() throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("First log received");
        input.setAction(InjectExecutionAction.command_execution);
        input.setStatus("COMMAND_NOT_FOUND");
        Inject inject = getPendingInjectWithAssets();

        // -- EXECUTE --
        String agentId = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
        performCallbackRequest(agentId, inject.getId(), input);

        InjectExecutionInput input2 = new InjectExecutionInput();
        String lastLogMessage = "Complete log received";
        input2.setMessage(lastLogMessage);
        input2.setAction(InjectExecutionAction.complete);
        input2.setStatus("INFO");
        performCallbackRequest(agentId, inject.getId(), input2);

        Awaitility.await()
            .atMost(180, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  Optional<Inject> injectSaved = injectRepository.findById(inject.getId());
                  if (injectSaved.isEmpty()) {
                    return false;
                  }
                  Optional<InjectStatus> injectStatusSaved = injectSaved.get().getStatus();
                  return injectStatusSaved
                      .filter(injectStatus -> injectStatus.getTraces().size() > 1)
                      .isPresent();
                });

        // -- ASSERT --
        Inject injectSaved = injectRepository.findById(inject.getId()).orElseThrow();
        InjectStatus injectStatusSaved = injectSaved.getStatus().orElseThrow();
        // Check inject status
        assertEquals(ExecutionStatus.PENDING, injectStatusSaved.getName());
        assertEquals(2, injectStatusSaved.getTraces().size());
        // The status of the complete trace should be ERROR
        List<ExecutionTrace> completeTraces =
            injectStatusSaved.getTraces().stream()
                .filter(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()))
                .toList();
        assertEquals(1, completeTraces.size());
        assertEquals(
            ExecutionTraceStatus.ERROR, completeTraces.stream().findFirst().get().getStatus());
      }

      @DisplayName(
          "Should add trace, compute agent status, and update inject status when all agents finish execution")
      @Test
      void shouldAddTraceComputeAgentStatusAndUpdateInjectStatusWhenAllAgentsFinish()
          throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("First log received");
        input.setAction(InjectExecutionAction.command_execution);
        input.setStatus("COMMAND_NOT_FOUND");
        Inject inject = getPendingInjectWithAssets();

        // -- EXECUTE --
        String firstAgentId =
            ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
        String secondAgentId =
            ((Endpoint) inject.getAssets().getFirst()).getAgents().getLast().getId();
        performCallbackRequest(firstAgentId, inject.getId(), input);
        input.setStatus("SUCCESS");
        performCallbackRequest(secondAgentId, inject.getId(), input);

        InjectExecutionInput input2 = new InjectExecutionInput();
        String lastLogMessage = "Complete log received";
        input2.setMessage(lastLogMessage);
        input2.setAction(InjectExecutionAction.complete);
        input2.setStatus("INFO");
        performCallbackRequest(firstAgentId, inject.getId(), input2);
        performCallbackRequest(secondAgentId, inject.getId(), input2);

        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  Optional<Inject> injectSaved = injectRepository.findById(inject.getId());
                  if (injectSaved.isEmpty()) {
                    return false;
                  }
                  Optional<InjectStatus> injectStatusSaved = injectSaved.get().getStatus();
                  return injectStatusSaved
                      .filter(injectStatus -> injectStatus.getTraces().size() > 1)
                      .isPresent();
                });

        // -- ASSERT --
        Inject injectSaved = injectRepository.findById(inject.getId()).orElseThrow();
        InjectStatus injectStatusSaved = injectSaved.getStatus().orElseThrow();
        // Check inject status
        assertEquals(ExecutionStatus.PARTIAL, injectStatusSaved.getName());
      }

      @DisplayName("Should add end date signature to a specific agent when finishing execution")
      @Test
      void given_completeTrace_should_setEndDateSignature() throws Exception {

        // -- PREPARE --
        Inject inject = getPendingInjectWithAssets();
        Agent agent = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst();

        // create expectation
        InjectExpectation detectionExpectation =
            InjectExpectationFixture.createDetectionInjectExpectation(inject, agent);
        injectTestHelper.forceSaveInjectExpectation(detectionExpectation);

        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("Complete log received");
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("INFO");
        input.setDuration(1000);

        performCallbackRequest(agent.getId(), inject.getId(), input);
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  List<InjectExpectation> injectExpectationSaved =
                      injectExpectationRepository.findAllByInjectAndAgent(
                          inject.getId(), agent.getId());
                  List<InjectExpectationSignature> endDatesignatures =
                      injectExpectationSaved.getFirst().getSignatures().stream()
                          .filter(s -> EXPECTATION_SIGNATURE_TYPE_END_DATE.equals(s.getType()))
                          .toList();
                  return endDatesignatures.size() > 0;
                });

        // -- ASSERT --
        List<InjectExpectation> injectExpectationSaved =
            injectExpectationRepository.findAllByInjectAndAgent(inject.getId(), agent.getId());
        assertEquals(1, injectExpectationSaved.size());
        List<InjectExpectationSignature> endDatesignatures =
            injectExpectationSaved.getFirst().getSignatures().stream()
                .filter(s -> EXPECTATION_SIGNATURE_TYPE_END_DATE.equals(s.getType()))
                .toList();
        assertEquals(1, endDatesignatures.size());
      }

      @DisplayName(
          "Should add a trace when inject completed without agent (e.g. external injectors")
      @Test
      void given_completeTraceAndNullAgent_should_addTrace() throws Exception {

        // -- PREPARE --
        Inject inject = getPendingInjectWithAssets();
        injectTestHelper.forceSaveInject(inject);

        String traceMessage = "Complete log received";
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage(traceMessage);
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("INFO");
        input.setDuration(1000);

        performAgentlessCallbackRequest(inject.getId(), input);
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  Inject dbInject = injectRepository.findById(inject.getId()).orElseThrow();
                  return dbInject.getStatus().isPresent()
                      && !dbInject.getStatus().get().getTraces().isEmpty();
                });

        // -- ASSERT --
        Inject dbInject = injectRepository.findById(inject.getId()).orElseThrow();
        assertThat(dbInject.getStatus().get().getTraces()).isNotEmpty();
        assertThat(dbInject.getStatus().get().getTraces().size()).isEqualTo(1);
        ExecutionTrace singleTrace = dbInject.getStatus().get().getTraces().getFirst();
        assertThat(singleTrace.getMessage()).isEqualTo(traceMessage);
        assertThat(singleTrace.getAction()).isEqualTo(ExecutionTraceAction.COMPLETE);
        assertThat(singleTrace.getStatus()).isEqualTo(ExecutionTraceStatus.INFO);
      }
    }

    @Nested
    @DisplayName("Agent Status Computation")
    @KeepRabbit
    class AgentStatusComputationTest {

      private void testAgentStatusFunction(
          String inputTraceStatus1,
          String inputTraceStatus2,
          ExecutionTraceStatus expectedAgentStatus)
          throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("First log received");
        input.setAction(InjectExecutionAction.command_execution);
        input.setStatus(inputTraceStatus1);
        Inject inject = getPendingInjectWithAssets();

        // -- EXECUTE --
        String firstAgentId =
            ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
        performCallbackRequest(firstAgentId, inject.getId(), input);
        input.setStatus(inputTraceStatus2);
        performCallbackRequest(firstAgentId, inject.getId(), input);
        // send complete trace
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("INFO");
        performCallbackRequest(firstAgentId, inject.getId(), input);

        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  Optional<Inject> injectSaved = injectRepository.findById(inject.getId());
                  if (injectSaved.isEmpty()) {
                    return false;
                  }
                  Optional<InjectStatus> injectStatusSaved = injectSaved.get().getStatus();
                  return injectStatusSaved
                      .filter(injectStatus -> injectStatus.getTraces().size() > 2)
                      .isPresent();
                });

        // -- ASSERT --
        Inject injectSaved = injectRepository.findById(inject.getId()).orElseThrow();
        InjectStatus injectStatusSaved = injectSaved.getStatus().orElseThrow();
        List<ExecutionTrace> completeTraces =
            injectStatusSaved.getTraces().stream()
                .filter(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction()))
                .toList();
        assertEquals(1, completeTraces.size());
        assertEquals(expectedAgentStatus, completeTraces.stream().findFirst().get().getStatus());
      }

      @Test
      @DisplayName("Should compute agent status as ERROR")
      void shouldComputeAgentStatusAsError() throws Exception {
        testAgentStatusFunction("COMMAND_NOT_FOUND", "ERROR", ExecutionTraceStatus.ERROR);
      }

      @Test
      @DisplayName("Should compute agent status as SUCCESS")
      void shouldComputeAgentStatusAsSuccess() throws Exception {
        testAgentStatusFunction("SUCCESS", "WARNING", ExecutionTraceStatus.SUCCESS);
      }

      @Test
      @DisplayName("Should compute agent status as PARTIAL")
      void shouldComputeAgentStatusAsPartial() throws Exception {
        testAgentStatusFunction("SUCCESS", "COMMAND_NOT_FOUND", ExecutionTraceStatus.PARTIAL);
      }

      @Test
      @DisplayName("Should compute agent status as MAYBE_PREVENTED")
      void shouldComputeAgentStatusAsMayBePrevented() throws Exception {
        testAgentStatusFunction(
            "COMMAND_CANNOT_BE_EXECUTED", "MAYBE_PREVENTED", ExecutionTraceStatus.MAYBE_PREVENTED);
      }
    }

    @Nested
    @DisplayName("Finding Processing Handling")
    @KeepRabbit
    class FindingProcessingHandlingTest {

      @Test
      @DisplayName("Should link finding to targeted asset")
      void given_targetedAsset_should_linkFindingToIt() throws Exception {
        // -- PREPARE --
        //        [CVE-2025-25241] [http] [critical] https://seen-ip-endpoint/
        InjectExecutionInput input = new InjectExecutionInput();
        String logMessage =
            "{\"stdout\":\"[CVE-2025-25241] [http] [critical] http://seen-ip-endpoint/\\n[CVE-2025-25002] [http] [critical] http://seen-ip-endpoint/\\n\"}";
        input.setMessage(logMessage);
        input.setAction(InjectExecutionAction.command_execution);
        input.setStatus("SUCCESS");
        Inject inject = getPendingInjectWithAssets();

        // Create payload with output parser
        ContractOutputElement CVEOutputElement = OutputParserFixture.getCVEOutputElement();
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(CVEOutputElement));

        Domain domainSaved = injectTestHelper.forceSaveDomain(DomainFixture.getRandomDomain());
        Command payloadCommand =
            PayloadFixture.createCommand(
                "bash", "command", null, null, new HashSet<>(Set.of(domainSaved)));
        payloadCommand.setOutputParsers(Set.of(outputParser));
        Payload payloadSaved = injectTestHelper.forceSavePayload(payloadCommand);

        // Create injectorContract with targeted asset field
        Injector injector = InjectorFixture.createDefaultPayloadInjector();
        injectTestHelper.forceSaveInjector(injector);
        InjectorContract injectorContract =
            InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
                injector, payloadSaved, List.of());
        InjectorContractFixture.addTargetedAssetFields(
            injectorContract, "asset-key", ContractTargetedProperty.seen_ip);
        injectorContract.setContent(injectorContract.getConvertedContent().toString());
        InjectorContract injectorContractSaved =
            injectTestHelper.forceSaveInjectorContract(injectorContract);
        inject.setInjectorContract(injectorContractSaved);

        // Set targeted inject on inject
        Endpoint endpoint = EndpointFixture.createEndpoint();
        endpoint.setSeenIp("seen-ip-endpoint");
        Endpoint endpointSaved = injectTestHelper.forceSaveEndpoint(endpoint);
        ObjectNode content = objectMapper.createObjectNode();
        content.set(
            "asset-key", objectMapper.convertValue(List.of(endpointSaved.getId()), JsonNode.class));
        inject.setContent(content);
        injectTestHelper.forceSaveInject(inject);

        // -- EXECUTE --
        String agentId = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
        performCallbackRequest(agentId, inject.getId(), input);

        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  List<Finding> findings = findingRepository.findAllByInjectId(inject.getId());
                  return findings.size() > 1;
                });

        List<Finding> findings = findingRepository.findAllByInjectId(inject.getId());
        assertEquals(2, findings.size());
        assertEquals(1, findings.getFirst().getAssets().size());
        assertEquals(endpointSaved.getId(), findings.getFirst().getAssets().getFirst().getId());
        assertEquals(1, findings.getLast().getAssets().size());
        assertEquals(endpointSaved.getId(), findings.getLast().getAssets().getFirst().getId());
      }

      // Deduplication

      @Test
      @DisplayName(
          "Should consolidate duplicate CVE findings when structured output contains multiple entries with the same id")
      void shouldConsolidateDuplicateCveFindingsWhenStructuredOutputContainsDuplicates()
          throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("Duplicate CVE findings test");
        input.setOutputStructured(
            """
                {
                  "cve": [
                    {"id": "CVE-2025-99999", "host": "192.168.1.10", "severity": "critical"},
                    {"id": "CVE-2025-99999", "host": "192.168.1.20", "severity": "critical"}
                  ]
                }
                """);
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("SUCCESS");

        Inject inject = getPendingInjectWithAssets();
        Injector injector = InjectorFixture.createDefaultPayloadInjector();
        injectTestHelper.forceSaveInjector(injector);

        ObjectNode convertedContent =
            (ObjectNode)
                mapper.readTree(
                    """
                        {
                          "outputs": [
                            {
                              "field": "cve",
                              "isFindingCompatible": true,
                              "isMultiple": true,
                              "labels": [],
                              "type": "cve"
                            }
                          ]
                        }
                        """);
        convertedContent.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(List.of()));
        InjectorContract injectorContract =
            InjectorContractFixture.createInjectorContract(convertedContent);
        injectorContract.setInjector(injector);
        InjectorContract injectorContractSaved =
            injectTestHelper.forceSaveInjectorContract(injectorContract);
        inject.setInjectorContract(injectorContractSaved);
        inject.setContent(convertedContent);
        injectTestHelper.forceSaveInject(inject);

        // -- EXECUTE --
        performAgentlessCallbackRequest(inject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> !injectTestHelper.findFindingsByInjectId(inject.getId()).isEmpty());

        List<Finding> findings = findingRepository.findAllByInjectId(inject.getId());
        assertEquals(
            1,
            findings.size(),
            "Duplicate CVE findings with the same id must be consolidated into one");
        assertEquals(ContractOutputType.CVE, findings.getFirst().getType());
        assertEquals("CVE-2025-99999", findings.getFirst().getValue());
      }

      @Test
      @DisplayName(
          "Should consolidate duplicate Port findings when two scanned hosts both have the same port open")
      void shouldConsolidateDuplicatePortFindingsWhenTwoHostsHaveSamePortOpen() throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("nmap TCP connect scan");
        input.setOutputStructured(
            """
                {
                  "ports": [22, 8080, 8080]
                }
                """);
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("SUCCESS");

        Inject inject = getPendingInjectWithAssets();
        Injector injector = InjectorFixture.createDefaultPayloadInjector();
        injectTestHelper.forceSaveInjector(injector);

        ObjectNode convertedContent =
            (ObjectNode)
                mapper.readTree(
                    """
                        {
                          "outputs": [
                            {
                              "field": "ports",
                              "isFindingCompatible": true,
                              "isMultiple": true,
                              "labels": ["scan"],
                              "type": "port"
                            }
                          ]
                        }
                        """);
        convertedContent.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(List.of()));
        InjectorContract injectorContract =
            InjectorContractFixture.createInjectorContract(convertedContent);
        injectorContract.setInjector(injector);
        InjectorContract injectorContractSaved =
            injectTestHelper.forceSaveInjectorContract(injectorContract);
        inject.setInjectorContract(injectorContractSaved);
        inject.setContent(convertedContent);
        injectTestHelper.forceSaveInject(inject);

        // -- EXECUTE --
        performAgentlessCallbackRequest(inject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(inject.getId()).size() >= 2);

        List<Finding> findings = findingRepository.findAllByInjectId(inject.getId());
        assertEquals(
            2,
            findings.size(),
            "Port 22 and port 8080 (deduplicated) must produce exactly 2 findings");
        assertTrue(
            findings.stream().anyMatch(f -> f.getValue().equals("22")),
            "Expected finding for port 22");
        assertTrue(
            findings.stream().anyMatch(f -> f.getValue().equals("8080")),
            "Expected deduplicated finding for port 8080");
        findings.forEach(f -> assertEquals(ContractOutputType.Port, f.getType()));
      }

      @Test
      @DisplayName(
          "Should merge assets of duplicate PortsScan findings when two assets expose the same host/port/service")
      void shouldMergeAssetsOfDuplicatePortScanFindingsWhenTwoAssetsHaveSameHostPortService()
          throws Exception {
        // -- PREPARE --
        Endpoint endpointA = EndpointFixture.createEndpoint();
        Endpoint endpointASaved = injectTestHelper.forceSaveEndpoint(endpointA);
        Endpoint endpointB = EndpointFixture.createEndpoint();
        Endpoint endpointBSaved = injectTestHelper.forceSaveEndpoint(endpointB);

        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("nmap scan_results two assets both expose 192.168.1.10:8080/http");
        input.setOutputStructured(
            String.format(
                """
                    {
                      "scan_results": [
                        {"asset_id": "%s", "host": "192.168.1.10", "port": "8080", "service": "http"},
                        {"asset_id": "%s", "host": "192.168.1.10", "port": "8080", "service": "http"}
                      ]
                    }
                    """,
                endpointASaved.getId(), endpointBSaved.getId()));
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("SUCCESS");

        Inject inject = getPendingInjectWithAssets();
        Injector injector = InjectorFixture.createDefaultPayloadInjector();
        injectTestHelper.forceSaveInjector(injector);

        ObjectNode convertedContent =
            (ObjectNode)
                mapper.readTree(
                    """
                        {
                          "outputs": [
                            {
                              "field": "scan_results",
                              "isFindingCompatible": true,
                              "isMultiple": true,
                              "labels": ["scan"],
                              "type": "portscan"
                            }
                          ]
                        }
                        """);
        convertedContent.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(List.of()));
        InjectorContract injectorContract =
            InjectorContractFixture.createInjectorContract(convertedContent);
        injectorContract.setInjector(injector);
        InjectorContract injectorContractSaved =
            injectTestHelper.forceSaveInjectorContract(injectorContract);
        inject.setInjectorContract(injectorContractSaved);
        inject.setContent(convertedContent);
        injectTestHelper.forceSaveInject(inject);

        // -- EXECUTE --
        performAgentlessCallbackRequest(inject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> !injectTestHelper.findFindingsByInjectId(inject.getId()).isEmpty());

        List<Finding> findings = findingRepository.findAllByInjectId(inject.getId());
        assertEquals(
            1,
            findings.size(),
            "Two PortsScan entries with same host/port/service must be consolidated into one finding");
        Finding merged = findings.getFirst();
        assertEquals(ContractOutputType.PortsScan, merged.getType());
        assertEquals("192.168.1.10:8080 (http)", merged.getValue());
        List<String> assetIds = merged.getAssets().stream().map(Asset::getId).toList();
        assertTrue(
            assetIds.contains(endpointASaved.getId()), "Merged finding must be linked to asset A");
        assertTrue(
            assetIds.contains(endpointBSaved.getId()), "Merged finding must be linked to asset B");
      }

      // CVE

      @Test
      @DisplayName("Should create findings for each CVE extracted from raw output")
      void shouldCreateFindingsForEachCveExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        ContractOutputElement cveElement = OutputParserFixture.getCVEOutputElement();
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(cveElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject cveInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage(
            "{\"stdout\":\"[CVE-2025-25241] [http] [critical] http://192.168.1.10/\\n"
                + "[CVE-2025-99999] [http] [high] http://192.168.1.20/\\n\"}");
        input.setAction(InjectExecutionAction.command_execution);
        input.setStatus("SUCCESS");

        // -- EXECUTE --
        performCallbackRequest(agentId, cveInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(cveInject.getId()).size() >= 2);

        List<Finding> cveFindings = injectTestHelper.findFindingsByInjectId(cveInject.getId());
        assertEquals(2, cveFindings.size());
        assertTrue(
            cveFindings.stream().anyMatch(f -> f.getValue().contains("CVE-2025-25241")),
            "Expected finding for CVE-2025-25241");
        assertTrue(
            cveFindings.stream().anyMatch(f -> f.getValue().contains("CVE-2025-99999")),
            "Expected finding for CVE-2025-99999");
        cveFindings.forEach(f -> assertEquals(ContractOutputType.CVE, f.getType()));
      }

      @Test
      @DisplayName("Should not create CVE findings when raw output contains no CVE matches")
      void shouldNotCreateCveFindingsWhenRawOutputContainsNoCveMatches() throws Exception {
        // -- PREPARE --
        ContractOutputElement cveElement = OutputParserFixture.getCVEOutputElement();
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(cveElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject cveInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no vulnerabilities found");

        // -- EXECUTE --
        performCallbackRequest(agentId, cveInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(cveInject.getId()));
        assertTrue(
            injectTestHelper.findFindingsByInjectId(cveInject.getId()).isEmpty(),
            "No findings expected when output has no CVE match");
      }

      // Credentials

      @Test
      @DisplayName("Should create a finding for each credential pair extracted from raw output")
      void shouldCreateFindingForEachCredentialPairExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup usernameGroup = OutputParserFixture.getRegexGroup("username", "$2");
        RegexGroup passwordGroup = OutputParserFixture.getRegexGroup("password", "$3");
        ContractOutputElement credElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Credentials,
                "(\\S+)\\\\(\\S+):(\\S+)",
                Set.of(usernameGroup, passwordGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(credElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject credInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        // domain\\user:pass format, each line produces one finding
        String rawOutput =
            "SMB 192.168.11.23 445 SERVER [+] WORKGROUP\\\\alice:secret123 (Pwn3d!)\\n"
                + "SMB 192.168.11.23 445 SERVER [+] WORKGROUP\\\\bob:hunter2 (Pwn3d!)\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, credInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(credInject.getId()).size() >= 2);

        List<Finding> credFindings = injectTestHelper.findFindingsByInjectId(credInject.getId());
        assertEquals(2, credFindings.size());
        assertTrue(
            credFindings.stream().anyMatch(f -> f.getValue().contains("alice:secret123")),
            "Expected finding for alice:secret123");
        assertTrue(
            credFindings.stream().anyMatch(f -> f.getValue().contains("bob:hunter2")),
            "Expected finding for bob:hunter2");
        credFindings.forEach(f -> assertEquals(ContractOutputType.Credentials, f.getType()));
      }

      @Test
      @DisplayName("Should not create credentials findings when raw output contains no credentials")
      void shouldNotCreateCredentialsFindingsWhenRawOutputContainsNoCredentials() throws Exception {
        // -- PREPARE --
        RegexGroup usernameGroup = OutputParserFixture.getRegexGroup("username", "$2");
        RegexGroup passwordGroup = OutputParserFixture.getRegexGroup("password", "$3");
        ContractOutputElement credElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Credentials,
                "(\\S+)\\\\(\\S+):(\\S+)",
                Set.of(usernameGroup, passwordGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(credElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject credInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no credentials here");

        // -- EXECUTE --
        performCallbackRequest(agentId, credInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(credInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(credInject.getId()).isEmpty());
      }

      // PortScan

      @Test
      @DisplayName("Should create a finding for each open port/service extracted from raw output")
      void shouldCreateFindingForEachOpenPortServiceExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup hostGroup = OutputParserFixture.getRegexGroup("host", "$1");
        RegexGroup portGroup = OutputParserFixture.getRegexGroup("port", "$2");
        RegexGroup serviceGroup = OutputParserFixture.getRegexGroup("service", "$3");
        ContractOutputElement portScanElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.PortsScan,
                "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d+)\\s+\\S+\\s+(LISTENING)",
                Set.of(hostGroup, portGroup, serviceGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(portScanElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject portScanInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage(
            "{\"stdout\":\"192.168.1.10:135 0.0.0.0:0 LISTENING\\n"
                + "10.0.0.5:443 0.0.0.0:0 LISTENING\\n\"}");
        input.setAction(InjectExecutionAction.command_execution);
        input.setStatus("SUCCESS");

        // -- EXECUTE --
        performCallbackRequest(agentId, portScanInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> injectTestHelper.findFindingsByInjectId(portScanInject.getId()).size() >= 2);

        List<Finding> portScanFindings =
            injectTestHelper.findFindingsByInjectId(portScanInject.getId());
        assertEquals(2, portScanFindings.size());
        assertTrue(
            portScanFindings.stream().anyMatch(f -> f.getValue().contains("192.168.1.10")),
            "Expected finding for 192.168.1.10:135");
        assertTrue(
            portScanFindings.stream().anyMatch(f -> f.getValue().contains("10.0.0.5")),
            "Expected finding for 10.0.0.5:443");
        portScanFindings.forEach(f -> assertEquals(ContractOutputType.PortsScan, f.getType()));
      }

      @Test
      @DisplayName("Should not create PortScan findings when raw output has no port scan matches")
      void shouldNotCreatePortScanFindingsWhenRawOutputHasNoPortScanMatches() throws Exception {
        // -- PREPARE --
        RegexGroup hostGroup = OutputParserFixture.getRegexGroup("host", "$1");
        RegexGroup portGroup = OutputParserFixture.getRegexGroup("port", "$2");
        RegexGroup serviceGroup = OutputParserFixture.getRegexGroup("service", "$3");
        ContractOutputElement portScanElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.PortsScan,
                "(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}):(\\d+)\\s+\\S+\\s+(LISTENING)",
                Set.of(hostGroup, portGroup, serviceGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(portScanElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject portScanInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("nothing to scan");

        // -- EXECUTE --
        performCallbackRequest(agentId, portScanInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(portScanInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(portScanInject.getId()).isEmpty());
      }

      // Port

      @Test
      @DisplayName("Should create a finding for each port number extracted from raw output")
      void shouldCreateFindingForEachPortNumberExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup portGroup = OutputParserFixture.getRegexGroup("port", "$1");
        ContractOutputElement portElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Port,
                "(?:TCP|UDP)\\s+[\\d\\.]+:(\\d+)",
                Set.of(portGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(portElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject portInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput =
            "  TCP    192.168.1.10:8080            0.0.0.0:0              LISTENING\\n"
                + "  TCP    192.168.1.10:443            0.0.0.0:0              LISTENING\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, portInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(portInject.getId()).size() >= 2);

        List<Finding> portFindings = injectTestHelper.findFindingsByInjectId(portInject.getId());
        assertEquals(2, portFindings.size());
        assertTrue(
            portFindings.stream().anyMatch(f -> f.getValue().equals("8080")),
            "Expected finding for port 8080");
        assertTrue(
            portFindings.stream().anyMatch(f -> f.getValue().equals("443")),
            "Expected finding for port 443");
        portFindings.forEach(f -> assertEquals(ContractOutputType.Port, f.getType()));
      }

      @Test
      @DisplayName("Should not create Port findings when raw output contains no port matches")
      void shouldNotCreatePortFindingsWhenRawOutputContainsNoPortMatches() throws Exception {
        // -- PREPARE --
        RegexGroup portGroup = OutputParserFixture.getRegexGroup("port", "$1");
        ContractOutputElement portElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Port,
                "(?:TCP|UDP)\\s+[\\d\\.]+:(\\d+)",
                Set.of(portGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(portElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject portInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no ports here");

        // -- EXECUTE --
        performCallbackRequest(agentId, portInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(portInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(portInject.getId()).isEmpty());
      }

      // Text

      @Test
      @DisplayName("Should create findings for each text value extracted from raw output")
      void shouldCreateFindingsForEachTextValueExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup textGroup = OutputParserFixture.getRegexGroup("text", "$0");
        ContractOutputElement textElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Text, "^(\\S+)", Set.of(textGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(textElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject textInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "System\\nRegistry\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, textInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> !injectTestHelper.findFindingsByInjectId(textInject.getId()).isEmpty());

        List<Finding> textFindings = injectTestHelper.findFindingsByInjectId(textInject.getId());
        assertFalse(textFindings.isEmpty(), "Expected at least one text finding");
        textFindings.forEach(f -> assertEquals(ContractOutputType.Text, f.getType()));
      }

      @Test
      @DisplayName("Should not create Text findings when raw output contains no matches")
      void shouldNotCreateTextFindingsWhenRawOutputContainsNoMatches() throws Exception {
        // -- PREPARE --
        RegexGroup textGroup = OutputParserFixture.getRegexGroup("text", "$1");
        ContractOutputElement textElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Text, "IMPOSSIBLE_PATTERN_XYZ_123", Set.of(textGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(textElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject textInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("some random text");

        // -- EXECUTE --
        performCallbackRequest(agentId, textInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(textInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(textInject.getId()).isEmpty());
      }

      // Number

      @Test
      @DisplayName("Should create findings for each number extracted from raw output")
      void shouldCreateFindingsForEachNumberExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup numberGroup = OutputParserFixture.getRegexGroup("number", "$1");
        ContractOutputElement numberElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Number, "(\\d{4,})", Set.of(numberGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(numberElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject numberInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "Process 1234 used 5678 bytes\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, numberInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(numberInject.getId()).size() >= 2);

        List<Finding> numberFindings =
            injectTestHelper.findFindingsByInjectId(numberInject.getId());
        assertEquals(2, numberFindings.size());
        assertTrue(numberFindings.stream().anyMatch(f -> f.getValue().equals("1234")));
        assertTrue(numberFindings.stream().anyMatch(f -> f.getValue().equals("5678")));
        numberFindings.forEach(f -> assertEquals(ContractOutputType.Number, f.getType()));
      }

      @Test
      @DisplayName("Should not create Number findings when raw output contains no numeric matches")
      void shouldNotCreateNumberFindingsWhenRawOutputContainsNoNumericMatches() throws Exception {
        // -- PREPARE --
        RegexGroup numberGroup = OutputParserFixture.getRegexGroup("number", "$1");
        ContractOutputElement numberElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Number, "(\\d{4,})", Set.of(numberGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(numberElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject numberInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no big numbers here");

        // -- EXECUTE --
        performCallbackRequest(agentId, numberInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(numberInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(numberInject.getId()).isEmpty());
      }

      // IPv4

      @Test
      @DisplayName("Should create a finding for each valid IPv4 address extracted from raw output")
      void shouldCreateFindingForEachValidIPv4AddressExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup ipv4Group = OutputParserFixture.getRegexGroup("ipv4", "$0");
        ContractOutputElement ipv4Element =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.IPv4,
                "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b",
                Set.of(ipv4Group),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(ipv4Element));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject ipv4Inject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput =
            "  TCP    192.168.1.10:135            0.0.0.0:0              LISTENING\\n"
                + "  TCP    10.0.0.5:443            0.0.0.0:0              LISTENING\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, ipv4Inject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> !injectTestHelper.findFindingsByInjectId(ipv4Inject.getId()).isEmpty());

        List<Finding> ipv4Findings = injectTestHelper.findFindingsByInjectId(ipv4Inject.getId());
        assertFalse(ipv4Findings.isEmpty(), "Expected at least one IPv4 finding");
        assertTrue(
            ipv4Findings.stream().anyMatch(f -> f.getValue().equals("192.168.1.10")),
            "Expected finding for 192.168.1.10");
        assertTrue(
            ipv4Findings.stream().anyMatch(f -> f.getValue().equals("10.0.0.5")),
            "Expected finding for 10.0.0.5");
        ipv4Findings.forEach(f -> assertEquals(ContractOutputType.IPv4, f.getType()));
      }

      @Test
      @DisplayName(
          "Should not create IPv4 findings when raw output contains no valid IPv4 addresses")
      void shouldNotCreateIPv4FindingsWhenRawOutputContainsNoValidIPv4Addresses() throws Exception {
        // -- PREPARE --
        RegexGroup ipv4Group = OutputParserFixture.getRegexGroup("ipv4", "$0");
        ContractOutputElement ipv4Element =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.IPv4,
                "\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b",
                Set.of(ipv4Group),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(ipv4Element));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject ipv4Inject = (Inject) setup[0];
        String agentId = (String) setup[1];

        // 999.x.x.x is not a valid IPv4, the processor's validate() rejects it
        InjectExecutionInput input = buildStdoutInput("host 999.999.999.999 is unknown");

        // -- EXECUTE --
        performCallbackRequest(agentId, ipv4Inject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(ipv4Inject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(ipv4Inject.getId()).isEmpty());
      }

      // IPv6

      @Test
      @DisplayName("Should create a finding for each valid IPv6 address extracted from raw output")
      void shouldCreateFindingForEachValidIPv6AddressExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup ipv6Group = OutputParserFixture.getRegexGroup("ipv6", "$1");
        String ipv6Regex = "\\[([a-fA-F0-9:]+(?:%[a-zA-Z0-9]+)?)\\]:\\d+";
        ContractOutputElement ipv6Element =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.IPv6, ipv6Regex, Set.of(ipv6Group), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(ipv6Element));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject ipv6Inject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput =
            " UDP [fe80::1b03:a1ff:ccdb:b464%66]:1900 *:*\\n" + " UDP [2001:db8::1]:8080 *:*\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, ipv6Inject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(ipv6Inject.getId()).size() >= 2);

        List<Finding> ipv6Findings = injectTestHelper.findFindingsByInjectId(ipv6Inject.getId());
        assertEquals(2, ipv6Findings.size());
        assertTrue(
            ipv6Findings.stream().anyMatch(f -> f.getValue().contains("fe80::1b03:a1ff:ccdb:b464")),
            "Expected finding for fe80::1b03:a1ff:ccdb:b464");
        assertTrue(
            ipv6Findings.stream().anyMatch(f -> f.getValue().contains("2001:db8::1")),
            "Expected finding for 2001:db8::1");
        ipv6Findings.forEach(f -> assertEquals(ContractOutputType.IPv6, f.getType()));
      }

      @Test
      @DisplayName("Should not create IPv6 findings when raw output contains no IPv6 addresses")
      void shouldNotCreateIPv6FindingsWhenRawOutputContainsNoIPv6Addresses() throws Exception {
        // -- PREPARE --
        RegexGroup ipv6Group = OutputParserFixture.getRegexGroup("ipv6", "$1");
        String ipv6Regex = "\\[([a-fA-F0-9:]+(?:%[a-zA-Z0-9]+)?)\\]:\\d+";
        ContractOutputElement ipv6Element =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.IPv6, ipv6Regex, Set.of(ipv6Group), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(ipv6Element));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject ipv6Inject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no ipv6 addresses");

        // -- EXECUTE --
        performCallbackRequest(agentId, ipv6Inject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(ipv6Inject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(ipv6Inject.getId()).isEmpty());
      }

      // Username

      @Test
      @DisplayName("Should create a finding for each username extracted from raw output")
      void shouldCreateFindingForEachUsernameExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup usernameGroup = OutputParserFixture.getRegexGroup("username", "$1");
        ContractOutputElement usernameElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Username, "USER:\\s*(\\S+)", Set.of(usernameGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(usernameElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject usernameInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "USER: jdoe\\nUSER: asmith\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, usernameInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> injectTestHelper.findFindingsByInjectId(usernameInject.getId()).size() >= 2);

        List<Finding> usernameFindings =
            injectTestHelper.findFindingsByInjectId(usernameInject.getId());
        assertEquals(2, usernameFindings.size());
        assertTrue(
            usernameFindings.stream().anyMatch(f -> f.getValue().equals("jdoe")),
            "Expected finding for jdoe");
        assertTrue(
            usernameFindings.stream().anyMatch(f -> f.getValue().equals("asmith")),
            "Expected finding for asmith");
        usernameFindings.forEach(f -> assertEquals(ContractOutputType.Username, f.getType()));
      }

      @Test
      @DisplayName(
          "Should not create Username findings when raw output contains no username matches")
      void shouldNotCreateUsernameFindingsWhenRawOutputContainsNoUsernameMatches()
          throws Exception {
        // -- PREPARE --
        RegexGroup usernameGroup = OutputParserFixture.getRegexGroup("username", "$1");
        ContractOutputElement usernameElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Username, "USER:\\s*(\\S+)", Set.of(usernameGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(usernameElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject usernameInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no users found");

        // -- EXECUTE --
        performCallbackRequest(agentId, usernameInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(usernameInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(usernameInject.getId()).isEmpty());
      }

      // Share

      @Test
      @DisplayName("Should create a finding for each network share extracted from raw output")
      void shouldCreateFindingForEachShareExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup shareNameGroup = OutputParserFixture.getRegexGroup("share_name", "$1");
        RegexGroup permissionsGroup = OutputParserFixture.getRegexGroup("permissions", "$2");
        ContractOutputElement shareElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Share,
                "SHARE:\\s*(\\S+)\\s+(\\w+)",
                Set.of(shareNameGroup, permissionsGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(shareElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject shareInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "SHARE: SYSVOL READ\\nSHARE: NETLOGON WRITE\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, shareInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(shareInject.getId()).size() >= 2);

        List<Finding> shareFindings = injectTestHelper.findFindingsByInjectId(shareInject.getId());
        assertEquals(2, shareFindings.size());
        assertTrue(
            shareFindings.stream().anyMatch(f -> f.getValue().contains("SYSVOL")),
            "Expected finding for SYSVOL share");
        assertTrue(
            shareFindings.stream().anyMatch(f -> f.getValue().contains("NETLOGON")),
            "Expected finding for NETLOGON share");
        shareFindings.forEach(f -> assertEquals(ContractOutputType.Share, f.getType()));
      }

      @Test
      @DisplayName("Should not create Share findings when raw output contains no share matches")
      void shouldNotCreateShareFindingsWhenRawOutputContainsNoShareMatches() throws Exception {
        // -- PREPARE --
        RegexGroup shareNameGroup = OutputParserFixture.getRegexGroup("share_name", "$1");
        RegexGroup permissionsGroup = OutputParserFixture.getRegexGroup("permissions", "$2");
        ContractOutputElement shareElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Share,
                "SHARE:\\s*(\\S+)\\s+(\\w+)",
                Set.of(shareNameGroup, permissionsGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(shareElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject shareInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no shares found");

        // -- EXECUTE --
        performCallbackRequest(agentId, shareInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(shareInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(shareInject.getId()).isEmpty());
      }

      // AdminUsername

      @Test
      @DisplayName("Should create a finding for each admin username extracted from raw output")
      void shouldCreateFindingForEachAdminUsernameExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup usernameGroup = OutputParserFixture.getRegexGroup("username", "$1");
        ContractOutputElement adminUsernameElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.AdminUsername, "ADMIN:\\s*(\\S+)", Set.of(usernameGroup), true);
        OutputParser outputParser =
            OutputParserFixture.getOutputParser(Set.of(adminUsernameElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject adminInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "ADMIN: administrator\\nADMIN: sysadmin\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, adminInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(adminInject.getId()).size() >= 2);

        List<Finding> adminFindings = injectTestHelper.findFindingsByInjectId(adminInject.getId());
        assertEquals(2, adminFindings.size());
        assertTrue(
            adminFindings.stream().anyMatch(f -> f.getValue().equals("administrator")),
            "Expected finding for administrator");
        assertTrue(
            adminFindings.stream().anyMatch(f -> f.getValue().equals("sysadmin")),
            "Expected finding for sysadmin");
        adminFindings.forEach(f -> assertEquals(ContractOutputType.AdminUsername, f.getType()));
      }

      @Test
      @DisplayName(
          "Should not create AdminUsername findings when raw output contains no admin matches")
      void shouldNotCreateAdminUsernameFindingsWhenRawOutputContainsNoAdminMatches()
          throws Exception {
        // -- PREPARE --
        RegexGroup usernameGroup = OutputParserFixture.getRegexGroup("username", "$1");
        ContractOutputElement adminUsernameElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.AdminUsername, "ADMIN:\\s*(\\S+)", Set.of(usernameGroup), true);
        OutputParser outputParser =
            OutputParserFixture.getOutputParser(Set.of(adminUsernameElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject adminInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no admin accounts here");

        // -- EXECUTE --
        performCallbackRequest(agentId, adminInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(adminInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(adminInject.getId()).isEmpty());
      }

      // Group

      @Test
      @DisplayName("Should create a finding for each group extracted from raw output")
      void shouldCreateFindingForEachGroupExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup groupNameGroup = OutputParserFixture.getRegexGroup("group_name", "$1");
        ContractOutputElement groupElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Group, "GROUP:\\s*(\\S+)", Set.of(groupNameGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(groupElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject groupInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "GROUP: Administrators\\nGROUP: DomainAdmins\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, groupInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(groupInject.getId()).size() >= 2);

        List<Finding> groupFindings = injectTestHelper.findFindingsByInjectId(groupInject.getId());
        assertEquals(2, groupFindings.size());
        assertTrue(
            groupFindings.stream().anyMatch(f -> f.getValue().equals("Administrators")),
            "Expected finding for Administrators");
        assertTrue(
            groupFindings.stream().anyMatch(f -> f.getValue().equals("DomainAdmins")),
            "Expected finding for DomainAdmins");
        groupFindings.forEach(f -> assertEquals(ContractOutputType.Group, f.getType()));
      }

      @Test
      @DisplayName("Should not create Group findings when raw output contains no group matches")
      void shouldNotCreateGroupFindingsWhenRawOutputContainsNoGroupMatches() throws Exception {
        // -- PREPARE --
        RegexGroup groupNameGroup = OutputParserFixture.getRegexGroup("group_name", "$1");
        ContractOutputElement groupElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Group, "GROUP:\\s*(\\S+)", Set.of(groupNameGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(groupElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject groupInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no groups found");

        // -- EXECUTE --
        performCallbackRequest(agentId, groupInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(groupInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(groupInject.getId()).isEmpty());
      }

      // Computer

      @Test
      @DisplayName("Should create a finding for each computer name extracted from raw output")
      void shouldCreateFindingForEachComputerExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup computerNameGroup = OutputParserFixture.getRegexGroup("computer_name", "$1");
        ContractOutputElement computerElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Computer,
                "COMPUTER:\\s*(\\S+)",
                Set.of(computerNameGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(computerElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject computerInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "COMPUTER: DESKTOP-ABC\\nCOMPUTER: SERVER01\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, computerInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> injectTestHelper.findFindingsByInjectId(computerInject.getId()).size() >= 2);

        List<Finding> computerFindings =
            injectTestHelper.findFindingsByInjectId(computerInject.getId());
        assertEquals(2, computerFindings.size());
        assertTrue(
            computerFindings.stream().anyMatch(f -> f.getValue().equals("DESKTOP-ABC")),
            "Expected finding for DESKTOP-ABC");
        assertTrue(
            computerFindings.stream().anyMatch(f -> f.getValue().equals("SERVER01")),
            "Expected finding for SERVER01");
        computerFindings.forEach(f -> assertEquals(ContractOutputType.Computer, f.getType()));
      }

      @Test
      @DisplayName(
          "Should not create Computer findings when raw output contains no computer matches")
      void shouldNotCreateComputerFindingsWhenRawOutputContainsNoComputerMatches()
          throws Exception {
        // -- PREPARE --
        RegexGroup computerNameGroup = OutputParserFixture.getRegexGroup("computer_name", "$1");
        ContractOutputElement computerElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Computer,
                "COMPUTER:\\s*(\\S+)",
                Set.of(computerNameGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(computerElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject computerInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no computers found");

        // -- EXECUTE --
        performCallbackRequest(agentId, computerInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(computerInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(computerInject.getId()).isEmpty());
      }

      // PasswordPolicy

      @Test
      @DisplayName(
          "Should create a finding for each password policy entry extracted from raw output")
      void shouldCreateFindingForEachPasswordPolicyEntryExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup keyGroup = OutputParserFixture.getRegexGroup("key", "$1");
        RegexGroup valueGroup = OutputParserFixture.getRegexGroup("value", "$2");
        ContractOutputElement passwordPolicyElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.PasswordPolicy,
                "POLICY:\\s*(\\S+)\\s+=\\s+(\\S+)",
                Set.of(keyGroup, valueGroup),
                true);
        OutputParser outputParser =
            OutputParserFixture.getOutputParser(Set.of(passwordPolicyElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject policyInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "POLICY: MinPasswordLength = 8\\nPOLICY: MaxPasswordAge = 90\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, policyInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(policyInject.getId()).size() >= 2);

        List<Finding> policyFindings =
            injectTestHelper.findFindingsByInjectId(policyInject.getId());
        assertEquals(2, policyFindings.size());
        assertTrue(
            policyFindings.stream().anyMatch(f -> f.getValue().contains("MinPasswordLength")),
            "Expected finding for MinPasswordLength");
        assertTrue(
            policyFindings.stream().anyMatch(f -> f.getValue().contains("MaxPasswordAge")),
            "Expected finding for MaxPasswordAge");
        policyFindings.forEach(f -> assertEquals(ContractOutputType.PasswordPolicy, f.getType()));
      }

      @Test
      @DisplayName(
          "Should not create PasswordPolicy findings when raw output contains no policy matches")
      void shouldNotCreatePasswordPolicyFindingsWhenRawOutputContainsNoPolicyMatches()
          throws Exception {
        // -- PREPARE --
        RegexGroup keyGroup = OutputParserFixture.getRegexGroup("key", "$1");
        RegexGroup valueGroup = OutputParserFixture.getRegexGroup("value", "$2");
        ContractOutputElement passwordPolicyElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.PasswordPolicy,
                "POLICY:\\s*(\\S+)\\s+=\\s+(\\S+)",
                Set.of(keyGroup, valueGroup),
                true);
        OutputParser outputParser =
            OutputParserFixture.getOutputParser(Set.of(passwordPolicyElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject policyInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no policy settings here");

        // -- EXECUTE --
        performCallbackRequest(agentId, policyInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(policyInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(policyInject.getId()).isEmpty());
      }

      // Delegation

      @Test
      @DisplayName("Should create a finding for each delegation account extracted from raw output")
      void shouldCreateFindingForEachDelegationExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup accountGroup = OutputParserFixture.getRegexGroup("account", "$1");
        ContractOutputElement delegationElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Delegation, "DELEGATION:\\s*(\\S+)", Set.of(accountGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(delegationElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject delegationInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "DELEGATION: svc_account\\nDELEGATION: http_service\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, delegationInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () ->
                    injectTestHelper.findFindingsByInjectId(delegationInject.getId()).size() >= 2);

        List<Finding> delegationFindings =
            injectTestHelper.findFindingsByInjectId(delegationInject.getId());
        assertEquals(2, delegationFindings.size());
        assertTrue(
            delegationFindings.stream().anyMatch(f -> f.getValue().equals("svc_account")),
            "Expected finding for svc_account");
        assertTrue(
            delegationFindings.stream().anyMatch(f -> f.getValue().equals("http_service")),
            "Expected finding for http_service");
        delegationFindings.forEach(f -> assertEquals(ContractOutputType.Delegation, f.getType()));
      }

      @Test
      @DisplayName(
          "Should not create Delegation findings when raw output contains no delegation matches")
      void shouldNotCreateDelegationFindingsWhenRawOutputContainsNoDelegationMatches()
          throws Exception {
        // -- PREPARE --
        RegexGroup accountGroup = OutputParserFixture.getRegexGroup("account", "$1");
        ContractOutputElement delegationElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Delegation, "DELEGATION:\\s*(\\S+)", Set.of(accountGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(delegationElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject delegationInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no delegation configured");

        // -- EXECUTE --
        performCallbackRequest(agentId, delegationInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(delegationInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(delegationInject.getId()).isEmpty());
      }

      // Sid

      @Test
      @DisplayName("Should create a finding for each SID extracted from raw output")
      void shouldCreateFindingForEachSidExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup sidGroup = OutputParserFixture.getRegexGroup("sid", "$1");
        ContractOutputElement sidElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Sid, "SID:\\s*(S-\\d-[\\d-]+)", Set.of(sidGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(sidElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject sidInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "SID: S-1-5-21-12345\\nSID: S-1-5-21-67890\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, sidInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(sidInject.getId()).size() >= 2);

        List<Finding> sidFindings = injectTestHelper.findFindingsByInjectId(sidInject.getId());
        assertEquals(2, sidFindings.size());
        assertTrue(
            sidFindings.stream().anyMatch(f -> f.getValue().equals("S-1-5-21-12345")),
            "Expected finding for S-1-5-21-12345");
        assertTrue(
            sidFindings.stream().anyMatch(f -> f.getValue().equals("S-1-5-21-67890")),
            "Expected finding for S-1-5-21-67890");
        sidFindings.forEach(f -> assertEquals(ContractOutputType.Sid, f.getType()));
      }

      @Test
      @DisplayName("Should not create Sid findings when raw output contains no SID matches")
      void shouldNotCreateSidFindingsWhenRawOutputContainsNoSidMatches() throws Exception {
        // -- PREPARE --
        RegexGroup sidGroup = OutputParserFixture.getRegexGroup("sid", "$1");
        ContractOutputElement sidElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Sid, "SID:\\s*(S-\\d-[\\d-]+)", Set.of(sidGroup), true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(sidElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject sidInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no SIDs found here");

        // -- EXECUTE --
        performCallbackRequest(agentId, sidInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(sidInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(sidInject.getId()).isEmpty());
      }

      // Vulnerability

      @Test
      @DisplayName("Should create a finding for each vulnerability extracted from raw output")
      void shouldCreateFindingForEachVulnerabilityExtractedFromRawOutput() throws Exception {
        // -- PREPARE --
        RegexGroup nameGroup = OutputParserFixture.getRegexGroup("name", "$1");
        RegexGroup statusGroup = OutputParserFixture.getRegexGroup("status", "$2");
        ContractOutputElement vulnerabilityElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Vulnerability,
                "VULN:\\s*(\\S+)\\s+(\\S+)",
                Set.of(nameGroup, statusGroup),
                true);
        OutputParser outputParser =
            OutputParserFixture.getOutputParser(Set.of(vulnerabilityElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject vulnInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "VULN: EternalBlue EXPLOITABLE\\nVULN: BlueKeep PATCHED\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, vulnInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(vulnInject.getId()).size() >= 2);

        List<Finding> vulnFindings = injectTestHelper.findFindingsByInjectId(vulnInject.getId());
        assertEquals(2, vulnFindings.size());
        assertTrue(
            vulnFindings.stream().anyMatch(f -> f.getValue().contains("EternalBlue")),
            "Expected finding for EternalBlue");
        assertTrue(
            vulnFindings.stream().anyMatch(f -> f.getValue().contains("BlueKeep")),
            "Expected finding for BlueKeep");
        vulnFindings.forEach(f -> assertEquals(ContractOutputType.Vulnerability, f.getType()));
      }

      @Test
      @DisplayName(
          "Should not create Vulnerability findings when raw output contains no vulnerability matches")
      void shouldNotCreateVulnerabilityFindingsWhenRawOutputContainsNoVulnerabilityMatches()
          throws Exception {
        // -- PREPARE --
        RegexGroup nameGroup = OutputParserFixture.getRegexGroup("name", "$1");
        RegexGroup statusGroup = OutputParserFixture.getRegexGroup("status", "$2");
        ContractOutputElement vulnerabilityElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.Vulnerability,
                "VULN:\\s*(\\S+)\\s+(\\S+)",
                Set.of(nameGroup, statusGroup),
                true);
        OutputParser outputParser =
            OutputParserFixture.getOutputParser(Set.of(vulnerabilityElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject vulnInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no vulnerabilities found");

        // -- EXECUTE --
        performCallbackRequest(agentId, vulnInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(vulnInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(vulnInject.getId()).isEmpty());
      }

      // AccountWithPasswordNotRequired

      @Test
      @DisplayName(
          "Should create a finding for each account with password not required extracted from raw output")
      void shouldCreateFindingForEachAccountWithPasswordNotRequiredExtractedFromRawOutput()
          throws Exception {
        // -- PREPARE --
        RegexGroup accountGroup = OutputParserFixture.getRegexGroup("account", "$1");
        ContractOutputElement accountElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.AccountWithPasswordNotRequired,
                "NOPASS:\\s*(\\S+)",
                Set.of(accountGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(accountElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject nopassInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "NOPASS: guest_account\\nNOPASS: test_user\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, nopassInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(nopassInject.getId()).size() >= 2);

        List<Finding> nopassFindings =
            injectTestHelper.findFindingsByInjectId(nopassInject.getId());
        assertEquals(2, nopassFindings.size());
        assertTrue(
            nopassFindings.stream().anyMatch(f -> f.getValue().equals("guest_account")),
            "Expected finding for guest_account");
        assertTrue(
            nopassFindings.stream().anyMatch(f -> f.getValue().equals("test_user")),
            "Expected finding for test_user");
        nopassFindings.forEach(
            f -> assertEquals(ContractOutputType.AccountWithPasswordNotRequired, f.getType()));
      }

      @Test
      @DisplayName(
          "Should not create AccountWithPasswordNotRequired findings when raw output contains no matches")
      void shouldNotCreateAccountWithPasswordNotRequiredFindingsWhenRawOutputContainsNoMatches()
          throws Exception {
        // -- PREPARE --
        RegexGroup accountGroup = OutputParserFixture.getRegexGroup("account", "$1");
        ContractOutputElement accountElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.AccountWithPasswordNotRequired,
                "NOPASS:\\s*(\\S+)",
                Set.of(accountGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(accountElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject nopassInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("all accounts require passwords");

        // -- EXECUTE --
        performCallbackRequest(agentId, nopassInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(nopassInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(nopassInject.getId()).isEmpty());
      }

      // AsreproastableAccount

      @Test
      @DisplayName(
          "Should create a finding for each AS-REP roastable account extracted from raw output")
      void shouldCreateFindingForEachAsreproastableAccountExtractedFromRawOutput()
          throws Exception {
        // -- PREPARE --
        RegexGroup usernameGroup = OutputParserFixture.getRegexGroup("username", "$1");
        ContractOutputElement asrepElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.AsreproastableAccount,
                "ASREP:\\s*(\\S+)",
                Set.of(usernameGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(asrepElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject asrepInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "ASREP: victim_user\\nASREP: service_account\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, asrepInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(asrepInject.getId()).size() >= 2);

        List<Finding> asrepFindings = injectTestHelper.findFindingsByInjectId(asrepInject.getId());
        assertEquals(2, asrepFindings.size());
        assertTrue(
            asrepFindings.stream().anyMatch(f -> f.getValue().equals("victim_user")),
            "Expected finding for victim_user");
        assertTrue(
            asrepFindings.stream().anyMatch(f -> f.getValue().equals("service_account")),
            "Expected finding for service_account");
        asrepFindings.forEach(
            f -> assertEquals(ContractOutputType.AsreproastableAccount, f.getType()));
      }

      @Test
      @DisplayName(
          "Should not create AsreproastableAccount findings when raw output contains no AS-REP matches")
      void shouldNotCreateAsreproastableAccountFindingsWhenRawOutputContainsNoAsrepMatches()
          throws Exception {
        // -- PREPARE --
        RegexGroup usernameGroup = OutputParserFixture.getRegexGroup("username", "$1");
        ContractOutputElement asrepElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.AsreproastableAccount,
                "ASREP:\\s*(\\S+)",
                Set.of(usernameGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(asrepElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject asrepInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no AS-REP roastable accounts found");

        // -- EXECUTE --
        performCallbackRequest(agentId, asrepInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(asrepInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(asrepInject.getId()).isEmpty());
      }

      // KerberoastableAccount

      @Test
      @DisplayName(
          "Should create a finding for each Kerberoastable account extracted from raw output")
      void shouldCreateFindingForEachKerberoastableAccountExtractedFromRawOutput()
          throws Exception {
        // -- PREPARE --
        RegexGroup usernameGroup = OutputParserFixture.getRegexGroup("username", "$1");
        ContractOutputElement kerbElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.KerberoastableAccount,
                "KERB:\\s*(\\S+)",
                Set.of(usernameGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(kerbElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject kerbInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        String rawOutput = "KERB: krbtgt\\nKERB: sql_service\\n";
        InjectExecutionInput input = buildStdoutInput(rawOutput);

        // -- EXECUTE --
        performCallbackRequest(agentId, kerbInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.findFindingsByInjectId(kerbInject.getId()).size() >= 2);

        List<Finding> kerbFindings = injectTestHelper.findFindingsByInjectId(kerbInject.getId());
        assertEquals(2, kerbFindings.size());
        assertTrue(
            kerbFindings.stream().anyMatch(f -> f.getValue().equals("krbtgt")),
            "Expected finding for krbtgt");
        assertTrue(
            kerbFindings.stream().anyMatch(f -> f.getValue().equals("sql_service")),
            "Expected finding for sql_service");
        kerbFindings.forEach(
            f -> assertEquals(ContractOutputType.KerberoastableAccount, f.getType()));
      }

      @Test
      @DisplayName(
          "Should not create KerberoastableAccount findings when raw output contains no Kerberoastable matches")
      void shouldNotCreateKerberoastableAccountFindingsWhenRawOutputContainsNoKerbMatches()
          throws Exception {
        // -- PREPARE --
        RegexGroup usernameGroup = OutputParserFixture.getRegexGroup("username", "$1");
        ContractOutputElement kerbElement =
            OutputParserFixture.getContractOutputElement(
                ContractOutputType.KerberoastableAccount,
                "KERB:\\s*(\\S+)",
                Set.of(usernameGroup),
                true);
        OutputParser outputParser = OutputParserFixture.getOutputParser(Set.of(kerbElement));
        Object[] setup = buildInjectWithOutputParser(outputParser);
        Inject kerbInject = (Inject) setup[0];
        String agentId = (String) setup[1];

        InjectExecutionInput input = buildStdoutInput("no Kerberoastable accounts found");

        // -- EXECUTE --
        performCallbackRequest(agentId, kerbInject.getId(), input);

        // -- ASSERT --
        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(() -> injectTestHelper.hasInjectStatusTrace(kerbInject.getId()));
        assertTrue(injectTestHelper.findFindingsByInjectId(kerbInject.getId()).isEmpty());
      }
    }

    @Nested
    @DisplayName("Asset Processing Handling")
    @KeepRabbit
    class AssetProcessingHandlingTest {

      @Test
      @DisplayName("Should create an asset agentless")
      void shouldCreateAssetFromStructuredOutput() throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("Creation Assets");
        input.setOutputStructured(
            """
                            {
                             	"found_assets": [
                             		{
                             			"name": "Asset A",
                             			"type": "Endpoint",
                             			"description": "describe asset A",
                             			"external_reference": "https://shodan.io/.../assetA",
                             			"tags": ["source:shodan.io"],
                             			"extended_attributes": {
                             				"ip_addresses": ["192.168.0.2"],
                             				"platform": "Windows",
                             				"hostname": "test.if",
                             				"mac_addresses": ["1::22:45:67:89:AB"],
                             				"arch": "x86_64",
                             				"end_of_life": true
                             			}
                             		},
                             		{
                             			"name": "Asset B",
                             			"type": "Endpoint",
                             			"description": "describe asset B",
                             			"external_reference": "https://shodan.io/.../assetB",
                             			"tags": ["source:shodan.io"],
                             			"extended_attributes": {
                             				"ip_addresses": ["192.168.0.10"],
                             				"platform": "Linux",
                             				"hostname": "test.io",
                             				"mac_addresses": ["1::23:45:67:89:AB"],
                             				"arch": "arm64",
                             				"end_of_life": false
                             			}
                             		}
                             	]
                             }
                """);
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("SUCCESS");
        Inject inject = getPendingInjectWithAssets();
        Injector injector = InjectorFixture.createDefaultPayloadInjector();
        injectTestHelper.forceSaveInjector(injector);
        ObjectNode convertedContent =
            (ObjectNode)
                mapper.readTree(
                    """
                        {
                          "outputs": [
                            {
                               "field": "found_assets",
                               "isFindingCompatible": false,
                               "isMultiple": true,
                               "labels": [
                                   "shodan"
                               ],
                               "type": "asset"
                           }
                          ]
                        }
                        """);
        convertedContent.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(List.of()));
        InjectorContract injectorContract =
            InjectorContractFixture.createInjectorContract(convertedContent);
        injectorContract.setInjector(injector);
        InjectorContract injectorContractSaved =
            injectTestHelper.forceSaveInjectorContract(injectorContract);
        inject.setInjectorContract(injectorContractSaved);
        inject.setContent(convertedContent);
        injectTestHelper.forceSaveInject(inject);

        // -- EXECUTE --
        performAgentlessCallbackRequest(inject.getId(), input);

        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  List<Endpoint> endpointsA =
                      endpointRepository.findByExternalReference("https://shodan.io/.../assetA");
                  List<Endpoint> endpointsB =
                      endpointRepository.findByExternalReference("https://shodan.io/.../assetB");
                  return endpointsA.size() == 1 && endpointsB.size() == 1;
                });

        List<Endpoint> endpointsA =
            endpointRepository.findByExternalReference("https://shodan.io/.../assetA");
        List<Endpoint> endpointsB =
            endpointRepository.findByExternalReference("https://shodan.io/.../assetB");
        assertEquals(1, endpointsA.size());
        assertEquals(1, endpointsB.size());
        assertEquals("test.if", endpointsA.getFirst().getHostname());
        assertEquals("test.io", endpointsB.getFirst().getHostname());
      }

      @Test
      @DisplayName("Should find asset from structured output and not create a new one")
      void shouldFindAssetFromStructuredOutputAndNotCreateNewAsset() throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("Creation Assets");
        input.setOutputStructured(
            """
                            {
                             	"found_assets": [
                             		{
                             			"name": "Asset A",
                             			"type": "Endpoint",
                             			"description": "describe asset A",
                             			"external_reference": "https://shodan.io/.../assetA",
                             			"tags": ["source:shodan.io"],
                             			"extended_attributes": {
                             				"ip_addresses": ["192.168.0.2"],
                             				"platform": "Windows",
                             				"hostname": "test.if",
                             				"mac_addresses": ["1::22:45:67:89:AB"],
                             				"arch": "x86_64",
                             				"end_of_life": true
                             			}
                             		},
                             		{
                             			"name": "Asset B",
                             			"type": "Endpoint",
                             			"description": "describe asset B",
                             			"external_reference": "https://shodan.io/.../assetA",
                             			"tags": ["source:shodan.io"],
                             			"extended_attributes": {
                             				"ip_addresses": ["192.168.0.2"],
                             				"platform": "Windows",
                             				"hostname": "test.if",
                             				"mac_addresses": ["1::22:45:67:89:AB"],
                             				"arch": "arm64",
                             				"end_of_life": false
                             			}
                             		}
                             	]
                             }
                """);
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("SUCCESS");
        Inject inject = getPendingInjectWithAssets();
        Injector injector = InjectorFixture.createDefaultPayloadInjector();
        injectTestHelper.forceSaveInjector(injector);
        ObjectNode convertedContent =
            (ObjectNode)
                mapper.readTree(
                    """
                        {
                          "outputs": [
                            {
                               "field": "found_assets",
                               "isFindingCompatible": false,
                               "isMultiple": true,
                               "labels": [
                                   "shodan"
                               ],
                               "type": "asset"
                           }
                          ]
                        }
                        """);
        convertedContent.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(List.of()));
        InjectorContract injectorContract =
            InjectorContractFixture.createInjectorContract(convertedContent);
        injectorContract.setInjector(injector);
        InjectorContract injectorContractSaved =
            injectTestHelper.forceSaveInjectorContract(injectorContract);
        inject.setInjectorContract(injectorContractSaved);
        inject.setContent(convertedContent);
        injectTestHelper.forceSaveInject(inject);

        // -- EXECUTE --
        performAgentlessCallbackRequest(inject.getId(), input);

        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  List<Endpoint> endpointsA =
                      endpointRepository.findByExternalReference("https://shodan.io/.../assetA");
                  return endpointsA.size() == 1;
                });

        List<Endpoint> endpointsA =
            endpointRepository.findByExternalReference("https://shodan.io/.../assetA");
        assertEquals(1, endpointsA.size());
        assertEquals("test.if", endpointsA.getFirst().getHostname());
      }

      @Test
      @DisplayName("Should not produce anything when contract has no asset outputType")
      void shouldNotProduceAnythingWhenContractHasNoAssetOutputType() throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("Creation Assets");
        input.setOutputStructured(
            """
                            {
                             	"found_assets": [
                             		{
                             			"name": "Asset A",
                             			"type": "Endpoint",
                             			"description": "describe asset A",
                             			"external_reference": "https://shodan.io/.../assetA",
                             			"tags": ["source:shodan.io"],
                             			"extended_attributes": {
                             				"ip_addresses": ["192.168.0.2"],
                             				"platform": "Windows",
                             				"hostname": "test.if",
                             				"mac_addresses": ["1::22:45:67:89:AB"],
                             				"arch": "x86_64",
                             				"end_of_life": true
                             			}
                             		}
                             	]
                             }
                """);
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("SUCCESS");
        Inject inject = getPendingInjectWithAssets();
        Injector injector = InjectorFixture.createDefaultPayloadInjector();
        injectTestHelper.forceSaveInjector(injector);
        ObjectNode convertedContent =
            (ObjectNode)
                mapper.readTree(
                    """
                        {
                          "outputs": [
                            {
                               "field": "cve",
                               "isFindingCompatible": true,
                               "isMultiple": true,
                               "labels": [
                                   "shodan"
                               ],
                               "type": "cve"
                           }
                          ]
                        }
                        """);
        convertedContent.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(List.of()));
        InjectorContract injectorContract =
            InjectorContractFixture.createInjectorContract(convertedContent);
        injectorContract.setInjector(injector);
        InjectorContract injectorContractSaved =
            injectTestHelper.forceSaveInjectorContract(injectorContract);
        inject.setInjectorContract(injectorContractSaved);
        inject.setContent(convertedContent);
        injectTestHelper.forceSaveInject(inject);

        // -- EXECUTE --
        performAgentlessCallbackRequest(inject.getId(), input);

        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  List<Finding> findings = findingRepository.findAllByInjectId(inject.getId());
                  return findings.isEmpty();
                });
      }

      @Test
      @DisplayName("Should Not Produce Nothing When StructuredOutput Is Empty")
      void shouldNotProduceNothingWhenStructuredOutputIsEmpty() throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("Creation Assets");
        input.setOutputStructured("{}");
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("SUCCESS");
        Inject inject = getPendingInjectWithAssets();
        Injector injector = InjectorFixture.createDefaultPayloadInjector();
        injectTestHelper.forceSaveInjector(injector);
        ObjectNode convertedContent =
            (ObjectNode)
                mapper.readTree(
                    """
                        {
                          "outputs": [
                            {
                               "field": "found_assets",
                               "isFindingCompatible": false,
                               "isMultiple": true,
                               "labels": [
                                   "shodan"
                               ],
                               "type": "asset"
                           }
                          ]
                        }
                        """);
        convertedContent.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(List.of()));
        InjectorContract injectorContract =
            InjectorContractFixture.createInjectorContract(convertedContent);
        injectorContract.setInjector(injector);
        InjectorContract injectorContractSaved =
            injectTestHelper.forceSaveInjectorContract(injectorContract);
        inject.setInjectorContract(injectorContractSaved);
        inject.setContent(convertedContent);
        injectTestHelper.forceSaveInject(inject);

        // -- EXECUTE --
        performAgentlessCallbackRequest(inject.getId(), input);

        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  List<Endpoint> endpointsA =
                      endpointRepository.findByExternalReference("https://shodan.io/.../assetA");
                  return endpointsA.isEmpty();
                });
      }

      @Test
      @DisplayName("Should Create Asset Even If Some Informations Are Null")
      void shouldCreateAssetEvenIfSomeInformationAreNull() throws Exception {
        // -- PREPARE --
        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("Creation Assets");
        input.setOutputStructured(
            """
                            {
                             	"found_assets": [
                             		{
                             			"name": "Asset C",
                             			"type": "Endpoint",
                             			"description": "describe asset C",
                             			"external_reference": "https://shodan.io/.../assetC",
                             			"tags": ["source:shodan.io"],
                             			"extended_attributes": {
                             				"ip_addresses": [],
                             				"platform": "Unknown",
                             				"mac_addresses": ["1::22:45:67:89:AB"],
                             				"arch": "x86_64",
                             				"end_of_life": true
                             			}
                             		}
                             	]
                             }
                """);
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("SUCCESS");
        Inject inject = getPendingInjectWithAssets();
        Injector injector = InjectorFixture.createDefaultPayloadInjector();
        injectTestHelper.forceSaveInjector(injector);
        ObjectNode convertedContent =
            (ObjectNode)
                mapper.readTree(
                    """
                        {
                          "outputs": [
                            {
                               "field": "found_assets",
                               "isFindingCompatible": false,
                               "isMultiple": true,
                               "labels": [
                                   "shodan:asset"
                               ],
                               "type": "asset"
                           }
                          ]
                        }
                        """);
        convertedContent.set(CONTRACT_CONTENT_FIELDS, objectMapper.valueToTree(List.of()));
        InjectorContract injectorContract =
            InjectorContractFixture.createInjectorContract(convertedContent);
        injectorContract.setInjector(injector);
        InjectorContract injectorContractSaved =
            injectTestHelper.forceSaveInjectorContract(injectorContract);
        inject.setInjectorContract(injectorContractSaved);
        inject.setContent(convertedContent);
        injectTestHelper.forceSaveInject(inject);

        // -- EXECUTE --
        performAgentlessCallbackRequest(inject.getId(), input);

        Awaitility.await()
            .atMost(15, TimeUnit.SECONDS)
            .with()
            .pollInterval(1, TimeUnit.SECONDS)
            .until(
                () -> {
                  List<Endpoint> endpointsA =
                      endpointRepository.findByExternalReference("https://shodan.io/.../assetC");
                  return endpointsA.size() == 1;
                });

        List<Endpoint> endpointsA =
            endpointRepository.findByExternalReference("https://shodan.io/.../assetC");
        assertEquals(1, endpointsA.size());
        assertEquals("", endpointsA.getFirst().getHostname());
        assertEquals(Endpoint.PLATFORM_TYPE.Unknown, endpointsA.getFirst().getPlatform());
        assertEquals(Endpoint.PLATFORM_ARCH.x86_64, endpointsA.getFirst().getArch());
        assertTrue(endpointsA.getFirst().isEoL());
        assertEquals(0, endpointsA.getFirst().getIps().length);
      }
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Fetch execution traces for inject/atomic overview")
  @KeepRabbit
  class ShouldFetchExecutionTracesForInjectOverview {

    private Inject buildInjectWithTraces(List<ExecutionTraceComposer.Composer> traces) {
      return injectComposer
          .forInject(InjectFixture.getDefaultInject())
          .withInjectStatus(
              injectStatusComposer
                  .forInjectStatus(InjectStatusFixture.createPendingInjectStatus())
                  .withExecutionTraces(traces))
          .persist()
          .get();
    }

    private String performGetRequest(
        String baseUri, String injectId, String targetId, TargetType targetType) throws Exception {
      MockHttpServletRequestBuilder requestBuilder =
          get(baseUri).accept(MediaType.APPLICATION_JSON).param("injectId", injectId);

      if (targetId != null) {
        requestBuilder.param("targetId", targetId);
        requestBuilder.param("targetType", targetType.name());
      }

      return mvc.perform(requestBuilder)
          .andExpect(status().is2xxSuccessful())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    private AgentComposer.Composer createAgentWithEndpoint() {
      AgentComposer.Composer agent =
          agentComposer.forAgent(AgentFixture.createDefaultAgentService());
      endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).withAgent(agent).persist();
      return agent;
    }

    private void assertExecutionTracesMatch(String response) {
      assertThatJson(response)
          .when(Option.IGNORING_ARRAY_ORDER)
          .inPath("[*].execution_message")
          .isArray()
          .contains("Info", "Success");

      assertThatJson(response)
          .when(Option.IGNORING_ARRAY_ORDER)
          .inPath("[*].execution_action")
          .isArray()
          .contains(ExecutionTraceAction.START, ExecutionTraceAction.COMPLETE);

      assertThatJson(response)
          .when(Option.IGNORING_ARRAY_ORDER)
          .inPath("[*].execution_status")
          .isArray()
          .contains(ExecutionTraceStatus.INFO, ExecutionTraceStatus.SUCCESS);
    }

    @Test
    @DisplayName("Fetch execution traces by target and inject for an asset")
    void shouldFetchExecutionTracesByTargetAndInjectForAsset() throws Exception {
      AgentComposer.Composer agent = createAgentWithEndpoint();
      Inject inject =
          buildInjectWithTraces(
              List.of(
                  executionTraceComposer
                      .forExecutionTrace(ExecutionTraceFixture.createDefaultExecutionTraceStart())
                      .withAgent(agent),
                  executionTraceComposer
                      .forExecutionTrace(
                          ExecutionTraceFixture.createDefaultExecutionTraceComplete())
                      .withAgent(agent)));

      String response =
          performGetRequest(
              INJECT_URI + "/execution-traces",
              inject.getId(),
              agent.get().getAsset().getId(),
              TargetType.ASSETS);

      assertExecutionTracesMatch(response);
    }

    @Test
    @DisplayName("Fetch execution traces by target and inject")
    void shouldFetchExecutionTracesByTargetAndInject() throws Exception {
      AgentComposer.Composer agent = createAgentWithEndpoint();
      Inject inject =
          buildInjectWithTraces(
              List.of(
                  executionTraceComposer
                      .forExecutionTrace(ExecutionTraceFixture.createDefaultExecutionTraceStart())
                      .withAgent(agent),
                  executionTraceComposer
                      .forExecutionTrace(
                          ExecutionTraceFixture.createDefaultExecutionTraceComplete())
                      .withAgent(agent)));

      String response =
          performGetRequest(
              INJECT_URI + "/execution-traces",
              inject.getId(),
              agent.get().getId(),
              TargetType.AGENT);

      assertExecutionTracesMatch(response);
    }

    @Test
    @DisplayName("Fetch execution traces by target and inject for a team")
    void shouldFetchExecutionTracesByTargetAndInjectForTeam() throws Exception {
      UserComposer.Composer user =
          userComposer.forUser(UserFixture.getUser("Bob", "TEST", "bob-test@fake.email"));
      User savedPlayer = user.persist().get();
      Team savedTeam =
          teamComposer.forTeam(TeamFixture.getDefaultTeam()).withUser(user).persist().get();

      Inject inject =
          buildInjectWithTraces(
              List.of(
                  executionTraceComposer.forExecutionTrace(
                      ExecutionTraceFixture.createDefaultExecutionTraceStartWithIdentifiers(
                          List.of(savedPlayer.getId()))),
                  executionTraceComposer.forExecutionTrace(
                      ExecutionTraceFixture.createDefaultExecutionTraceCompleteWithIdentifiers(
                          List.of(savedPlayer.getId())))));

      String response =
          performGetRequest(
              INJECT_URI + "/execution-traces",
              inject.getId(),
              savedTeam.getId(),
              TargetType.TEAMS);

      assertExecutionTracesMatch(response);
    }

    @Test
    @DisplayName("Fetch execution traces by target and inject for a player")
    void shouldFetchExecutionTracesByTargetAndInjectForPlayer() throws Exception {
      UserComposer.Composer user =
          userComposer.forUser(UserFixture.getUser("Alice", "TEST", "alice-test@fake.email"));
      User savedPlayer = user.persist().get();

      Inject inject =
          buildInjectWithTraces(
              List.of(
                  executionTraceComposer.forExecutionTrace(
                      ExecutionTraceFixture.createDefaultExecutionTraceStartWithIdentifiers(
                          List.of(savedPlayer.getId()))),
                  executionTraceComposer.forExecutionTrace(
                      ExecutionTraceFixture.createDefaultExecutionTraceCompleteWithIdentifiers(
                          List.of(savedPlayer.getId())))));

      String response =
          performGetRequest(
              INJECT_URI + "/execution-traces",
              inject.getId(),
              savedPlayer.getId(),
              TargetType.PLAYERS);

      assertExecutionTracesMatch(response);
    }

    @Test
    @DisplayName("Fetch inject status with global traces by inject")
    void shouldFetchInjectStatusWithGlobalTracesByInject() throws Exception {
      Inject inject =
          buildInjectWithTraces(
              List.of(
                  executionTraceComposer.forExecutionTrace(
                      ExecutionTraceFixture.createDefaultExecutionTraceStart()),
                  executionTraceComposer.forExecutionTrace(
                      ExecutionTraceFixture.createDefaultExecutionTraceError())));

      String response =
          performGetRequest(INJECT_URI + "/status", inject.getId(), null, TargetType.AGENT);

      List<ExecutionTraceOutput> expectedTraces =
          List.of(
              ExecutionTraceOutput.builder()
                  .action(ExecutionTraceAction.START)
                  .message("Info")
                  .status(ExecutionTraceStatus.INFO)
                  .build(),
              ExecutionTraceOutput.builder()
                  .action(ExecutionTraceAction.COMPLETE)
                  .message("Error")
                  .status(ExecutionTraceStatus.ERROR)
                  .build());

      InjectStatusOutput expected = InjectStatusOutput.builder().traces(expectedTraces).build();

      assertThatJson(response)
          .whenIgnoringPaths(
              "status_id",
              "status_name",
              "tracking_sent_date",
              "tracking_end_date",
              "status_main_traces[*].execution_time")
          .isEqualTo(mapper.writeValueAsString(expected));
    }

    @Test
    @DisplayName("Should return 400 when target type is unsupported")
    void shouldReturn400WhenTargetTypeIsUnsupported() throws Exception {
      MockHttpServletRequestBuilder requestBuilder =
          get(INJECT_URI + "/execution-traces")
              .accept(MediaType.APPLICATION_JSON)
              .param("injectId", "someInjectId")
              .param("targetId", "someTargetId")
              .param("targetType", TargetType.ASSETS_GROUPS.name());

      mvc.perform(requestBuilder)
          .andExpect(status().isBadRequest())
          .andExpect(
              result -> assertInstanceOf(BadRequestException.class, result.getResolvedException()));
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Fetch documents for inject by payload")
  @KeepRabbit
  class ShouldFetchDocuments {

    private Inject getInjectWithPayloadAndFileDropDocumentsLinkedOnIt() {
      return injectComposer
          .forInject(InjectFixture.getDefaultInject())
          .withInjectorContract(
              injectorContractComposer
                  .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                  .withPayload(
                      payloadComposer
                          .forPayload(PayloadFixture.createDefaultFileDrop())
                          .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
                          .withFileDrop(
                              documentComposer.forDocument(
                                  DocumentFixture.getDocument(
                                      FileFixture.getPlainTextFileContent())))))
          .persist()
          .get();
    }

    private Inject getInjectWithPayloadAndExecutableDocumentsLinkedOnIt() {
      return injectComposer
          .forInject(InjectFixture.getDefaultInject())
          .withInjectorContract(
              injectorContractComposer
                  .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                  .withPayload(
                      payloadComposer
                          .forPayload(PayloadFixture.createDefaultExecutable())
                          .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
                          .withExecutable(
                              documentComposer.forDocument(
                                  DocumentFixture.getDocument(FileFixture.getBeadFileContent())))))
          .persist()
          .get();
    }

    private Inject getInjectWithoutPayload() {
      return injectComposer
          .forInject(InjectFixture.getDefaultInject())
          .withInjectorContract(
              injectorContractComposer.forInjectorContract(
                  InjectorContractFixture.createDefaultInjectorContract()))
          .persist()
          .get();
    }

    @Test
    @DisplayName("Should return drop file documents for inject id and payload id")
    void shouldReturnDropFileDocumentsForInjectIdAndPayloadId() throws Exception {
      // PREPARE
      Inject inject = getInjectWithPayloadAndFileDropDocumentsLinkedOnIt();
      InjectorContract injectorContract = inject.getInjectorContract().orElseThrow();
      Payload payload = injectorContract.getPayload();

      // EXECUTE
      MockHttpServletRequestBuilder requestBuilder =
          get(INJECT_URI + "/" + inject.getId() + "/payload/" + payload.getId() + "/documents")
              .accept(MediaType.APPLICATION_JSON);

      // ASSERT
      String response =
          mvc.perform(requestBuilder)
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response).node("[0].document_type").isEqualTo("text/plain");
    }

    @Test
    @DisplayName("Should return executable documents for inject id and payload id")
    void shouldReturnExecutableDocumentsForInjectIdAndPayloadId() throws Exception {
      // PREPARE
      Inject inject = getInjectWithPayloadAndExecutableDocumentsLinkedOnIt();
      InjectorContract injectorContract = inject.getInjectorContract().orElseThrow();
      Payload payload = injectorContract.getPayload();

      // EXECUTE
      MockHttpServletRequestBuilder requestBuilder =
          get(INJECT_URI + "/" + inject.getId() + "/payload/" + payload.getId() + "/documents")
              .accept(MediaType.APPLICATION_JSON);

      // ASSERT
      String response =
          mvc.perform(requestBuilder)
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response).node("[0].document_type").isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("Should return ElementNotFoundException for unknown inject")
    void shouldReturnElementNotFoundExceptionForUnknownInject() throws Exception {
      // EXECUTE
      MockHttpServletRequestBuilder requestBuilder =
          get(INJECT_URI + "/TEST/payload/TEST/documents").accept(MediaType.APPLICATION_JSON);

      // ASSERT
      String response =
          mvc.perform(requestBuilder)
              .andExpect(status().is4xxClientError())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response)
          .node("message")
          .isEqualTo("Element not found: Inject not found with id: TEST");
    }

    @Test
    @DisplayName("Should return ElementNotFoundException for inject without payload")
    void shouldReturnElementNotFoundExceptionForInjectWithoutPayload() throws Exception {
      // PREPARE
      Inject inject = getInjectWithoutPayload();

      // EXECUTE
      MockHttpServletRequestBuilder requestBuilder =
          get(INJECT_URI + "/" + inject.getId() + "/payload/TEST/documents")
              .accept(MediaType.APPLICATION_JSON);

      // ASSERT
      String response =
          mvc.perform(requestBuilder)
              .andExpect(status().is4xxClientError())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThatJson(response)
          .node("message")
          .isEqualTo("Element not found: payload not found on inject with id : " + inject.getId());
    }

    @Test
    @DisplayName("Should return BadRequestException when inject and payload mismatch")
    void shouldReturnBadRequestExceptionWhenInjectAndPayloadMismatch() throws Exception {
      // PREPARE
      Inject inject = getInjectWithPayloadAndFileDropDocumentsLinkedOnIt();

      // EXECUTE
      MockHttpServletRequestBuilder requestBuilder =
          get(INJECT_URI + "/" + inject.getId() + "/payload/TEST/documents")
              .accept(MediaType.APPLICATION_JSON);

      // ASSERT
      mvc.perform(requestBuilder).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "Should return List of Collector related to detection remediations from a payload when an Inject Id is given")
    void shouldReturnListCollectorRelatedToDetectionRemediationsWhenInjectIdIsGiven()
        throws Exception {
      // PREPARE
      Collector collector =
          collectorComposer
              .forCollector(CollectorFixture.createDefaultCollector("SENTINEL"))
              .persist()
              .get();

      entityManager.flush();
      entityManager.clear();

      Inject inject =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withPayload(
                          payloadComposer
                              .forPayload(PayloadFixture.createDefaultCommand())
                              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
                              .withDetectionRemediation(
                                  detectionRemediationComposer
                                      .forDetectionRemediation(
                                          DetectionRemediationFixture
                                              .createDefaultDetectionRemediation())
                                      .withCollector(collectorComposer.forCollector(collector)))))
              .persist()
              .get();

      // EXECUTE
      MockHttpServletRequestBuilder requestBuilder =
          get(ATOMIC_TESTING_URI + "/" + inject.getId() + "/collectors")
              .accept(MediaType.APPLICATION_JSON);

      // ASSERT
      String result =
          mvc.perform(requestBuilder)
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      JsonNode rootNode = new ObjectMapper().readTree(result);

      // ASSERTIONS
      assertTrue(rootNode.isArray(), "Response should be a JSON array");
      assertEquals(1, rootNode.size(), "There should be exactly 1 collector");

      JsonNode collectorNode = rootNode.get(0);
      assertEquals("SENTINEL", collectorNode.get("collector_name").asText());
    }
  }

  // ==========================================================================
  // Integration tests for the retry/requeue behaviour added to
  // BatchingInjectStatusService + ExecutionTracesBatchRequeueJob.
  //
  // These tests drive the batching service directly rather than going through
  // the MVC callback, because:
  //  - the MVC-based tests in handleInjectExecutionCallback force the sync path
  //    (setInjectTraceQueueService(null)) to avoid flaky RabbitMQ consumer
  //    lifecycle issues, and the retry logic only lives in the async batch path;
  //  - the sync path still throws DataIntegrityViolationException on a
  //    complete-before-PENDING race, so the retry branch can only be exercised
  //    by invoking BatchingInjectStatusService directly.
  // ==========================================================================
  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Batching inject callback retry/requeue behaviour")
  @KeepRabbit
  class BatchingInjectRetryIntegrationTest {

    private static final int MAX_RETRIES = 5;

    @Autowired private BatchingInjectStatusService batchingInjectStatusService;

    @SuppressWarnings("unchecked")
    private final BatchQueueService<InjectExecutionCallback> mockQueueService =
        mock(BatchQueueService.class);

    @BeforeEach
    void wireMockQueueService() {
      batchingInjectStatusService.setInjectTraceQueueService(mockQueueService);
    }

    @AfterEach
    void resetQueueService() {
      // Drain any leftovers and unwire so other tests keep the default (null) behaviour.
      try {
        batchingInjectStatusService.requeueCallbacks();
      } catch (Exception ignored) {
        // best-effort cleanup
      }
      batchingInjectStatusService.setInjectTraceQueueService(null);
    }

    private Inject buildPendingInjectWithAgent() {
      return injectTestHelper.getPendingInjectWithAssets(
          injectComposer,
          injectorContractComposer,
          endpointComposer,
          agentComposer,
          injectStatusComposer);
    }

    private Inject transitionToExecuting(Inject inject) {
      inject.getStatus().orElseThrow().setName(ExecutionStatus.EXECUTING);
      return injectTestHelper.forceSaveInject(inject);
    }

    private InjectExecutionCallback buildCompleteCallback(String injectId, String agentId) {
      InjectExecutionInput input = new InjectExecutionInput();
      input.setMessage("complete received");
      input.setAction(InjectExecutionAction.complete);
      input.setStatus("INFO");
      input.setDuration(1000);
      long emissionDate = Instant.now().toEpochMilli();
      return InjectExecutionCallback.builder()
          .injectId(injectId)
          .agentId(agentId)
          .injectExecutionInput(input)
          .emissionDate(emissionDate)
          .build();
    }

    @DisplayName(
        "Should queue a complete callback received before the inject is PENDING for retry, "
            + "without persisting any trace")
    @Test
    void shouldQueueCompleteCallbackForRetryWhenInjectIsNotPending() throws Exception {
      // -- PREPARE --
      Inject inject = buildPendingInjectWithAgent();
      inject = transitionToExecuting(inject);
      String agentId = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
      InjectExecutionCallback callback = buildCompleteCallback(inject.getId(), agentId);

      // -- EXECUTE --
      List<InjectExecutionCallback> processed =
          batchingInjectStatusService.handleInjectExecutionCallback(List.of(callback));

      // -- ASSERT --
      // Callback is not in the successfully-processed list: it was queued for retry.
      assertTrue(processed.isEmpty());
      // The retry counter was bumped on the callback instance.
      assertEquals(1, callback.getRetryCount());
      // No trace has been written to the DB — the inject is still in its pre-callback state.
      entityManager.flush();
      entityManager.clear();
      Inject reloaded = injectRepository.findById(inject.getId()).orElseThrow();
      InjectStatus reloadedStatus = reloaded.getStatus().orElseThrow();
      assertEquals(ExecutionStatus.EXECUTING, reloadedStatus.getName());
      assertTrue(
          reloadedStatus.getTraces().isEmpty(),
          "No execution trace should have been persisted for the rejected callback");

      // Draining the requeue queue publishes the callback to the external queue service.
      batchingInjectStatusService.requeueCallbacks();
      ArgumentCaptor<InjectExecutionCallback> captor =
          ArgumentCaptor.forClass(InjectExecutionCallback.class);
      verify(mockQueueService).publish(captor.capture());
      assertEquals(inject.getId(), captor.getValue().getInjectId());
      assertEquals(1, captor.getValue().getRetryCount());
    }

    @DisplayName(
        "Should process a previously retried callback successfully once the inject is PENDING")
    @Test
    void shouldProcessRequeuedCallbackAfterStatusTransition() throws Exception {
      // -- PREPARE --
      Inject inject = buildPendingInjectWithAgent();
      inject = transitionToExecuting(inject);
      String agentId = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
      InjectExecutionCallback callback = buildCompleteCallback(inject.getId(), agentId);

      // First attempt: inject still EXECUTING → callback is queued for retry.
      List<InjectExecutionCallback> firstPass =
          batchingInjectStatusService.handleInjectExecutionCallback(List.of(callback));
      assertTrue(firstPass.isEmpty());
      assertEquals(1, callback.getRetryCount());

      // Simulate the race resolving: inject transitions back to PENDING.
      inject.getStatus().orElseThrow().setName(ExecutionStatus.PENDING);
      injectTestHelper.forceSaveInject(inject);

      // Second attempt: equivalent to the Quartz requeue job republishing the callback
      // and the consumer calling handleInjectExecutionCallback again.
      List<InjectExecutionCallback> secondPass =
          batchingInjectStatusService.handleInjectExecutionCallback(List.of(callback));

      // -- ASSERT --
      assertEquals(1, secondPass.size());
      entityManager.flush();
      entityManager.clear();
      Inject reloaded = injectRepository.findById(inject.getId()).orElseThrow();
      InjectStatus reloadedStatus = reloaded.getStatus().orElseThrow();
      assertFalse(
          reloadedStatus.getTraces().isEmpty(),
          "A complete trace should have been written after the successful retry");
      assertTrue(
          reloadedStatus.getTraces().stream()
              .anyMatch(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction())),
          "A trace with action COMPLETE should have been persisted");
    }

    @DisplayName(
        "Should persist the execution trace once MAX_RETRIES is reached instead of republishing")
    @Test
    void shouldPersistExecutionTraceAfterMaxRetries() throws Exception {
      // -- PREPARE --
      Inject inject = buildPendingInjectWithAgent();
      inject = transitionToExecuting(inject);
      String agentId = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
      InjectExecutionCallback callback = buildCompleteCallback(inject.getId(), agentId);
      // Pretend this callback has already been retried MAX_RETRIES times.
      callback.setRetryCount(MAX_RETRIES);

      // -- EXECUTE --
      List<InjectExecutionCallback> processed =
          batchingInjectStatusService.handleInjectExecutionCallback(List.of(callback));
      batchingInjectStatusService.requeueCallbacks();

      // -- ASSERT --
      // Max retries reached → fall through to persist the trace as a last-ditch effort
      // (the expiration manager will handle any status discrepancies).
      assertEquals(1, processed.size());
      // Retry counter is not re-incremented past MAX_RETRIES.
      assertEquals(MAX_RETRIES, callback.getRetryCount());
      // No publish to the external queue — retries are over.
      verify(mockQueueService, never()).publish(any());
      // But the trace IS written so no information from the implant is lost.
      entityManager.flush();
      entityManager.clear();
      Inject reloaded = injectRepository.findById(inject.getId()).orElseThrow();
      InjectStatus reloadedStatus = reloaded.getStatus().orElseThrow();
      assertFalse(
          reloadedStatus.getTraces().isEmpty(),
          "A trace should be persisted even after max retries");
      assertTrue(
          reloadedStatus.getTraces().stream()
              .anyMatch(t -> ExecutionTraceAction.COMPLETE.equals(t.getAction())),
          "A trace with action COMPLETE should have been persisted after max retries");
    }

    @DisplayName("requeueCallbacks should be a safe no-op when no queue service is wired")
    @Test
    void requeueCallbacksShouldBeSafeNoOpWhenQueueServiceIsNull() throws Exception {
      // -- PREPARE --
      Inject inject = buildPendingInjectWithAgent();
      inject = transitionToExecuting(inject);
      String agentId = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
      InjectExecutionCallback callback = buildCompleteCallback(inject.getId(), agentId);

      // First handle the callback so it lands in the in-memory requeue queue,
      // then remove the queue service to simulate the legacy / unconfigured path.
      batchingInjectStatusService.handleInjectExecutionCallback(List.of(callback));
      batchingInjectStatusService.setInjectTraceQueueService(null);

      // -- EXECUTE + ASSERT --
      assertDoesNotThrow(() -> batchingInjectStatusService.requeueCallbacks());
      verifyNoInteractions(mockQueueService);
    }
  }
}
