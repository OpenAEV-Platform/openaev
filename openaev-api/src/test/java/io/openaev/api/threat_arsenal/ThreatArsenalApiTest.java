package io.openaev.api.threat_arsenal;

import static io.openaev.service.UserService.buildAuthenticationToken;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.StringUtils.DUPLICATE_SUFFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionCreateInput;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.collectors.utils.CollectorsUtils;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.CollectorRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.InjectorRepository;
import io.openaev.database.repository.PayloadRepository;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.integration.impl.injectors.openaev.OpenaevInjectorIntegrationFactory;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.fixtures.files.AttackPatternFixture;
import io.openaev.utils.helpers.UserTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
public class ThreatArsenalApiTest extends IntegrationTest {

  private static final String THREAT_ARSENAL_URI = "/api/threat_arsenals";
  private static final String TENANT_THREAT_ARSENAL_URI = "/api/tenants/{tenantId}/threat_arsenals";
  private static Document EXECUTABLE_FILE;

  @Autowired private MockMvc mvc;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private PayloadRepository payloadRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private CollectorRepository collectorRepository;
  @Autowired private InjectorRepository injectorRepository;
  @Autowired private OpenaevInjectorIntegrationFactory openaevInjectorIntegrationFactory;
  @Autowired private CollectorComposer collectorComposer;
  @Autowired private DomainComposer domainComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private PayloadComposer payloadComposer;
  @Autowired private AttackPatternComposer attackPatternComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private DetectionRemediationComposer detectionRemediationComposer;
  @Autowired private CollectorTypeComposer collectorTypeComposer;
  @Autowired private UserTestHelper userTestHelper;

  @MockitoBean private EnterpriseEditionService enterpriseEditionService;

  @BeforeEach
  void beforeEach() throws Exception {
    openaevInjectorIntegrationFactory.registerConnectorForTenant(TenantContext.getCurrentTenant());
    injectorContractComposer.reset();
    attackPatternComposer.reset();
    tagComposer.reset();
    domainComposer.reset();
    detectionRemediationComposer.reset();
    collectorTypeComposer.reset();
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
              new BaseInjectExpectation.EXPECTATION_TYPE[] {},
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
              new BaseInjectExpectation.EXPECTATION_TYPE[] {},
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
              new BaseInjectExpectation.EXPECTATION_TYPE[] {},
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
              new BaseInjectExpectation.EXPECTATION_TYPE[] {},
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
      // Keep this test dataset deterministic by removing seed contracts created at app bootstrap.
      injectorContractRepository.deleteAll();

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

    @Test
    @DisplayName(
        "given action_domains not_eq with AND and two values should return no result for search and domain counts")
    void given_actionDomainsNotEqAndWithTwoValues_should_returnNoResultForSearchAndDomainCounts()
        throws Exception {
      // Arrange
      SearchPaginationInput input =
          buildSearchInputForActionDomainsNotEqAnd(
              List.of(domain1.get().getId(), domain2.get().getId()));

      // Act
      String searchResponse =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/search")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String countResponse =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/domain-counts")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<String> countedDomains = JsonPath.read(countResponse, "$[*].domain");
      assertFalse(
          countedDomains.contains(domain1.get().getId()),
          "domain1 should not be present in domain counts");
      assertFalse(
          countedDomains.contains(domain2.get().getId()),
          "domain2 should not be present in domain counts");

      List<List<String>> resultDomains =
          JsonPath.read(searchResponse, "$.content[*].action_domains_ids");
      assertTrue(
          resultDomains.stream().noneMatch(domains -> domains.contains(domain1.get().getId())),
          "No result should contain domain1");
      assertTrue(
          resultDomains.stream().noneMatch(domains -> domains.contains(domain2.get().getId())),
          "No result should contain domain2");
    }

