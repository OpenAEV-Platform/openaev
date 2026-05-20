package io.openaev.api.threat_arsenal;

import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.StringUtils.DUPLICATE_SUFFIX;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
public class ThreatArsenalApiTest extends IntegrationTest {

  private static final String THREAT_ARSENAL_URI = "/api/threat_arsenals";
  private static Document EXECUTABLE_FILE;

  @Autowired private MockMvc mvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private CollectorRepository collectorRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectorFixture injectorFixture;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void beforeEach() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant();
    injectorContractComposer.reset();
    attackPatternComposer.reset();
    tagComposer.reset();
    domainComposer.reset();
  }

  @BeforeAll
  void beforeAll() {
    collectorComposer.reset();
    EXECUTABLE_FILE = documentRepository.save(PayloadInputFixture.createDefaultExecutableFile());
  }

  @AfterAll
  void afterAll() {
    payloadRepository.deleteAll();
    if (EXECUTABLE_FILE != null) {
      documentRepository.deleteById(EXECUTABLE_FILE.getId());
    }
    collectorRepository.deleteAll();
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Create Threat Arsenal Action")
  class CreateThreatArsenalAction {

    @Test
    @DisplayName(
        "Creating an executable action should succeed create injector contract and payload")
    void given_validExecutableActionInput_should_createPayloadWithInjectorContract()
        throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput input =
          ThreatArsenalInputFixture.createDefaultExecutableAction(
              List.of(domain.getId()), EXECUTABLE_FILE.getId());

      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotNull(response);
      String payloadId = JsonPath.read(response, "$.action_payload.payload_id");
      Payload payload = payloadRepository.findById(payloadId).orElse(null);
      assertNotNull(payload);
      assertEquals(payload.getName(), input.name());
      assertEquals(Payload.PAYLOAD_STATUS.VERIFIED, payload.getStatus());
      assertInstanceOf(Executable.class, payload);
      Executable executable = (Executable) payload;
      assertEquals(executable.getExecutableFile().getId(), EXECUTABLE_FILE.getId());
      InjectorContract contract =
          injectorContractRepository.findInjectorContractByPayload(payload).orElse(null);
      assertNotNull(contract);
      assertEquals(1, contract.getDomains().size());
      assertEquals(domain.getId(), contract.getDomains().stream().findFirst().get().getId());
    }

    @Test
    @DisplayName("Creating a command line action should succeed and create injector contract")
    void given_validCommandLineActionInput_should_createPayloadWithInjectorContract()
        throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput input =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      assertNotNull(response);
      String payloadId = JsonPath.read(response, "$.action_payload.payload_id");
      Payload payload = payloadRepository.findById(payloadId).orElse(null);
      assertNotNull(payload);
      assertEquals(payload.getName(), input.name());
      assertInstanceOf(Command.class, payload);

      Command command = (Command) payload;
      assertEquals(command.getContent(), input.content());
      InjectorContract contract =
          injectorContractRepository.findInjectorContractByPayload(payload).orElse(null);
      assertNotNull(contract);
      assertEquals(1, contract.getDomains().size());
      assertEquals(domain.getId(), contract.getDomains().stream().findFirst().get().getId());
    }

    @Test
    @DisplayName("Creating an action with null arch should fail")
    void given_nullArch_should_returnBadRequest() throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput input =
          new ThreatArsenalActionCreateInput(
              Command.COMMAND_TYPE,
              "Command line payload",
              Payload.PAYLOAD_SOURCE.MANUAL,
              Payload.PAYLOAD_STATUS.VERIFIED,
              new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
              null,
              new InjectExpectation.EXPECTATION_TYPE[] {},
              null,
              "bash",
              "echo hello",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(),
              List.of(),
              null,
              null,
              List.of(domain.getId()));

      mvc.perform(
              post(THREAT_ARSENAL_URI)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Creating an executable action with ALL_ARCHITECTURES arch should fail")
    void given_executableWithAllArchitectures_should_returnBadRequest() throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput input =
          new ThreatArsenalActionCreateInput(
              Executable.EXECUTABLE_TYPE,
              "My Executable Payload",
              Payload.PAYLOAD_SOURCE.MANUAL,
              Payload.PAYLOAD_STATUS.VERIFIED,
              new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
              Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
              new InjectExpectation.EXPECTATION_TYPE[] {},
              null,
              null,
              null,
              EXECUTABLE_FILE.getId(),
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(),
              List.of(),
              null,
              null,
              List.of(domain.getId()));

      mvc.perform(
              post(THREAT_ARSENAL_URI)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Creating an action with output parsers should create payload with output parsers")
    void given_inputWithOutputParsers_should_createPayloadWithOutputParsers() throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput input =
          ThreatArsenalInputFixture.createCommandLineActionWithOutputParser(
              List.of(domain.getId()));

      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertNotNull(response);
      String payloadId = JsonPath.read(response, "$.action_payload.payload_id");
      Payload payload = payloadRepository.findById(payloadId).orElse(null);
      assertNotNull(payload);
      assertEquals(1, payload.getOutputParsers().size());
      ;
    }

    @Test
    @DisplayName("Creating an action with tags should create injectorContract with tags")
    void given_inputWithTags_should_createInjectorContractWithTags() throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      Tag tag = tagComposer.forTag(TagFixture.getTagWithText("New tag")).persist().get();
      ThreatArsenalActionCreateInput input =
          new ThreatArsenalActionCreateInput(
              Command.COMMAND_TYPE,
              "Command line payload",
              Payload.PAYLOAD_SOURCE.MANUAL,
              Payload.PAYLOAD_STATUS.VERIFIED,
              new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
              Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
              new InjectExpectation.EXPECTATION_TYPE[] {},
              "This does something, maybe",
              "bash",
              "echo hello",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(tag.getId()),
              Collections.emptyList(),
              null,
              null,
              List.of(domain.getId()));

      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String payloadId = JsonPath.read(response, "$.action_payload.payload_id");
      assertNotNull(payloadId);
      Payload payload = payloadRepository.findById(payloadId).orElse(null);
      assertNotNull(payload);
      InjectorContract contract =
          injectorContractRepository.findInjectorContractByPayload(payload).orElse(null);
      assertNotNull(contract);
      assertEquals(1, contract.getTags().size());
      assertEquals(tag.getName(), contract.getTags().stream().findFirst().get().getName());
    }

    @Test
    @DisplayName(
        "Creating an action with attack pattern should create injectorContract with Attack Pattern")
    void given_inputWithAttackPattern_should_createInjectorContractWithAttackPattern()
        throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      AttackPattern attackPattern =
          attackPatternComposer
              .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
              .persist()
              .get();
      ThreatArsenalActionCreateInput input =
          new ThreatArsenalActionCreateInput(
              Command.COMMAND_TYPE,
              "Command line payload",
              Payload.PAYLOAD_SOURCE.MANUAL,
              Payload.PAYLOAD_STATUS.VERIFIED,
              new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
              Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
              new InjectExpectation.EXPECTATION_TYPE[] {},
              "This does something, maybe",
              "bash",
              "echo hello",
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              Collections.emptyList(),
              List.of(attackPattern.getId()),
              Collections.emptyList(),
              null,
              List.of(domain.getId()));

      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input))
                      .with(csrf()))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String payloadId = JsonPath.read(response, "$.action_payload.payload_id");
      assertNotNull(payloadId);
      Payload payload = payloadRepository.findById(payloadId).orElse(null);
      assertNotNull(payload);
      InjectorContract contract =
          injectorContractRepository.findInjectorContractByPayload(payload).orElse(null);
      assertNotNull(contract);
      assertEquals(1, contract.getAttackPatterns().size());
      assertEquals(
          attackPattern.getName(),
          contract.getAttackPatterns().stream().findFirst().get().getName());
    }

    @DisplayName("Cleanup executor/command consistency")
    @ParameterizedTest(
        name = "given cleanupExecutor={0} and cleanupCommand={1} should return status {2}")
    @MethodSource("cleanupConsistencyProvider")
    void given_cleanupCombination_should_returnExpectedStatus(
        String cleanupExecutor, String cleanupCommand, int expectedStatus) throws Exception {
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput input =
          ThreatArsenalInputFixture.createCommandLineActionWithCleanup(
              List.of(domain.getId()), cleanupExecutor, cleanupCommand);

      mvc.perform(
              post(THREAT_ARSENAL_URI)
                  .contentType(MediaType.APPLICATION_JSON)
                  .with(csrf())
                  .content(asJsonString(input)))
          .andExpect(status().is(expectedStatus));
    }

    static Stream<Arguments> cleanupConsistencyProvider() {
      return Stream.of(
          Arguments.of(null, null, 200),
          Arguments.of("sh", "cleanup this mess", 200),
          Arguments.of("sh", null, 409),
          Arguments.of(null, "cleanup this mess", 409));
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Search Threat Arsenal Actions")
  class SearchThreatArsenalActions {
    static TagComposer.Composer tag;
    static DomainComposer.Composer domain1;
    static DomainComposer.Composer domain2;
    static String emailInjectorId;

    // Create two payloads with injector contract + two injector contract that are not payload based
    @BeforeEach
    void setUpInjectorContracts() {
      tag = tagComposer.forTag(TagFixture.getTagWithText("tag1"));
      TagComposer.Composer tag2 = tagComposer.forTag(TagFixture.getTagWithText("tag2"));
      domain1 = domainComposer.forDomain(DomainFixture.getRandomDomain());
      domain2 = domainComposer.forDomain(DomainFixture.getRandomDomain());
      Injector emailInjector = injectorFixture.getWellKnownEmailInjector(false);
      emailInjectorId = emailInjector.getId();
      injectorContractComposer
          .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
          .withInjector(emailInjector)
          .withDomain(domain1)
          .persist();
      injectorContractComposer
          .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
          .withInjector(emailInjector)
          .withDomain(domain1)
          .withTag(tag)
          .persist();
      injectorContractComposer
          .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
          .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))
          .withDomain(domain1)
          .withTag(tag2)
          .persist();
      injectorContractComposer
          .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
          .withPayload(payloadComposer.forPayload(PayloadFixture.createDefaultCommand()))
          .withDomain(domain2)
          .persist();
    }

    static Stream<Arguments> searchFilterProvider() {
      return Stream.of(
          Arguments.of("no filter", 4, "no-filter"),
          Arguments.of("domain1 filter", 3, "domain1-filter"),
          Arguments.of("email injector type filter", 2, "injector-email-filter"),
          Arguments.of("with tags ", 1, "action_tags"));
    }

    @DisplayName("Searching threat arsenal actions with filters")
    @ParameterizedTest(name = "given {0} should return {1} results")
    @MethodSource("searchFilterProvider")
    void given_searchFilter_should_returnExpectedResults(
        String filterTypeLabel, int expectedMinCount, String filterType) throws Exception {
      // Arrange
      SearchPaginationInput input = buildSearchInput(filterType);

      // Act
      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/search")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      int totalElements = JsonPath.read(response, "$.totalElements");
      assertTrue(
          totalElements >= expectedMinCount,
          "Expected at least " + expectedMinCount + " results but got " + totalElements);
    }

    static Stream<Arguments> domainCountFilterProvider() {
      return Stream.of(
          Arguments.of("no filter", "no-filter", 3, 1),
          Arguments.of("email injector type filter", "injector-email-filter", 2, 0),
          Arguments.of("with tags", "action_tags", 1, 0));
    }

    @DisplayName("Searching threat arsenal counter by domain")
    @ParameterizedTest(name = "given {0} should return {2} for domain1 and {3} for domain2")
    @MethodSource("domainCountFilterProvider")
    void given_searchFilter_should_returnThreatArsenalCounterByDomain(
        String filterTypeLabel,
        String filterType,
        int expectedCountForDomain1,
        int expectedCountForDomain2)
        throws Exception {
      // Arrange
      SearchPaginationInput input = buildSearchInput(filterType);

      // Act
      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/domain-counts")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — domain1
      List<Integer> countsDomain1 =
          JsonPath.read(response, "$[?(@.domain=='" + domain1.get().getId() + "')].count");
      assertFalse(countsDomain1.isEmpty(), "domain1 should be present in domain counts");
      assertEquals(
          expectedCountForDomain1, countsDomain1.getFirst(), "Unexpected count for domain1");

      // Assert — domain2
      List<Integer> countsDomain2 =
          JsonPath.read(response, "$[?(@.domain=='" + domain2.get().getId() + "')].count");
      if (expectedCountForDomain2 > 0) {
        assertFalse(countsDomain2.isEmpty(), "domain2 should be present in domain counts");
        assertEquals(
            expectedCountForDomain2, countsDomain2.getFirst(), "Unexpected count for domain2");
      } else {
        assertTrue(countsDomain2.isEmpty(), "domain2 should not be present in domain counts");
      }
    }

    private SearchPaginationInput buildSearchInput(String filterType) {
      return switch (filterType) {
        case "injector-email-filter" ->
            PaginationFixture.simpleSearchWithAndOperator(
                "action_injectors", emailInjectorId, Filters.FilterOperator.contains);
        case "domain1-filter" ->
            PaginationFixture.simpleSearchWithAndOperator(
                "action_domains", domain1.get().getId(), Filters.FilterOperator.contains);
        case "action_tags" ->
            PaginationFixture.simpleSearchWithAndOperator(
                "action_tags", tag.get().getId(), Filters.FilterOperator.contains);
        default -> PaginationFixture.getDefault().build();
      };
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Update Threat Arsenal Action")
  class UpdateThreatArsenalAction {

    @Test
    @DisplayName("Updating a command line action should update name, description, and domains")
    void given_validUpdateInput_should_updatePayloadAndInjectorContract() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      Domain newDomain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mvc.perform(
                  post(THREAT_ARSENAL_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(createInput)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String actionId = JsonPath.read(createResponse, "$.injector_contract_id");

      ThreatArsenalActionUpdateInput updateInput =
          new ThreatArsenalActionUpdateInput(
              "Updated name",
              new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows},
              "Updated description",
              "powershell",
              "echo updated",
              Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
              new InjectExpectation.EXPECTATION_TYPE[] {},
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              Collections.emptyList(),
              Collections.emptyList(),
              null,
              null,
              List.of(newDomain.getId()));

      // Act
      String updateResponse =
          mvc.perform(
                  put(THREAT_ARSENAL_URI + "/" + actionId)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(updateInput)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String payloadId = JsonPath.read(updateResponse, "$.action_payload.payload_id");
      Payload payload = payloadRepository.findById(payloadId).orElse(null);
      assertNotNull(payload);
      assertEquals("Updated name", payload.getName());
      assertEquals("Updated description", payload.getDescription());
      assertInstanceOf(Command.class, payload);
      assertEquals("echo updated", ((Command) payload).getContent());

      InjectorContract contract =
          injectorContractRepository.findInjectorContractByPayload(payload).orElse(null);
      assertNotNull(contract);
      assertEquals(1, contract.getDomains().size());
      assertEquals(newDomain.getId(), contract.getDomains().iterator().next().getId());
    }

    @Test
    @DisplayName("Updating a non-payload injector contract should fail with NOT FOUND")
    void given_nonPayloadContract_should_returnNotFound() throws Exception {
      // Arrange — create a non-payload injector contract via composer
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      InjectorContract nonPayloadContract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownEmailInjector(false))
              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
              .persist()
              .get();

      ThreatArsenalActionUpdateInput updateInput =
          new ThreatArsenalActionUpdateInput(
              "Should fail",
              new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
              null,
              "bash",
              "echo fail",
              Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
              new InjectExpectation.EXPECTATION_TYPE[] {},
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              Collections.emptyList(),
              Collections.emptyList(),
              null,
              null,
              List.of(domain.getId()));

      // Act & Assert
      mvc.perform(
              put(THREAT_ARSENAL_URI + "/" + nonPayloadContract.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput)))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Updating an action with null arch should fail with BAD REQUEST")
    void given_nullArch_should_returnBadRequest() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mvc.perform(
                  post(THREAT_ARSENAL_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(createInput)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String actionId = JsonPath.read(createResponse, "$.injector_contract_id");

      ThreatArsenalActionUpdateInput updateInput =
          new ThreatArsenalActionUpdateInput(
              "Updated name",
              new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Linux},
              null,
              "bash",
              "echo hello",
              null,
              new InjectExpectation.EXPECTATION_TYPE[] {},
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              Collections.emptyList(),
              Collections.emptyList(),
              null,
              null,
              List.of(domain.getId()));

      // Act & Assert
      mvc.perform(
              put(THREAT_ARSENAL_URI + "/" + actionId)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput)))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Duplicate Threat Arsenal Action")
  class DuplicateThreatArsenalAction {

    @Test
    @DisplayName("Duplicating a command line action should create a new independent action")
    void given_existingPayloadAction_should_createDuplicate() throws Exception {
      // Arrange
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mvc.perform(
                  post(THREAT_ARSENAL_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(createInput)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String originalActionId = JsonPath.read(createResponse, "$.injector_contract_id");
      String originalPayloadId = JsonPath.read(createResponse, "$.action_payload.payload_id");

      // Flush and clear to force Hibernate to reload from DB with discriminator column set
      entityManager.flush();
      entityManager.clear();

      // Act
      String duplicateResponse =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/" + originalActionId + "/duplicate")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      String duplicateActionId = JsonPath.read(duplicateResponse, "$.injector_contract_id");
      String duplicatePayloadId = JsonPath.read(duplicateResponse, "$.action_payload.payload_id");

      assertNotEquals(originalActionId, duplicateActionId);
      assertNotEquals(originalPayloadId, duplicatePayloadId);

      Payload duplicatePayload = payloadRepository.findById(duplicatePayloadId).orElse(null);
      assertNotNull(duplicatePayload);
      assertEquals(createInput.name() + DUPLICATE_SUFFIX, duplicatePayload.getName());
      assertInstanceOf(Command.class, duplicatePayload);
      assertEquals(createInput.content(), ((Command) duplicatePayload).getContent());

      InjectorContract duplicateContract =
          injectorContractRepository.findInjectorContractByPayload(duplicatePayload).orElse(null);
      assertNotNull(duplicateContract);
      assertEquals(1, duplicateContract.getDomains().size());
      assertEquals(domain.getId(), duplicateContract.getDomains().iterator().next().getId());
    }

    @Test
    @DisplayName("Duplicating a non-payload injector contract should fail with NOT FOUND")
    void given_nonPayloadContract_should_returnNotFound() throws Exception {
      // Arrange
      InjectorContract nonPayloadContract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(injectorFixture.getWellKnownEmailInjector(false))
              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
              .persist()
              .get();

      // Act & Assert
      mvc.perform(
              post(THREAT_ARSENAL_URI + "/" + nonPayloadContract.getId() + "/duplicate")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Updating the original action after duplication should not affect the duplicate")
    void given_duplicatedAction_should_beIndependentFromOriginal() throws Exception {
      // Arrange — create and duplicate
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      Domain newDomain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mvc.perform(
                  post(THREAT_ARSENAL_URI)
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(createInput)))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String originalActionId = JsonPath.read(createResponse, "$.injector_contract_id");

      // Flush and clear to force Hibernate to reload from DB with discriminator column set
      entityManager.flush();
      entityManager.clear();

      String duplicateResponse =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/" + originalActionId + "/duplicate")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String duplicatePayloadId = JsonPath.read(duplicateResponse, "$.action_payload.payload_id");

      // Flush and clear again before update to ensure clean state
      entityManager.flush();
      entityManager.clear();

      // Act — update the original
      ThreatArsenalActionUpdateInput updateInput =
          new ThreatArsenalActionUpdateInput(
              "Original updated",
              new Endpoint.PLATFORM_TYPE[] {Endpoint.PLATFORM_TYPE.Windows},
              "New description",
              "powershell",
              "echo original-updated",
              Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES,
              new InjectExpectation.EXPECTATION_TYPE[] {},
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              Collections.emptyList(),
              Collections.emptyList(),
              null,
              null,
              List.of(newDomain.getId()));

      mvc.perform(
              put(THREAT_ARSENAL_URI + "/" + originalActionId)
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput)))
          .andExpect(status().is2xxSuccessful());

      // Assert — duplicate is unchanged
      Payload duplicatePayload = payloadRepository.findById(duplicatePayloadId).orElse(null);
      assertNotNull(duplicatePayload);
      assertEquals(createInput.name() + DUPLICATE_SUFFIX, duplicatePayload.getName());
      assertEquals(createInput.content(), ((Command) duplicatePayload).getContent());
    }
  }
}
