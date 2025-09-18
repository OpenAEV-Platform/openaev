package io.openbas.rest.inject;

import static io.openbas.config.SessionHelper.currentUser;
import static io.openbas.database.model.ExerciseStatus.RUNNING;
import static io.openbas.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_END_DATE;
import static io.openbas.database.model.InjectExpectationSignature.EXPECTATION_SIGNATURE_TYPE_START_DATE;
import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR;
import static io.openbas.database.model.InjectorContract.CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY;
import static io.openbas.injectors.email.EmailContract.EMAIL_DEFAULT;
import static io.openbas.rest.exercise.ExerciseApi.EXERCISE_URI;
import static io.openbas.rest.inject.InjectApi.INJECT_URI;
import static io.openbas.utils.JsonUtils.asJsonString;
import static io.openbas.utils.fixtures.InjectFixture.getInjectForEmailContract;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.repository.*;
import io.openbas.execution.ExecutableInject;
import io.openbas.executors.Executor;
import io.openbas.injector_contract.ContractTargetedProperty;
import io.openbas.injector_contract.fields.ContractFieldType;
import io.openbas.rest.atomic_testing.form.ExecutionTraceOutput;
import io.openbas.rest.atomic_testing.form.InjectStatusOutput;
import io.openbas.rest.document.DocumentService;
import io.openbas.rest.exception.BadRequestException;
import io.openbas.rest.exercise.service.ExerciseService;
import io.openbas.rest.inject.form.*;
import io.openbas.rest.inject.service.InjectStatusService;
import io.openbas.service.ScenarioService;
import io.openbas.utils.TargetType;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import io.openbas.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.transaction.Transactional;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.*;
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
import org.springframework.util.ResourceUtils;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(PER_CLASS)
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
  @Autowired private ScenarioService scenarioService;
  @Autowired private ExerciseService exerciseService;
  @SpyBean private InjectStatusService injectStatusService;
  @SpyBean private DocumentService documentService;

  @Autowired private AgentComposer agentComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private DocumentComposer documentComposer;
  @Autowired private InjectStatusComposer injectStatusComposer;
  @Autowired private ExecutionTraceComposer executionTraceComposer;
  @Autowired private TeamComposer teamComposer;
  @Autowired private UserComposer userComposer;

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
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private FindingRepository findingRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private UserRepository userRepository;
  @Resource private ObjectMapper objectMapper;
  @MockBean private JavaMailSender javaMailSender;

  @BeforeAll
  void beforeAll() {
    Scenario scenario = new Scenario();
    scenario.setName("Scenario name");
    scenario.setFrom("test@test.com");
    scenario.setReplyTos(List.of("test@test.com"));
    SCENARIO = scenarioService.createScenario(scenario);

    Exercise exercise = new Exercise();
    exercise.setName("Exercise name");
    exercise.setStart(Instant.now());
    exercise.setFrom("test@test.com");
    exercise.setReplyTos(List.of("test@test.com"));
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
  }

  // BULK DELETE
  @DisplayName("Delete list of injects for scenario")
  @Test
  @Order(6)
  @WithMockUser(isAdmin = true)
  void deleteInjectsForScenarioTest() throws Exception {
    // -- PREPARE --
    Inject injectForScenario1 = new Inject();
    injectForScenario1.setTitle("Inject for scenario 1");
    injectForScenario1.setCreatedAt(Instant.now());
    injectForScenario1.setUpdatedAt(Instant.now());
    injectForScenario1.setDependsDuration(5L);
    injectForScenario1.setInjectorContract(
        injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow());
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
    Inject inject =
        injectInput.toInject(injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow());
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
    InjectorContract injectorContract =
        this.injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow();
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
    InjectorContract injectorContract =
        this.injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow();
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
    InjectorContract injectorContract =
        this.injectorContractRepository.findById(EMAIL_DEFAULT).orElseThrow();
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
  @Order(8)
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
      Payload payloadSaved = payloadRepository.save(payloadCommand);

      Injector injector = injectorRepository.findByType("openbas_implant").orElseThrow();
      InjectorContract injectorContract =
          InjectorContractFixture.createPayloadInjectorContract(injector, payloadSaved);
      InjectorContract injectorContractSaved = injectorContractRepository.save(injectorContract);

      String argValue = "Hello world";
      Map<String, Object> payloadArguments = new HashMap<>();
      payloadArguments.put("arg_value", argValue);
      Inject inject =
          InjectFixture.createInjectCommandPayload(injectorContractSaved, payloadArguments);

      Inject injectSaved = injectRepository.save(inject);
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
      Payload payloadSaved = payloadRepository.save(payloadCommand);

      Injector injector = injectorRepository.findByType("openbas_implant").orElseThrow();
      InjectorContract injectorContract =
          InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
              injector, payloadSaved, List.of());
      InjectorContractFixture.addTargetedAssetFields(
          injectorContract, "asset-separate-by-space", ContractTargetedProperty.hostname);
      InjectorContractFixture.addTargetedAssetFields(
          injectorContract, "asset-separate-by-comma", ContractTargetedProperty.seen_ip);
      InjectorContract injectorContractSaved = injectorContractRepository.save(injectorContract);

      // Create two endpoints
      Endpoint endpoint1 = EndpointFixture.createEndpoint();
      endpoint1.setHostname("endpoint1-hostname");
      String[] endpoint1IP = {"233.152.15.205"};
      endpoint1.setIps(endpoint1IP);
      endpoint1.setSeenIp("seen-ip-endpoint1");
      Endpoint endpoint1Saved = endpointRepository.save(endpoint1);

      Endpoint endpoint2 = EndpointFixture.createEndpoint();
      endpoint2.setHostname("endpoint2-hostname");
      String[] endpoint2IP = {"253.110.186.71"};
      endpoint2.setIps(endpoint2IP);
      endpoint2.setSeenIp("seen-ip-endpoint2");
      Endpoint endpoint2Saved = endpointRepository.save(endpoint2);

      Map<String, Object> payloadArguments = new HashMap<>();
      payloadArguments.put(
          "asset-separate-by-space", List.of(endpoint1Saved.getId(), endpoint2Saved.getId()));
      payloadArguments.put(
          "asset-separate-by-comma", List.of(endpoint1Saved.getId(), endpoint2Saved.getId()));
      payloadArguments.put(
          CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY + "-asset-separate-by-space", "local_ip");
      payloadArguments.put(
          CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR + "-asset-separate-by-space", " ");

      Inject inject =
          InjectFixture.createInjectCommandPayload(injectorContractSaved, payloadArguments);

      Inject injectSaved = injectRepository.save(inject);
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
      Command payloadCommand =
          PayloadFixture.createCommand(
              "bash", "echo command name #{arg_value}", List.of(), "echo cleanup cmd");
      Payload payloadSaved = payloadRepository.save(payloadCommand);

      Injector injector = injectorRepository.findByType("openbas_implant").orElseThrow();
      InjectorContract injectorContract =
          InjectorContractFixture.createPayloadInjectorContract(injector, payloadSaved);
      InjectorContract injectorContractSaved = injectorContractRepository.save(injectorContract);

      Inject inject =
          InjectFixture.createInjectCommandPayload(injectorContractSaved, new HashMap<>());
      Inject injectSaved = injectRepository.save(inject);

      // Prepare injectExpectation on specific agent
      Endpoint endpoint = EndpointFixture.createEndpoint();
      endpoint.setSeenIp("seen-ip-endpoint");
      Endpoint endpointSaved = endpointRepository.save(endpoint);
      Agent agent = AgentFixture.createDefaultAgentService();
      agent.setAsset(endpointSaved);
      Agent agentSaved = agentRepository.save(agent);
      InjectExpectation detectionExpectation =
          InjectExpectationFixture.createDetectionInjectExpectation(injectSaved, agentSaved);
      injectExpectationRepository.save(detectionExpectation);

      doNothing()
          .when(injectStatusService)
          .addStartImplantExecutionTraceByInject(any(), any(), any(), any());

      // -- EXECUTE --
      mvc.perform(
              get(INJECT_URI
                      + "/"
                      + injectSaved.getId()
                      + "/"
                      + agentSaved.getId()
                      + "/executable-payload")
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());

      // -- ASSERT --
      List<InjectExpectation> injectExpectationSaved =
          injectExpectationRepository.findAllByInjectAndAgent(injectSaved.getId(), agent.getId());
      assertEquals(1, injectExpectationSaved.size());
      assertEquals(
          1,
          injectExpectationSaved.getFirst().getSignatures().stream()
              .filter(s -> EXPECTATION_SIGNATURE_TYPE_START_DATE.equals(s.getType()))
              .count());
    }

    @DisplayName("Get obfuscate command")
    @Test
    void getExecutableObfuscatePayloadInject() throws Exception {
      // -- PREPARE --
      Command payloadCommand =
          PayloadFixture.createCommand("psh", "echo Hello World", List.of(), "echo cleanup cmd");
      Payload payloadSaved = payloadRepository.save(payloadCommand);

      Injector injector = injectorRepository.findByType("openbas_implant").orElseThrow();
      InjectorContract injectorContract =
          InjectorContractFixture.createPayloadInjectorContractWithObfuscator(
              injector, payloadSaved);
      InjectorContract injectorContractSaved = injectorContractRepository.save(injectorContract);

      Map<String, Object> payloadArguments = new HashMap<>();
      payloadArguments.put("obfuscator", "base64");
      Inject inject =
          InjectFixture.createInjectCommandPayload(injectorContractSaved, payloadArguments);

      Inject injectSaved = injectRepository.save(inject);
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
  }

  @Nested
  @Transactional
  @WithMockUser(isAdmin = true)
  @DisplayName("Inject Execution Callback Handling (simulating a request from an implant)")
  class handleInjectExecutionCallback {

    private Inject getPendingInjectWithAssets() {
      return injectComposer
          .forInject(InjectFixture.getDefaultInject())
          .withEndpoint(
              endpointComposer
                  .forEndpoint(EndpointFixture.createEndpoint())
                  .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
                  .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentSession())))
          .withInjectStatus(
              injectStatusComposer.forInjectStatus(InjectStatusFixture.createPendingInjectStatus()))
          .persist()
          .get();
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

    @Nested
    @DisplayName("Action Handling:")
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

        // -- EXECUTE --
        String agentId = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
        performCallbackRequest(agentId, inject.getId(), input);

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
        injectExpectationRepository.save(detectionExpectation);

        InjectExecutionInput input = new InjectExecutionInput();
        input.setMessage("Complete log received");
        input.setAction(InjectExecutionAction.complete);
        input.setStatus("INFO");
        input.setDuration(1000);

        performCallbackRequest(agent.getId(), inject.getId(), input);
        List<InjectExpectation> injectExpectationSaved =
            injectExpectationRepository.findAllByInjectAndAgent(inject.getId(), agent.getId());
        assertEquals(1, injectExpectationSaved.size());
        List<InjectExpectationSignature> endDatesignatures =
            injectExpectationSaved.getFirst().getSignatures().stream()
                .filter(s -> EXPECTATION_SIGNATURE_TYPE_END_DATE.equals(s.getType()))
                .toList();
        assertEquals(1, endDatesignatures.size());
      }
    }

    @Nested
    @DisplayName("Agent Status Computation")
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
    @Transactional
    @DisplayName("Finding Handling")
    class FindingHandlingTest {
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
        Command payloadCommand = PayloadFixture.createCommand("bash", "command", null, null);
        payloadCommand.setOutputParsers(Set.of(outputParser));
        Payload payloadSaved = payloadRepository.save(payloadCommand);

        // Create injectorContract with targeted asset field
        Injector injector = injectorRepository.findByType("openbas_implant").orElseThrow();
        InjectorContract injectorContract =
            InjectorContractFixture.createPayloadInjectorContractWithFieldsContent(
                injector, payloadSaved, List.of());
        InjectorContractFixture.addTargetedAssetFields(
            injectorContract, "asset-key", ContractTargetedProperty.seen_ip);
        InjectorContract injectorContractSaved = injectorContractRepository.save(injectorContract);
        inject.setInjectorContract(injectorContractSaved);

        // Set targeted inject on inject
        Endpoint endpoint = EndpointFixture.createEndpoint();
        endpoint.setSeenIp("seen-ip-endpoint");
        Endpoint endpointSaved = endpointRepository.save(endpoint);
        ObjectNode content = objectMapper.createObjectNode();
        content.set(
            "asset-key", objectMapper.convertValue(List.of(endpointSaved.getId()), JsonNode.class));
        inject.setContent(content);
        injectRepository.save(inject);

        // -- EXECUTE --
        String agentId = ((Endpoint) inject.getAssets().getFirst()).getAgents().getFirst().getId();
        performCallbackRequest(agentId, inject.getId(), input);

        List<Finding> findings = findingRepository.findAllByInjectId(inject.getId());
        assertEquals(2, findings.size());
        assertEquals(1, findings.getFirst().getAssets().size());
        assertEquals(endpointSaved.getId(), findings.getFirst().getAssets().getFirst().getId());
        assertEquals(1, findings.getLast().getAssets().size());
        assertEquals(endpointSaved.getId(), findings.getLast().getAssets().getFirst().getId());
      }
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Fetch execution traces for inject/atomic overview")
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
  }
}