    @Test
    @DisplayName(
        "given action_domains not_eq with AND and one value should keep contracts from other domains")
    void given_actionDomainsNotEqAndWithOneValue_should_keepOtherDomains() throws Exception {
      // Arrange
      SearchPaginationInput input =
          buildSearchInputForActionDomainsNotEqAnd(List.of(domain1.get().getId()));

      // Act
      String searchResponse =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/search")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      String countResponse =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/domain-counts")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      List<Integer> countsDomain2 =
          JsonPath.read(countResponse, "$[?(@.domain=='" + domain2.get().getId() + "')].count");
      assertFalse(countsDomain2.isEmpty(), "domain2 should be present in domain counts");
      assertEquals(1, countsDomain2.getFirst(), "Unexpected count for domain2");

      List<Integer> countsDomain1 =
          JsonPath.read(countResponse, "$[?(@.domain=='" + domain1.get().getId() + "')].count");
      assertTrue(countsDomain1.isEmpty(), "domain1 should not be present in domain counts");

      List<List<String>> resultDomains =
          JsonPath.read(searchResponse, "$.content[*].action_domains_ids");
      assertTrue(
          resultDomains.stream().noneMatch(domains -> domains.contains(domain1.get().getId())),
          "No result should contain domain1");
    }

    @Test
    @DisplayName("given action_platforms eq filter should match by array membership")
    void given_actionPlatformsEqFilter_should_returnMatchingContracts() throws Exception {
      // Arrange — eq on a text[] column (platforms) used to fail with a 500
      // because lower() cannot be applied to an array expression.
      injectorContractComposer
          .forInjectorContract(
              InjectorContractFixture.createInjectorContractWithPlatforms(
                  new Endpoint.PLATFORM_TYPE[] {
                    Endpoint.PLATFORM_TYPE.Windows, Endpoint.PLATFORM_TYPE.Linux
                  }))
          .withInjector(injectorFixture.getWellKnownEmailInjector(false))
          .withDomain(domain1)
          .persist();

      // Act & Assert — single value (or mode) matches only the Windows contract
      String response =
          searchWith(
              buildSearchInputForActionPlatformsEq(List.of("Windows"), Filters.FilterMode.or));
      assertEquals(
          1, (int) JsonPath.read(response, "$.totalElements"), "Windows should match one contract");

      // and mode requires every value to be present in the array
      response =
          searchWith(
              buildSearchInputForActionPlatformsEq(
                  List.of("Windows", "Linux"), Filters.FilterMode.and));
      assertEquals(
          1,
          (int) JsonPath.read(response, "$.totalElements"),
          "Windows and Linux should match one contract");

      response =
          searchWith(
              buildSearchInputForActionPlatformsEq(
                  List.of("Windows", "MacOS"), Filters.FilterMode.and));
      assertEquals(
          0,
          (int) JsonPath.read(response, "$.totalElements"),
          "Windows and MacOS should match no contract");

      // not_eq is the negation of the membership predicate: contracts without the
      // value — including those with an empty platforms array — are kept. Assert
      // relative to the unfiltered total: the environment may register extra
      // contracts (without platforms) after the setup's deleteAll.
      int unfilteredTotal =
          JsonPath.read(searchWith(PaginationFixture.getDefault().build()), "$.totalElements");
      SearchPaginationInput notEqInput =
          buildSearchInputForActionPlatformsEq(List.of("Windows"), Filters.FilterMode.and);
      notEqInput
          .getFilterGroup()
          .getFilters()
          .getFirst()
          .setOperator(Filters.FilterOperator.not_eq);
      response = searchWith(notEqInput);
      assertEquals(
          unfilteredTotal - 1,
          (int) JsonPath.read(response, "$.totalElements"),
          "not_eq Windows should keep every contract except the Windows one");

      // The facet count endpoints receive the same filter group and must not fail either
      SearchPaginationInput input =
          buildSearchInputForActionPlatformsEq(List.of("Windows"), Filters.FilterMode.and);
      mvc.perform(
              post(THREAT_ARSENAL_URI + "/domain-counts")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isOk());
      mvc.perform(
              post(THREAT_ARSENAL_URI + "/author-counts")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isOk());
    }

    private String searchWith(SearchPaginationInput input) throws Exception {
      return mvc.perform(
              post(THREAT_ARSENAL_URI + "/search")
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(input)))
          .andExpect(status().isOk())
          .andReturn()
          .getResponse()
          .getContentAsString();
    }

    private SearchPaginationInput buildSearchInputForActionPlatformsEq(
        List<String> platforms, Filters.FilterMode mode) {
      Filters.Filter filter = new Filters.Filter();
      filter.setKey("action_platforms");
      filter.setOperator(Filters.FilterOperator.eq);
      filter.setMode(mode);
      filter.setValues(platforms);

      Filters.FilterGroup filterGroup = new Filters.FilterGroup();
      filterGroup.setMode(Filters.FilterMode.and);
      filterGroup.setFilters(new ArrayList<>(List.of(filter)));

      SearchPaginationInput input = PaginationFixture.getDefault().build();
      input.setFilterGroup(filterGroup);
      return input;
    }

    private SearchPaginationInput buildSearchInputForActionDomainsNotEqAnd(List<String> domainIds) {
      Filters.Filter filter = new Filters.Filter();
      filter.setKey("action_domains");
      filter.setOperator(Filters.FilterOperator.not_eq);
      filter.setMode(Filters.FilterMode.and);
      filter.setValues(domainIds);

      Filters.FilterGroup filterGroup = new Filters.FilterGroup();
      filterGroup.setMode(Filters.FilterMode.and);
      filterGroup.setFilters(new ArrayList<>(List.of(filter)));

      SearchPaginationInput input = PaginationFixture.getDefault().build();
      input.setFilterGroup(filterGroup);
      return input;
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

    @Test
    @DisplayName("Searching non-tabletop threat arsenal should exclude email injector contracts")
    void given_nonTabletopSearch_should_excludeEmailInjectorContracts() throws Exception {
      // Arrange
      SearchPaginationInput input = PaginationFixture.getDefault().build();

      // Act
      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/search/non-tabletop")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — email contracts (tabletop) must not be returned
      int totalElements = JsonPath.read(response, "$.totalElements");
      // We created 2 payload-based (non-tabletop) contracts + possibly pre-existing non-tabletop
      assertTrue(
          totalElements >= 2, "Expected at least 2 non-tabletop results but got " + totalElements);

      // Verify no email injector ID appears in the results
      List<List<String>> injectorIds = JsonPath.read(response, "$.content[*].action_injectors");
      for (List<String> ids : injectorIds) {
        assertFalse(
            ids.contains(emailInjectorId),
            "Non-tabletop search should not include email injector contracts");
      }
    }

    @Test
    @DisplayName("Searching non-tabletop threat arsenal should exclude all tabletop injector types")
    void given_nonTabletopSearch_should_excludeAllTabletopInjectorTypes() throws Exception {
      // Arrange — create contracts for each tabletop type (SMS, challenge, channel)
      Injector smsInjector =
          injectorRepository.save(
              InjectorFixture.createInjector(
                  UUID.randomUUID().toString(), "OVH SMS", "openaev_ovh_sms"));
      Injector challengeInjector =
          injectorRepository.save(
              InjectorFixture.createInjector(
                  UUID.randomUUID().toString(), "Challenge", "openaev_challenge"));
      Injector channelInjector =
          injectorRepository.save(
              InjectorFixture.createInjector(
                  UUID.randomUUID().toString(), "Channel", "openaev_channel"));

      injectorContractComposer
          .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
          .withInjector(smsInjector)
          .withDomain(domain1)
          .persist();
      injectorContractComposer
          .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
          .withInjector(challengeInjector)
          .withDomain(domain1)
          .persist();
      injectorContractComposer
          .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
          .withInjector(channelInjector)
          .withDomain(domain1)
          .persist();

      SearchPaginationInput input = PaginationFixture.getDefault().build();

      // Act
      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/search/non-tabletop")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — none of the tabletop injector IDs should appear
      List<List<String>> injectorIds = JsonPath.read(response, "$.content[*].action_injectors");
      List<String> tabletopInjectorIds =
          List.of(
              emailInjectorId,
              smsInjector.getId(),
              challengeInjector.getId(),
              channelInjector.getId());
      for (List<String> ids : injectorIds) {
        for (String tabletopId : tabletopInjectorIds) {
          assertFalse(
              ids.contains(tabletopId),
              "Non-tabletop search should not include tabletop injector " + tabletopId);
        }
      }
    }

    @Test
    @DisplayName(
        "Searching non-tabletop threat arsenal with domain filter should return only matching non-tabletop contracts")
    void given_nonTabletopSearchWithDomainFilter_should_returnFilteredResults() throws Exception {
      // Arrange
      SearchPaginationInput input =
          PaginationFixture.simpleSearchWithAndOperator(
              "action_domains", domain2.get().getId(), Filters.FilterOperator.contains);

      // Act
      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/search/non-tabletop")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — only domain2 payload-based (non-tabletop) contract should be returned
      int totalElements = JsonPath.read(response, "$.totalElements");
      assertTrue(
          totalElements >= 1,
          "Expected at least 1 non-tabletop result for domain2 but got " + totalElements);

      // Verify no email injector ID appears in the results
      List<List<String>> injectorIds = JsonPath.read(response, "$.content[*].action_injectors");
      for (List<String> ids : injectorIds) {
        assertFalse(
            ids.contains(emailInjectorId),
            "Non-tabletop search with domain filter should not include email injector contracts");
      }
    }

    @Test
    @DisplayName(
        "Searching non-tabletop threat arsenal with tag filter should return only matching non-tabletop contracts")
    void given_nonTabletopSearchWithTagFilter_should_returnFilteredResults() throws Exception {
      // Arrange
      SearchPaginationInput input =
          PaginationFixture.simpleSearchWithAndOperator(
              "action_tags", tag.get().getId(), Filters.FilterOperator.contains);

      // Act
      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/search/non-tabletop")
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(input)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert — tag1 is only on an email injector contract, so non-tabletop should return 0
      int totalElements = JsonPath.read(response, "$.totalElements");
      assertEquals(
          0, totalElements, "Tag filter for email-only tag should return 0 non-tabletop results");
    }
  }

  @Nested
  @DisplayName("Search Threat Arsenal Actions with different user access right")
  class SearchThreatArsenalActionsWithDifferentUsersAccessRight {
    private int preExistingContractsCount;
    private List<InjectorContract> injectorContractsCreated = new ArrayList<>();

    private InjectorContract createStaticInjectorContract() {
      InjectorContractComposer.Composer icComposer =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()).persist())
              .withInjector(injectorFixture.getWellKnownOaevImplantInjector());
      return icComposer.persist().get();
    }

    @BeforeEach
    void setUp() {
      injectorContractsCreated.clear();
      preExistingContractsCount = (int) injectorContractRepository.count();
      injectorContractsCreated.add(createStaticInjectorContract());
      injectorContractsCreated.add(createStaticInjectorContract());
      injectorContractsCreated.add(createStaticInjectorContract());
    }

    private static Stream<Arguments> userTestCases() {
      return Stream.of(
          Arguments.of(
              "User with no groups",
              UserTestHelper.UserType.NO_GROUPS,
              0, // number of granted threat Arsenal action
              false // shouldSeeAllContracts
              ),
          Arguments.of(
              "Admin user",
              UserTestHelper.UserType.ADMIN,
              0, // number of granted threat Arsenal action
              true // Admin sees all
              ),
          Arguments.of(
              "User with BYPASS capability",
              UserTestHelper.UserType.WITH_BYPASS,
              0, // number of granted threat Arsenal action
              true // BYPASS users should see all
              ),
          Arguments.of(
              "User with ACCESS_THREAT_ARSENALS capability",
              UserTestHelper.UserType.WITH_ACCESS_THREAT_ARSENALS,
              0, // number of granted threat Arsenal action
              true // ACCESS_THREAT_ARSENALS users should see all actions
              ),
          Arguments.of(
              "User with OBSERVER grant on threat arsenal",
              UserTestHelper.UserType.NO_GROUPS,
              2, // number of granted threat Arsenal action
              false // users should see granted actions only
              ));
    }

    @ParameterizedTest(name = "{index} - {0}")
    @MethodSource("userTestCases")
    @DisplayName("GET /threat-arsenals - Test access control for different user types")
    void given_user_should_searchThreatArsenal(
        String testCase,
        UserTestHelper.UserType userType,
        int grantedActionNumber,
        boolean shouldSeeAllContracts)
        throws Exception {

      List<String> grantedResourceIds = new ArrayList<>();
      for (int i = 0; i < grantedActionNumber; i++) {
        grantedResourceIds.add(injectorContractsCreated.get(i).getId());
      }
      // Create test user based on type
      User testUser = userTestHelper.createTestUser(userType, grantedResourceIds).persist().get();
      Authentication auth = buildAuthenticationToken(testUser);

      String response =
          mvc.perform(
                  post(THREAT_ARSENAL_URI + "/search")
                      .with(authentication(auth))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(asJsonString(PaginationFixture.getDefault().build())))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Verify the response based on user permissions
      int totalElements = JsonPath.read(response, "$.totalElements");
      if (shouldSeeAllContracts) {
        // Admin, BYPASS, ACCESS_THREAT_ARSENALS users see everything
        assertEquals(preExistingContractsCount + 3, totalElements);
      } else {
        // User with no groups only sees contracts granted
        assertEquals(grantedActionNumber, totalElements);
      }
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
              new BaseInjectExpectation.EXPECTATION_TYPE[] {},
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
    @DisplayName(
        "Updating a non-payload injector contract should only work with tags domains and TTP")
    void given_nonPayloadContract_should_onlyUpdateDomainTagAndTTP() throws Exception {
      // Arrange — create a non-payload injector contract via composer
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      Tag tag = tagComposer.forTag(TagFixture.getTagWithText("New tag")).persist().get();
      AttackPattern attackPattern =
          attackPatternComposer
              .forAttackPattern(AttackPatternFixture.createDefaultAttackPattern())
              .persist()
              .get();
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
              new BaseInjectExpectation.EXPECTATION_TYPE[] {},
              null,
              null,
              null,
              null,
              null,
              null,
              null,
              List.of(tag.getId()),
              List.of(attackPattern.getId()),
              null,
              null,
              List.of(domain.getId()));

      // Act & Assert
      mvc.perform(
              put(THREAT_ARSENAL_URI + "/" + nonPayloadContract.getId())
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(asJsonString(updateInput)))
          .andExpect(status().is2xxSuccessful());

      // Assert
      InjectorContract injectorContractUpdated =
          injectorContractRepository.findById(nonPayloadContract.getId()).orElse(null);
      assertNotNull(injectorContractUpdated);
      assertEquals(
          nonPayloadContract.getLabels(),
          injectorContractUpdated.getLabels(),
          "Labels should remain unchanged for non-payload contracts");
      assertEquals(1, injectorContractUpdated.getDomains().size());
      assertEquals(domain.getId(), injectorContractUpdated.getDomains().iterator().next().getId());
      assertEquals(1, injectorContractUpdated.getAttackPatterns().size());
      assertEquals(
          attackPattern.getId(),
          injectorContractUpdated.getAttackPatterns().iterator().next().getId());
      assertEquals(1, injectorContractUpdated.getTags().size());
      assertEquals(tag.getId(), injectorContractUpdated.getTags().iterator().next().getId());
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
              new BaseInjectExpectation.EXPECTATION_TYPE[] {},
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
              new BaseInjectExpectation.EXPECTATION_TYPE[] {},
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

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Delete Threat Arsenal Action")
  class DeleteThreatArsenalAction {
    @Test
    @DisplayName("Deleting a non-payload-based action should fail with BAD REQUEST")
    void given_nonPayloadContract_should_returnFailed() throws Exception {
      // Arrange
      Injector emailInjector = injectorFixture.getWellKnownEmailInjector(false);
      InjectorContract nonPayloadContract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(emailInjector)
              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
              .persist()
              .get();

      // Act & Assert
      mvc.perform(
              delete(tenantUri(TENANT_THREAT_ARSENAL_URI + "/" + nonPayloadContract.getId()))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound())
          .andExpect(
              content()
                  .string(
                      containsString("Only payload-based or orphaned actions can be deleted.")));
    }

    @Test
    @DisplayName("Payload-based deletion should complete successfully.")
    void given_existingPayloadAction_should_delete() throws Exception {
      // Arrange — create and delete
      Domain domain = domainComposer.forDomain(DomainFixture.getRandomDomain()).persist().get();
      ThreatArsenalActionCreateInput createInput =
          ThreatArsenalInputFixture.createDefaultCommandLineAction(List.of(domain.getId()));

      String createResponse =
          mvc.perform(
                  post(tenantUri(TENANT_THREAT_ARSENAL_URI))
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

      mvc.perform(
              delete(tenantUri(TENANT_THREAT_ARSENAL_URI + "/" + originalActionId))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().is2xxSuccessful());
    }
  }

  @Nested
  @WithMockUser(isAdmin = true)
  @DisplayName("Get collector used in action remediation")
  class GetCollectorForActionRemediation {

    @Test
    @DisplayName("Getting collectors for a non-payload-based action should fail")
    void given_nonPayloadContract_should_returnFailed() throws Exception {
      // Arrange
      Injector emailInjector = injectorFixture.getWellKnownEmailInjector(false);
      InjectorContract nonPayloadContract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(emailInjector)
              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
              .persist()
              .get();

      // Act & Assert
      mvc.perform(
              get(tenantUri(
                      TENANT_THREAT_ARSENAL_URI + "/" + nonPayloadContract.getId() + "/collectors"))
                  .with(csrf())
                  .contentType(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound())
          .andExpect(
              content()
                  .string(
                      containsString(
                          "Only payload-based injector contracts can provide collectors for action remediation.")));
    }

    @Test
    @DisplayName(
        "Getting collectors for a payload-based action should return the associated collectors")
    void given_nonPayloadContract_should_returnCollectorsForActionRemediation() throws Exception {
      // Arrange — create and delete
      Injector oaevImplantInjector = injectorFixture.getWellKnownOaevImplantInjector();
      Collector crowdstrikeCollector =
          collectorComposer
              .forCollector(CollectorFixture.createDefaultCollector(CollectorsUtils.CROWDSTRIKE))
              .persist()
              .get();
      Collector defenderCollector =
          collectorComposer
              .forCollector(
                  CollectorFixture.createDefaultCollector(CollectorsUtils.MICROSOFT_DEFENDER))
              .persist()
              .get();
      InjectorContract nonPayloadContract =
          injectorContractComposer
              .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
              .withInjector(oaevImplantInjector)
              .withDomain(domainComposer.forDomain(DomainFixture.getRandomDomain()))
              .withPayload(
                  payloadComposer
                      .forPayload(PayloadFixture.createDefaultCommand())
                      .withDetectionRemediation(
                          detectionRemediationComposer
                              .forDetectionRemediation(
                                  DetectionRemediationFixture.createDefaultDetectionRemediation())
                              .withCollectorType(
                                  collectorTypeComposer.forCollectorType(
                                      CollectorTypeFixture.createCollectorType(
                                          crowdstrikeCollector.getType()))))
                      .withDetectionRemediation(
                          detectionRemediationComposer
                              .forDetectionRemediation(
                                  DetectionRemediationFixture.createDefaultDetectionRemediation())
                              .withCollectorType(
                                  collectorTypeComposer.forCollectorType(
                                      CollectorTypeFixture.createCollectorType(
                                          defenderCollector.getType())))))
              .persist()
              .get();

      String response =
          mvc.perform(
                  get(tenantUri(
                          TENANT_THREAT_ARSENAL_URI
                              + "/"
                              + nonPayloadContract.getId()
                              + "/collectors"))
                      .with(csrf())
                      .contentType(MediaType.APPLICATION_JSON))
              .andExpect(status().is2xxSuccessful())
              .andExpect(jsonPath("$.length()").value(2))
              .andReturn()
              .getResponse()
              .getContentAsString();

      List<String> collectorTypes = JsonPath.read(response, "$[*].collector_type");
      assertThat(collectorTypes)
          .containsExactlyInAnyOrder(
              CollectorsUtils.CROWDSTRIKE, CollectorsUtils.MICROSOFT_DEFENDER);
    }
  }
}
