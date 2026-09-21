package io.openaev.rest.challenge;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.injectors.challenge.ChallengeContract.CHALLENGE_PUBLISH;
import static io.openaev.rest.challenge.ChallengeApi.TENANT_CHALLENGE_URI;
import static io.openaev.utils.fixtures.ChallengeFixture.createDefaultChallenge;
import static io.openaev.utils.fixtures.DocumentFixture.getDocumentJpeg;
import static io.openaev.utils.fixtures.InjectFixture.createDefaultInjectChallenge;
import static io.openaev.utils.fixtures.ScenarioFixture.createDefaultCrisisScenario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.IntegrationTest;
import io.openaev.database.model.Capability;
import io.openaev.database.model.Challenge;
import io.openaev.database.model.Document;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.ChallengeRepository;
import io.openaev.database.repository.DocumentRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.integration.impl.injectors.challenge.ChallengeInjectorIntegrationFactory;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.mockUser.WithMockUser;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * The challenge read endpoints that return raw {@code Challenge} entities load {@code
 * challenge_documents} inside the scoped transaction. That load is done in bulk: the statements
 * reading the association do not grow with the number of challenges, and the payload still carries
 * each challenge with its own documents.
 *
 * <p>Statements are counted from the SQL Hibernate actually logs, filtered on the link table,
 * because the lazy flags and tags of a challenge legitimately issue their own statements at
 * serialization. Every request is preceded by a flush and clear so the entities are loaded from the
 * database and not served initialized from the persistence context of the test.
 */
@Transactional
@TestPropertySource(properties = "openaev.tenant.active-tables=challenges,documents")
@WithMockUser(isAdmin = true)
@DisplayName("Challenge read endpoints load their documents in bulk")
class ChallengeDocumentsBulkLoadTest extends IntegrationTest {

  private static final String DOCUMENTS_LINK_TABLE = "challenges_documents";
  private static final String OBSERVER_SCENARIO_CHALLENGES_URI =
      TENANT_PREFIX + "/observer/scenarios/{scenarioId}/challenges";

  @Autowired private MockMvc mvc;
  @Autowired private ScenarioService scenarioService;
  @Autowired private InjectRepository injectRepository;
  @Autowired private ChallengeRepository challengeRepository;
  @Autowired private DocumentRepository documentRepository;
  @Autowired private InjectorContractRepository injectorContractRepository;
  @Autowired private ChallengeInjectorIntegrationFactory challengeInjectorIntegrationFactory;
  @Autowired private TenantIsolationTestHelper tenantHelper;
  @Resource private ObjectMapper objectMapper;

  private Scenario scenario;
  private Tenant tenant;

  private Logger sqlLogger;
  private Level previousLevel;
  private boolean previousAdditive;
  private ListAppender<ILoggingEvent> sqlAppender;

  @BeforeEach
  void setUp() throws Exception {
    scenario = scenarioService.createScenario(createDefaultCrisisScenario());
    tenant = scenario.getTenant();
    tenantHelper.grantCapabilitiesInTenant(
        tenant.getId(), Set.of(Capability.ACCESS_ASSESSMENT, Capability.ACCESS_CHALLENGES));
    challengeInjectorIntegrationFactory.registerConnectorForTenant(tenant.getId());

    sqlLogger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
    previousLevel = sqlLogger.getLevel();
    previousAdditive = sqlLogger.isAdditive();
    sqlAppender = new ListAppender<>();
    sqlAppender.start();
  }

  @AfterEach
  void tearDown() {
    stopCapture();
  }

  @Nested
  @DisplayName("Statements reading challenges_documents")
  class DocumentStatements {

    @Test
    @DisplayName(
        "given more challenges when listed then the documents load issues no more statements and"
            + " each challenge keeps its own documents")
    void given_moreChallenges_should_notIssueMoreDocumentStatementsOnList() throws Exception {
      // -- Arrange --
      List<Challenge> challenges = new ArrayList<>();
      challenges.add(saveChallengeWithTwoDocuments());
      flushAndClear();

      // -- Act --
      startCapture();
      perform(get(TENANT_CHALLENGE_URI, tenant.getId()));
      long documentLoadsForOne = stopCaptureAndCount();
      for (int i = 0; i < 4; i++) {
        challenges.add(saveChallengeWithTwoDocuments());
      }
      flushAndClear();
      startCapture();
      String response = perform(get(TENANT_CHALLENGE_URI, tenant.getId()));
      long documentLoadsForFive = stopCaptureAndCount();

      // -- Assert --
      assertThat(documentLoadsForOne).as("the capture sees the documents load").isPositive();
      assertThat(documentLoadsForFive)
          .as("statements reading challenges_documents must not grow with the challenges")
          .isEqualTo(documentLoadsForOne);
      for (Challenge challenge : challenges) {
        assertThat(documentIdsOf(response, "$", challenge))
            .containsExactlyInAnyOrderElementsOf(documentIds(challenge));
      }
    }

    @Test
    @DisplayName(
        "given more challenges published by a scenario when its challenges are expanded then the"
            + " documents load issues no more statements and each challenge keeps its own"
            + " documents")
    void given_moreChallenges_should_notIssueMoreDocumentStatementsOnScenarioChallenges()
        throws Exception {
      // -- Arrange --
      List<Challenge> challenges = new ArrayList<>();
      challenges.add(saveChallengeWithTwoDocuments());
      publishInScenario(challenges.get(0));
      flushAndClear();

      // -- Act --
      startCapture();
      perform(get(OBSERVER_SCENARIO_CHALLENGES_URI, tenant.getId(), scenario.getId()));
      long documentLoadsForOne = stopCaptureAndCount();
      for (int i = 0; i < 4; i++) {
        Challenge challenge = saveChallengeWithTwoDocuments();
        publishInScenario(challenge);
        challenges.add(challenge);
      }
      flushAndClear();
      startCapture();
      String response =
          perform(get(OBSERVER_SCENARIO_CHALLENGES_URI, tenant.getId(), scenario.getId()));
      long documentLoadsForFive = stopCaptureAndCount();

      // -- Assert --
      assertThat(documentLoadsForOne).as("the capture sees the documents load").isPositive();
      assertThat(documentLoadsForFive)
          .as("statements reading challenges_documents must not grow with the challenges")
          .isEqualTo(documentLoadsForOne);
      List<String> expanded =
          JsonPath.read(response, "$.scenario_challenges[*].challenge_detail.challenge_id");
      assertThat(expanded)
          .containsExactlyInAnyOrderElementsOf(challenges.stream().map(Challenge::getId).toList());
      for (Challenge challenge : challenges) {
        assertThat(documentIdsOf(response, "$.scenario_challenges[*].challenge_detail", challenge))
            .containsExactlyInAnyOrderElementsOf(documentIds(challenge));
      }
    }
  }

  // -- Helpers --

  private String perform(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request.accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static List<String> documentIdsOf(String response, String root, Challenge challenge) {
    return JsonPath.read(
        response, root + "[?(@.challenge_id=='" + challenge.getId() + "')].challenge_documents[*]");
  }

  private static List<String> documentIds(Challenge challenge) {
    return challenge.getDocuments().stream().map(Document::getId).toList();
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private Challenge saveChallengeWithTwoDocuments() {
    Challenge challenge = createDefaultChallenge();
    challenge.setTenant(tenant);
    challenge.setDocuments(new ArrayList<>(List.of(saveDocument(), saveDocument())));
    return challengeRepository.save(challenge);
  }

  private Document saveDocument() {
    Document document = getDocumentJpeg();
    document.setName("bulk-enrichment-" + UUID.randomUUID());
    document.setTarget("bulk-enrichment-" + UUID.randomUUID());
    document.setTenant(tenant);
    return documentRepository.save(document);
  }

  private void publishInScenario(Challenge challenge) {
    Inject inject =
        createDefaultInjectChallenge(
            injectorContractRepository.findById(CHALLENGE_PUBLISH).orElseThrow(),
            objectMapper,
            List.of(challenge.getId()));
    inject.setScenario(scenario);
    injectRepository.save(inject);
  }

  private void startCapture() {
    sqlAppender.list.clear();
    sqlLogger.setLevel(Level.DEBUG);
    // Keep the captured SQL out of the console for the duration of the capture.
    sqlLogger.setAdditive(false);
    sqlLogger.addAppender(sqlAppender);
  }

  private void stopCapture() {
    sqlLogger.detachAppender(sqlAppender);
    sqlLogger.setLevel(previousLevel);
    sqlLogger.setAdditive(previousAdditive);
  }

  private long stopCaptureAndCount() {
    stopCapture();
    return sqlAppender.list.stream()
        .map(ILoggingEvent::getFormattedMessage)
        .filter(sql -> sql.toLowerCase().contains(DOCUMENTS_LINK_TABLE))
        .count();
  }
}
