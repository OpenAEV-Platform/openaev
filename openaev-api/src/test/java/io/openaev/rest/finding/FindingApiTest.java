package io.openaev.rest.finding;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.SensitiveValueMaskingUtils.MASK;
import static io.openaev.utils.fixtures.FindingFixture.createDefaultTextFindingWithRandomValue;
import static io.openaev.utils.fixtures.FindingOccurrenceFixture.createOccurrence;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.openaev.ProductionTablesIntegrationTest;
import io.openaev.architecture.CoversTenantIsolation;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.rest.finding.form.FindingSimple;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mapper.FindingMapper;
import io.openaev.utils.mockUser.TestUserHolder;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@CoversTenantIsolation("findings")
@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Findings search tests")
class FindingApiTest extends ProductionTablesIntegrationTest {

  // The test profile declares no active tables, so the suite registers the production list: with
  // no list, the search isolation below would assert what nothing enforces.
  private static final String FINDING_URI = "/api/findings";

  @Resource protected ObjectMapper mapper;
  @Autowired private MockMvc mvc;

  @Autowired private FindingComposer findingComposer;
  @Autowired private AssetGroupComposer assetGroupComposer;
  @Autowired private EndpointComposer endpointComposer;
  @Autowired private InjectComposer injectComposer;
  @Autowired private InjectorContractComposer injectorContractComposer;
  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer simulationComposer;
  @Autowired private AgentComposer agentComposer;
  @Autowired private TagComposer tagComposer;
  @Autowired private InjectorFixture injectorFixture;
  @Autowired private FindingRepository findingRepository;
  @Autowired private FindingMapper findingMapper;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private MarkingDefinitionComposer markingDefinitionComposer;
  @Autowired private TenantGroupComposer tenantGroupComposer;
  @Autowired private MarkingClearanceCacheManager markingClearanceCacheManager;
  @Autowired private TestUserHolder testUserHolder;

  /**
   * Scopes the test's own transaction, for the tests that query the repository directly instead of
   * going through HTTP. Production reaches those queries through a scoped handler; without this the
   * read is denied and the test sees an empty result. It is deliberately NOT a {@code @BeforeEach}:
   * an HTTP test whose request addresses another tenant must let the handler set the scope, and a
   * class-wide pin makes the aspect refuse that as a redefinition.
   */
  private void scopeToAmbientTenant() {
    tenantTx.setScopeOnCurrentTransaction(TxCtx.forTenant(TenantContext.getCurrentTenant()));
  }

  private MarkingDefinition persistClearedMarking() {
    MarkingDefinition marking =
        markingDefinitionComposer
            .forMarkingDefinition(MarkingDefinitionFixture.createDefaultMarkingDefinition())
            .withTenantId(Tenant.DEFAULT_TENANT_UUID)
            .persist()
            .get();
    // The caller sees a marked asset only through a group holding its marking.
    Group group = TenantGroupFixture.getGroup("finding-markings-" + UUID.randomUUID());
    group.setMarkings(new HashSet<>(Set.of(marking)));
    group.setUsers(new ArrayList<>(List.of(testUserHolder.get())));
    tenantGroupComposer.forGroup(group).persist();
    entityManager.flush();
    markingClearanceCacheManager.evictForUser(testUserHolder.get().getId());
    return marking;
  }

  @BeforeEach
  void setUp() {
    // @WithMockUser builds a user with no users_tenants row, so every scope its requests resolve is
    // TxCtx.missing() and every read of an activated table is denied. Production never has that
    // state (V4_95__Migrate_users_to_default_tenant attaches every user to the default tenant), so
    // without this the suite asserts against an empty result on both sides and passes for the wrong
    // reason.
    tenantIsolationHelper.attachCurrentUserToTenant(TenantContext.getCurrentTenant());
    scenarioComposer.reset();
    simulationComposer.reset();
    injectComposer.reset();
    tagComposer.reset();
    agentComposer.reset();
    findingComposer.reset();
    endpointComposer.reset();
    assetGroupComposer.reset();
    injectorContractComposer.reset();
    markingDefinitionComposer.reset();
    tenantGroupComposer.reset();
  }

  @Nested
  @DisplayName("With several simulations from same scenario in database")
  class WithSeveralSimulationsFromSameScenario {
    private final int numberOfPreviousSimulations = 5;
    private final String firstInjectName = "firstInjectName";
    private final String secondInjectName = "secondInjectName";
    private final String thirdInjectName = "thirdInjectName";
    private final String fourthInjectName = "fourthInjectName";

    private ScenarioComposer.Composer getScenarioWithSimulationsWrapper() {
      ScenarioComposer.Composer scenarioWrapper =
          scenarioComposer.forScenario(ScenarioFixture.getScenario());

      // add simulations with default findings
      for (int i = 0; i < numberOfPreviousSimulations; i++) {
        Hashtable<String, InjectComposer.Composer> injects =
            attachSimulationToScenario(
                scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());
        for (Map.Entry<String, InjectComposer.Composer> entry : injects.entrySet()) {
          for (FindingComposer.Composer findingWrapper : getDefaultFindings()) {
            entry.getValue().withFinding(findingWrapper);
          }
        }
      }

      return scenarioWrapper;
    }

    private Hashtable<String, InjectComposer.Composer> attachSimulationToScenario(
        ScenarioComposer.Composer scenarioWrapper, Exercise simulationFixture) {
      // create arbitrary injects
      Hashtable<String, InjectComposer.Composer> injects = new Hashtable<>();
      injects.put(
          firstInjectName,
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(injectorFixture.getWellKnownOaevImplantInjector())));
      injects.put(
          secondInjectName,
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(injectorFixture.getWellKnownOaevImplantInjector())));
      injects.put(
          thirdInjectName,
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(injectorFixture.getWellKnownOaevImplantInjector())));
      injects.put(
          fourthInjectName,
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(injectorFixture.getWellKnownOaevImplantInjector())));

      ExerciseComposer.Composer simulationWrapper =
          simulationComposer.forExercise(simulationFixture);
      for (Map.Entry<String, InjectComposer.Composer> entry : injects.entrySet()) {
        simulationWrapper.withInject(entry.getValue());
      }

      scenarioWrapper.withSimulation(simulationWrapper);

      return injects;
    }

    private List<FindingComposer.Composer> getDefaultFindings() {
      return new ArrayList<>(
          List.of(
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue()),
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue()),
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue()),
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue())));
    }

    @Nested
    @DisplayName("When searching globally for findings")
    class WhenSearchingGloballyForFindings {
      @Test
      @DisplayName("Returns findings from all simulations of each scenario")
      public void ReturnsFindingsFromAllSimulationsOfEachScenario() throws Exception {
        List<ScenarioComposer.Composer> scenarioWrappers =
            List.of(getScenarioWithSimulationsWrapper(), getScenarioWithSimulationsWrapper());

        // add one more simulation with findings to each scenario
        for (ScenarioComposer.Composer scenarioWrapper : scenarioWrappers) {
          Hashtable<String, InjectComposer.Composer> latestSimulationInjectWrappers =
              attachSimulationToScenario(
                  scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());
          for (Map.Entry<String, InjectComposer.Composer> entry :
              latestSimulationInjectWrappers.entrySet()) {
            entry
                .getValue()
                .withFinding(findingComposer.forFinding(createDefaultTextFindingWithRandomValue()));
          }
          scenarioWrapper.persist();
        }

        // add injects (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withFinding(findingComposer.forFinding(createDefaultTextFindingWithRandomValue()))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().size(500).build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        // every finding is visible, whatever the simulation it comes from
        List<FindingSimple> expectedFindings =
            fromIterable(findingRepository.findAll()).stream()
                .map(findingMapper::toFindingSimple)
                .toList();

        // The expectation is read through the SAME transaction the request just scoped: the aspect
        // is
        // @Before-only, set_config(..., true) is transaction-local, and this class is
        // @Transactional, so
        // the scope the handler resolved is still set here. If the search ever fails closed, the
        // response AND this expectation both come back empty and the comparison holds on [] == [].
        // Asserting the expectation is non-empty is what breaks that coupling.
        assertFalse(
            expectedFindings.isEmpty(), "the expectation must not be empty, or it proves nothing");

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }

      @Test
      @DisplayName("Returns findings from running simulations as well as finished ones")
      public void ReturnsFindingsFromRunningSimulationsAsWellAsFinishedOnes() throws Exception {
        List<ScenarioComposer.Composer> scenarioWrappers =
            List.of(getScenarioWithSimulationsWrapper(), getScenarioWithSimulationsWrapper());

        for (ScenarioComposer.Composer scenarioWrapper : scenarioWrappers) {
          ///  FINISHED simulation with findings
          Hashtable<String, InjectComposer.Composer> finishedSimulationInjectWrappers =
              attachSimulationToScenario(
                  scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());
          for (Map.Entry<String, InjectComposer.Composer> entry :
              finishedSimulationInjectWrappers.entrySet()) {
            entry
                .getValue()
                .withFinding(findingComposer.forFinding(createDefaultTextFindingWithRandomValue()));
          }

          /// RUNNING simulation with findings - visible while the simulation is still running
          Hashtable<String, InjectComposer.Composer> runningSimulationInjectWrappers =
              attachSimulationToScenario(
                  scenarioWrapper, ExerciseFixture.createRunningAttackExercise());
          for (Map.Entry<String, InjectComposer.Composer> entry :
              runningSimulationInjectWrappers.entrySet()) {
            entry
                .getValue()
                .withFinding(findingComposer.forFinding(createDefaultTextFindingWithRandomValue()));
          }
          scenarioWrapper.persist();
        }

        // add injects (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withFinding(findingComposer.forFinding(createDefaultTextFindingWithRandomValue()))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().size(500).build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        // every finding is visible, including those of the running simulations
        List<FindingSimple> expectedFindings =
            fromIterable(findingRepository.findAll()).stream()
                .map(findingMapper::toFindingSimple)
                .toList();

        // The expectation is read through the SAME transaction the request just scoped: the aspect
        // is
        // @Before-only, set_config(..., true) is transaction-local, and this class is
        // @Transactional, so
        // the scope the handler resolved is still set here. If the search ever fails closed, the
        // response AND this expectation both come back empty and the comparison holds on [] == [].
        // Asserting the expectation is non-empty is what breaks that coupling.
        assertFalse(
            expectedFindings.isEmpty(), "the expectation must not be empty, or it proves nothing");

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
    }

    @Nested
    @DisplayName("When searching for findings on scenario")
    class WhenSearchingForFindingsOnScenario {
      @Test
      @DisplayName("Returns findings from all simulations of the scenario")
      public void ReturnsFindingsFromAllSimulationsOfScenario() throws Exception {
        ScenarioComposer.Composer scenarioWrapper = getScenarioWithSimulationsWrapper();

        // add one more simulation with findings to the scenario
        Hashtable<String, InjectComposer.Composer> latestSimulationInjectWrappers =
            attachSimulationToScenario(
                scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());
        for (Map.Entry<String, InjectComposer.Composer> entry :
            latestSimulationInjectWrappers.entrySet()) {
          entry
              .getValue()
              .withFinding(findingComposer.forFinding(createDefaultTextFindingWithRandomValue()));
        }
        scenarioWrapper.persist();

        // every finding of every simulation of the scenario is expected
        List<String> expectedFindingIds =
            scenarioWrapper.get().getExercises().stream()
                .flatMap(exercise -> exercise.getInjects().stream())
                .flatMap(inject -> findingsOf(inject).stream())
                .map(Finding::getId)
                .toList();

        SearchPaginationInput input = PaginationFixture.getDefault().size(500).build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/scenarios/" + scenarioWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FindingSimple> expectedFindings =
            fromIterable(findingRepository.findAllById(expectedFindingIds)).stream()
                .map(findingMapper::toFindingSimple)
                .toList();

        // The expectation is read through the SAME transaction the request just scoped: the aspect
        // is

        // @Before-only, set_config(..., true) is transaction-local, and this class is
        // @Transactional, so

        // the scope the handler resolved is still set here. If the search ever fails closed, the

        // response AND this expectation both come back empty and the comparison holds on [] == [].

        // Asserting the expectation is non-empty is what breaks that coupling.

        assertFalse(
            expectedFindings.isEmpty(), "the expectation must not be empty, or it proves nothing");

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
    }

    @Nested
    @DisplayName("When searching for findings on simulation")
    class WhenSearchingForFindingsOnSimulation {
      @Test
      @DisplayName("Returns all findings for observed simulation")
      public void ReturnsAllFindingsForObservedSimulation() throws Exception {
        ScenarioComposer.Composer scenarioWrapper = getScenarioWithSimulationsWrapper();
        scenarioWrapper.persist();

        Exercise ex = scenarioWrapper.get().getExercises().iterator().next();

        SearchPaginationInput input = PaginationFixture.getDefault().build();
        input.setSorts(
            List.of(
                new SortField("finding_created_at", "asc", null),
                new SortField("finding_value", "asc", null)));

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/exercises/" + ex.getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FindingSimple> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        ex.getInjects().stream()
                            .flatMap(inject -> findingsOf(inject).stream().map(Finding::getId))
                            .toList()))
                .stream()
                .sorted(
                    Comparator.comparing(Finding::getCreationDate)
                        .thenComparing(Finding::getValueDisplay))
                .map(findingMapper::toFindingSimple)
                .limit(input.getSize())
                .toList();

        // The expectation is read through the SAME transaction the request just scoped: the aspect
        // is

        // @Before-only, set_config(..., true) is transaction-local, and this class is
        // @Transactional, so

        // the scope the handler resolved is still set here. If the search ever fails closed, the

        // response AND this expectation both come back empty and the comparison holds on [] == [].

        // Asserting the expectation is non-empty is what breaks that coupling.

        assertFalse(
            expectedFindings.isEmpty(), "the expectation must not be empty, or it proves nothing");

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
    }

    @Nested
    @DisplayName("When searching for findings on inject")
    class WhenSearchingForFindingsOnInject {
      @Test
      @DisplayName("Returns all findings for observed inject")
      public void ReturnsAllFindingsForObservedInject() throws Exception {
        ScenarioComposer.Composer scenarioWrapper = getScenarioWithSimulationsWrapper();
        scenarioWrapper.persist();

        Inject inject =
            scenarioWrapper.get().getExercises().iterator().next().getInjects().getFirst();

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/injects/" + inject.getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FindingSimple> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        findingsOf(inject).stream().map(Finding::getId).toList()))
                .stream()
                .map(findingMapper::toFindingSimple)
                .limit(input.getSize())
                .toList();

        // The expectation is read through the SAME transaction the request just scoped: the aspect
        // is

        // @Before-only, set_config(..., true) is transaction-local, and this class is
        // @Transactional, so

        // the scope the handler resolved is still set here. If the search ever fails closed, the

        // response AND this expectation both come back empty and the comparison holds on [] == [].

        // Asserting the expectation is non-empty is what breaks that coupling.

        assertFalse(
            expectedFindings.isEmpty(), "the expectation must not be empty, or it proves nothing");

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
    }

    @Nested
    @DisplayName("When searching for findings on Endpoint")
    class WhenSearchingForFindingsOnEndpoint {
      @Test
      @DisplayName("Returns findings from all simulations involving endpoint")
      public void ReturnsFindingsFromAllSimulationsInvolvingEndpoint() throws Exception {
        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        ScenarioComposer.Composer scenarioWrapper = getScenarioWithSimulationsWrapper();

        // hack findings to attach to endpoint
        List<Finding> previousSimulationFindings = new ArrayList<>();
        for (Exercise ex : scenarioWrapper.get().getExercises()) {
          for (Inject inject : ex.getInjects()) {
            for (Finding finding : findingsOf(inject)) {
              finding.setAssets(new ArrayList<>(List.of(endpointWrapper.get())));
              previousSimulationFindings.add(finding);
            }
          }
        }

        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();
        // add latest simulation to scenario
        Hashtable<String, InjectComposer.Composer> latestSimulationInjectWrappers =
            attachSimulationToScenario(
                scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());
        for (Map.Entry<String, InjectComposer.Composer> entry :
            latestSimulationInjectWrappers.entrySet()) {
          FindingComposer.Composer findingWrapper =
              findingComposer
                  .forFinding(createDefaultTextFindingWithRandomValue())
                  .withEndpoint(endpointWrapper);
          entry.getValue().withFinding(findingWrapper);
          latestFindingWrappers.add(findingWrapper);
        }
        scenarioWrapper.persist();

        // add injects (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withFinding(findingWrapper.withEndpoint(endpointWrapper))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().size(500).build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/endpoints/" + endpointWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        // previous simulations' findings are visible too
        List<String> expectedFindingIds = new ArrayList<>();
        previousSimulationFindings.forEach(finding -> expectedFindingIds.add(finding.getId()));
        latestFindingWrappers.forEach(wrapper -> expectedFindingIds.add(wrapper.get().getId()));

        List<FindingSimple> expectedFindings =
            fromIterable(findingRepository.findAllById(expectedFindingIds)).stream()
                .map(findingMapper::toFindingSimple)
                .toList();

        // The expectation is read through the SAME transaction the request just scoped: the aspect
        // is

        // @Before-only, set_config(..., true) is transaction-local, and this class is
        // @Transactional, so

        // the scope the handler resolved is still set here. If the search ever fails closed, the

        // response AND this expectation both come back empty and the comparison holds on [] == [].

        // Asserting the expectation is non-empty is what breaks that coupling.

        assertFalse(
            expectedFindings.isEmpty(), "the expectation must not be empty, or it proves nothing");

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }

      @Test
      @DisplayName("Returns previous simulations findings even when latest simulation reports none")
      public void ReturnsPreviousSimulationsFindingsEvenWhenLatestSimulationReportsNone()
          throws Exception {
        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        ScenarioComposer.Composer scenarioWrapper = getScenarioWithSimulationsWrapper();

        // hack findings to attach to endpoint
        List<Finding> previousSimulationFindings = new ArrayList<>();
        for (Exercise ex : scenarioWrapper.get().getExercises()) {
          for (Inject inject : ex.getInjects()) {
            for (Finding finding : findingsOf(inject)) {
              finding.setAssets(new ArrayList<>(List.of(endpointWrapper.get())));
              previousSimulationFindings.add(finding);
            }
          }
        }

        List<FindingComposer.Composer> atomicFindingWrappers = new ArrayList<>();
        // add finished simulation to scenario with no findings
        attachSimulationToScenario(scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());

        scenarioWrapper.persist();

        // add injects (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
          atomicFindingWrappers.add(findingWrapper);
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withFinding(findingWrapper.withEndpoint(endpointWrapper))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().size(500).build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/endpoints/" + endpointWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        // findings of previous simulations remain visible even though the latest one has none
        List<String> expectedFindingIds = new ArrayList<>();
        previousSimulationFindings.forEach(finding -> expectedFindingIds.add(finding.getId()));
        atomicFindingWrappers.forEach(wrapper -> expectedFindingIds.add(wrapper.get().getId()));

        List<FindingSimple> expectedFindings =
            fromIterable(findingRepository.findAllById(expectedFindingIds)).stream()
                .map(findingMapper::toFindingSimple)
                .toList();

        // The expectation is read through the SAME transaction the request just scoped: the aspect
        // is

        // @Before-only, set_config(..., true) is transaction-local, and this class is
        // @Transactional, so

        // the scope the handler resolved is still set here. If the search ever fails closed, the

        // response AND this expectation both come back empty and the comparison holds on [] == [].

        // Asserting the expectation is non-empty is what breaks that coupling.

        assertFalse(
            expectedFindings.isEmpty(), "the expectation must not be empty, or it proves nothing");

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }

      @Test
      @DisplayName("Returns findings from running simulations involving endpoint")
      public void ReturnsFindingsFromRunningSimulationsInvolvingEndpoint() throws Exception {
        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        ScenarioComposer.Composer scenarioWrapper = getScenarioWithSimulationsWrapper();

        // hack findings to attach to endpoint
        List<Finding> previousSimulationFindings = new ArrayList<>();
        for (Exercise ex : scenarioWrapper.get().getExercises()) {
          for (Inject inject : ex.getInjects()) {
            for (Finding finding : findingsOf(inject)) {
              finding.setAssets(new ArrayList<>(List.of(endpointWrapper.get())));
              previousSimulationFindings.add(finding);
            }
          }
        }

        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();
        // add finished simulation to scenario
        Hashtable<String, InjectComposer.Composer> finishedSimulationInjectWrappers =
            attachSimulationToScenario(
                scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());
        for (Map.Entry<String, InjectComposer.Composer> entry :
            finishedSimulationInjectWrappers.entrySet()) {
          FindingComposer.Composer findingWrapper =
              findingComposer
                  .forFinding(createDefaultTextFindingWithRandomValue())
                  .withEndpoint(endpointWrapper);
          entry.getValue().withFinding(findingWrapper);
          latestFindingWrappers.add(findingWrapper);
        }

        // add RUNNING simulation with findings - visible while still running
        Hashtable<String, InjectComposer.Composer> runningSimulationInjectWrappers =
            attachSimulationToScenario(
                scenarioWrapper, ExerciseFixture.createRunningAttackExercise());
        for (Map.Entry<String, InjectComposer.Composer> entry :
            runningSimulationInjectWrappers.entrySet()) {
          FindingComposer.Composer findingWrapper =
              findingComposer
                  .forFinding(createDefaultTextFindingWithRandomValue())
                  .withEndpoint(endpointWrapper);
          entry.getValue().withFinding(findingWrapper);
          latestFindingWrappers.add(findingWrapper);
        }

        scenarioWrapper.persist();

        // add injects (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withFinding(findingWrapper.withEndpoint(endpointWrapper))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().size(500).build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/endpoints/" + endpointWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        // every finding involving the endpoint is visible, including the running simulation's
        List<String> expectedFindingIds = new ArrayList<>();
        previousSimulationFindings.forEach(finding -> expectedFindingIds.add(finding.getId()));
        latestFindingWrappers.forEach(wrapper -> expectedFindingIds.add(wrapper.get().getId()));

        List<FindingSimple> expectedFindings =
            fromIterable(findingRepository.findAllById(expectedFindingIds)).stream()
                .map(findingMapper::toFindingSimple)
                .toList();

        // The expectation is read through the SAME transaction the request just scoped: the aspect
        // is

        // @Before-only, set_config(..., true) is transaction-local, and this class is
        // @Transactional, so

        // the scope the handler resolved is still set here. If the search ever fails closed, the

        // response AND this expectation both come back empty and the comparison holds on [] == [].

        // Asserting the expectation is non-empty is what breaks that coupling.

        assertFalse(
            expectedFindings.isEmpty(), "the expectation must not be empty, or it proves nothing");

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
    }
  }

  @Nested
  @DisplayName("Tenant Isolation")
  @WithMockUser
  class TenantIsolation {

    private Finding createFindingInTenant(String tenantId) {
      String previousTenant = TenantContext.getCurrentTenant();
      tenantIsolationHelper.switchToTenant(tenantId, entityManager);

      InjectComposer.Composer injectWrapper =
          injectComposer.forInject(InjectFixture.getDefaultInject()).persist();
      Finding finding =
          findingComposer
              .forFinding(createDefaultTextFindingWithRandomValue())
              .withInject(injectWrapper)
              .persist()
              .get();

      tenantIsolationHelper.switchToTenant(previousTenant, entityManager);
      return finding;
    }

    @Test
    @DisplayName("Finding created in tenant X should NOT be readable from tenant Y")
    void given_findingInTenantX_should_notBeReadableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_FINDINGS, Capability.ACCESS_FINDINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_FINDINGS));

      Finding finding = createFindingInTenant(tenantX.getId());
      entityManager.flush();
      entityManager.clear();

      // -------- Act --------
      int responseStatus =
          mvc.perform(
                  get("/api/tenants/" + tenantY.getId() + "/findings/" + finding.getId())
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Finding created in tenant X should be readable from tenant X")
    void given_findingInTenantX_should_beReadableFromTenantX() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_FINDINGS, Capability.ACCESS_FINDINGS));

      Finding finding = createFindingInTenant(tenantX.getId());
      entityManager.flush();
      entityManager.clear();

      // -------- Act & Assert --------
      mvc.perform(
              get("/api/tenants/" + tenantX.getId() + "/findings/" + finding.getId()).with(csrf()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.finding_id").value(finding.getId()));
    }

    @Test
    @DisplayName("Finding search in tenant Y should NOT return findings from tenant X")
    void given_findingInTenantX_should_notAppearInTenantYSearch() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_FINDINGS, Capability.ACCESS_FINDINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.ACCESS_FINDINGS));

      createFindingInTenant(tenantX.getId());
      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput searchInput = PaginationFixture.getDefault().build();

      // -------- Act & Assert --------
      mvc.perform(
              post("/api/tenants/" + tenantY.getId() + "/findings/search")
                  .content(asJsonString(searchInput))
                  .contentType(MediaType.APPLICATION_JSON)
                  .accept(MediaType.APPLICATION_JSON)
                  .with(csrf()))
          .andExpect(status().is2xxSuccessful())
          .andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"scenarios", "exercises", "injects", "endpoints"})
    @DisplayName("Finding search scoped to a container of tenant X should NOT answer from tenant Y")
    void given_containerInTenantX_should_notBeSearchableFromTenantY(String container)
        throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X",
              Set.of(
                  Capability.ACCESS_FINDINGS,
                  Capability.ACCESS_ASSESSMENT,
                  Capability.ACCESS_ASSETS,
                  Capability.MANAGE_ASSESSMENT));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y",
              Set.of(
                  Capability.ACCESS_FINDINGS,
                  Capability.ACCESS_ASSESSMENT,
                  Capability.ACCESS_ASSETS));
      ContainerIds ids = createContainersWithFindingInTenant(tenantX.getId());
      entityManager.flush();
      entityManager.clear();
      String containerId =
          switch (container) {
            case "scenarios" -> ids.scenarioId();
            case "exercises" -> ids.simulationId();
            case "injects" -> ids.injectId();
            default -> ids.endpointId();
          };

      // -------- Act --------
      int contentLength =
          mvc.perform(
                  post("/api/tenants/"
                          + tenantY.getId()
                          + "/findings/"
                          + container
                          + "/"
                          + containerId
                          + "/search")
                      .content(asJsonString(PaginationFixture.getDefault().build()))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getContentLength();

      // -------- Assert --------
      assertThat(contentLength).isZero();
    }

    private record ContainerIds(
        String scenarioId, String simulationId, String injectId, String endpointId) {}

    private ContainerIds createContainersWithFindingInTenant(String tenantId) {
      String previousTenant = TenantContext.getCurrentTenant();
      tenantIsolationHelper.switchToTenant(tenantId, entityManager);

      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());
      FindingComposer.Composer findingWrapper =
          findingComposer
              .forFinding(createDefaultTextFindingWithRandomValue())
              .withEndpoint(endpointWrapper);
      InjectComposer.Composer injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withEndpoint(endpointWrapper)
              .withFinding(findingWrapper);
      Scenario scenario =
          scenarioComposer
              .forScenario(ScenarioFixture.getScenario())
              .withSimulation(
                  simulationComposer
                      .forExercise(ExerciseFixture.createFinishedAttackExercise())
                      .withInject(injectWrapper))
              .persist()
              .get();
      Exercise simulation = scenario.getExercises().iterator().next();
      Inject inject = simulation.getInjects().getFirst();

      tenantIsolationHelper.switchToTenant(previousTenant, entityManager);
      return new ContainerIds(
          scenario.getId(),
          simulation.getId(),
          inject.getId(),
          inject.getAssets().getFirst().getId());
    }
  }

  @Nested
  @DisplayName("Basic tests")
  class BasicTests {
    private Exercise savedSimulation;
    private Scenario savedScenario;
    private AssetGroup savedAssetGroup;
    private Endpoint savedEndpoint;
    private InjectComposer.Composer injectWrapper;
    private InjectComposer.Composer injectWrapper2;

    @BeforeEach
    void setup() {
      EndpointComposer.Composer endpointWrapper =
          endpointComposer.forEndpoint(EndpointFixture.createEndpoint());

      savedEndpoint =
          endpointWrapper
              .withAgent(agentComposer.forAgent(AgentFixture.createDefaultAgentService()))
              .get();

      AssetGroupComposer.Composer assetGroupWrapper =
          assetGroupComposer
              .forAssetGroup(AssetGroupFixture.createDefaultAssetGroup("asset-group"))
              .withAsset(endpointWrapper);

      savedAssetGroup = assetGroupWrapper.get();

      injectWrapper =
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withAssetGroup(assetGroupWrapper);

      injectWrapper2 = injectComposer.forInject(InjectFixture.getDefaultInject());

      ExerciseComposer.Composer simulationWrapper =
          simulationComposer
              .forExercise(ExerciseFixture.createFinishedAttackExercise())
              .withInject(injectWrapper);

      savedScenario =
          scenarioComposer
              .forScenario(ScenarioFixture.createDefaultCrisisScenario())
              .withSimulation(simulationWrapper)
              .persist()
              .get();

      savedSimulation = savedScenario.getExercises().iterator().next();
    }

    @DisplayName("Search global findings")
    @Test
    public void given_a_search_input_should_return_page_of_findings() throws Exception {
      Finding finding = FindingFixture.createDefaultTextFinding();
      finding.setLocation(persistAssetLocation(savedEndpoint));
      MarkingDefinition marking = persistClearedMarking();
      savedEndpoint.setMarkingIds(new String[] {marking.getId()});
      Finding savedFinding =
          findingComposer
              .forFinding(finding)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .withInject(injectWrapper)
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("Finding")))
              .persist()
              .get();
      SearchPaginationInput input =
          buildDefaultFilters(
              ContractOutputType.Text,
              savedFinding,
              savedSimulation,
              savedScenario,
              savedEndpoint,
              savedAssetGroup);

      entityManager.flush();
      entityManager.clear();

      performCallbackRequest(FINDING_URI + "/search", input)
          .andExpect(
              jsonPath("$.content.[0].finding_type").value(savedFinding.getType().getLabel()))
          .andExpect(jsonPath("$.content.[0].finding_value").value("text_value"))
          .andExpect(
              jsonPath("$.content.[0].finding_assets.[0].asset_id").value(savedEndpoint.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_assets.[0].asset_markings.[0]")
                  .value(marking.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_inject.inject_id")
                  .value(savedFinding.getInject().getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_simulation.exercise_id")
                  .value(savedSimulation.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_scenario.scenario_id").value(savedScenario.getId()));
    }

    @Test
    @DisplayName("Should read a finding with its dates, occurrence count and location")
    void given_twoOccurrences_should_returnDatesCountAndLocation() throws Exception {
      // Arrange
      Instant firstSeen = Instant.now().truncatedTo(ChronoUnit.SECONDS).minus(3, ChronoUnit.DAYS);
      Instant lastSeen = firstSeen.plus(1, ChronoUnit.DAYS);
      Finding finding = createDefaultTextFindingWithRandomValue();
      finding.setLocation(persistAssetLocation(savedEndpoint));
      finding.setFirstSeen(firstSeen);
      finding.setLastSeen(lastSeen);
      findingComposer.forFinding(finding).persist();
      entityManager.persist(createOccurrence(finding, null, lastSeen, "field"));
      entityManager.persist(createOccurrence(finding, null, firstSeen, "field"));
      entityManager.flush();
      entityManager.clear();

      // Act
      String response =
          mvc.perform(get(FINDING_URI + "/" + finding.getId()).with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();

      // Assert
      assertThat(Instant.parse(JsonPath.read(response, "$.finding_first_seen")))
          .isEqualTo(firstSeen);
      assertThat(Instant.parse(JsonPath.read(response, "$.finding_last_seen"))).isEqualTo(lastSeen);
      assertThat((Integer) JsonPath.read(response, "$.finding_occurrences_count")).isEqualTo(2);
      assertThat((String) JsonPath.read(response, "$.finding_location.target_id"))
          .isEqualTo(savedEndpoint.getId());
    }

    @Test
    @DisplayName("Multi-value not_eq filter excludes every listed value (NOT IN semantics)")
    void given_multiValueNotEqFilter_should_excludeAllListedValues() throws Exception {
      // Three findings with distinct values; the filter must keep only the one whose
      // value matches none of the excluded values. With the historical OR combination
      // ("!= 443 OR != 80"), all three findings would match.
      for (String value : List.of("443", "80", "8080")) {
        Finding finding = FindingFixture.createDefaultTextFinding();
        finding.setValue(value);
        findingComposer
            .forFinding(finding)
            .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
            .withInject(injectWrapper)
            .persist();
      }

      SearchPaginationInput input = new SearchPaginationInput();
      Filters.FilterGroup group = new Filters.FilterGroup();
      group.setMode(Filters.FilterMode.and);
      group.setFilters(
          List.of(
              buildFilter("finding_value", Filters.FilterOperator.not_eq, List.of("443", "80"))));
      input.setFilterGroup(group);

      entityManager.flush();
      entityManager.clear();

      performCallbackRequest(FINDING_URI + "/search", input)
          .andExpect(jsonPath("$.totalElements").value(1))
          .andExpect(jsonPath("$.content.[0].finding_value").value("8080"));
    }

    @Test
    @DisplayName("An asset groups filter matches a finding of a dynamic group member (#9850)")
    void given_assetGroupsFilterOnDynamicGroup_should_returnFindingsOfItsMembers()
        throws Exception {
      // Arrange: a group with no static asset whose filter matches the hostname of savedEndpoint.
      String hostname = "dyn-finding-" + UUID.randomUUID();
      savedEndpoint.setHostname(hostname);
      Filters.FilterGroup dynamicFilter =
          Filters.FilterGroup.builder()
              .filters(
                  List.of(
                      Filters.Filter.builder()
                          .key("asset_hostname")
                          .operator(Filters.FilterOperator.contains)
                          .values(List.of(hostname))
                          .build()))
              .build();
      AssetGroup dynamicGroup =
          assetGroupComposer
              .forAssetGroup(
                  AssetGroupFixture.createAssetGroupWithDynamicFilter(
                      "dynamic-" + UUID.randomUUID(), dynamicFilter))
              .persist()
              .get();
      Finding finding =
          findingComposer
              .forFinding(createDefaultTextFindingWithRandomValue())
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .withInject(injectWrapper)
              .persist()
              .get();
      AssetGroup emptyGroup =
          assetGroupComposer
              .forAssetGroup(
                  AssetGroupFixture.createDefaultAssetGroup("empty-" + UUID.randomUUID()))
              .persist()
              .get();
      entityManager.flush();
      entityManager.clear();

      // Act & Assert: the dynamic group's member finding is returned, in list and distinct mode.
      performCallbackRequest(FINDING_URI + "/search", assetGroupsInput(dynamicGroup.getId()))
          .andExpect(jsonPath("$.totalElements").value(1))
          .andExpect(jsonPath("$.content.[0].finding_id").value(finding.getId()));
      performCallbackRequest(
              FINDING_URI + "/search?distinct=true", assetGroupsInput(dynamicGroup.getId()))
          .andExpect(jsonPath("$.totalElements").value(1));
      // A group with no member filters every finding out.
      performCallbackRequest(FINDING_URI + "/search", assetGroupsInput(emptyGroup.getId()))
          .andExpect(jsonPath("$.totalElements").value(0));
    }

    private SearchPaginationInput assetGroupsInput(String assetGroupId) {
      SearchPaginationInput input = new SearchPaginationInput();
      Filters.FilterGroup group = new Filters.FilterGroup();
      group.setMode(Filters.FilterMode.and);
      group.setFilters(
          List.of(
              buildFilter(
                  "finding_asset_groups", Filters.FilterOperator.eq, List.of(assetGroupId))));
      input.setFilterGroup(group);
      return input;
    }

    @Test
    @DisplayName("Search findings by simulation")
    void should_return_findings_by_simulation() throws Exception {
      Finding savedFinding =
          findingComposer
              .forFinding(FindingFixture.createDefaultIPV6Finding())
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .withInject(injectWrapper)
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("Finding IPv6")))
              .persist()
              .get();
      SearchPaginationInput input =
          buildDefaultFilters(
              ContractOutputType.IPv6, savedFinding, savedSimulation, null, savedEndpoint, null);

      performCallbackRequest(
              FINDING_URI + "/exercises/" + savedSimulation.getId() + "/search", input)
          .andExpect(
              jsonPath("$.content.[0].finding_type").value(savedFinding.getType().getLabel()))
          .andExpect(
              jsonPath("$.content.[0].finding_value")
                  .value("2001:0000:130F:0000:0000:09C0:876A:130B"));
    }

    @Test
    @DisplayName("Search findings by scenario")
    void should_return_findings_by_scenario() throws Exception {
      Finding savedFinding =
          findingComposer
              .forFinding(FindingFixture.createDefaultFindingCredentials())
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .withInject(injectWrapper)
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("Finding")))
              .persist()
              .get();

      SearchPaginationInput input =
          buildDefaultFilters(
              ContractOutputType.Credentials,
              savedFinding,
              null,
              savedScenario,
              savedEndpoint,
              savedAssetGroup);

      entityManager.flush();
      entityManager.clear();

      performCallbackRequest(FINDING_URI + "/scenarios/" + savedScenario.getId() + "/search", input)
          .andExpect(jsonPath("$.content.[0].finding_id").value(savedFinding.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_type").value(savedFinding.getType().getLabel()))
          // Credentials hold secret material: the API never returns the cleartext value.
          .andExpect(jsonPath("$.content.[0].finding_value").value("admin:ad" + MASK));
    }

    @Test
    @DisplayName("Search findings by endpoint")
    void should_return_findings_by_endpoint() throws Exception {
      Finding finding = FindingFixture.createDefaultTextFinding();
      finding.setLocation(persistAssetLocation(savedEndpoint));
      Finding savedFinding =
          findingComposer
              .forFinding(finding)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .withInject(injectWrapper)
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("Finding Text")))
              .persist()
              .get();
      SearchPaginationInput input =
          buildDefaultFilters(
              ContractOutputType.Text, savedFinding, null, null, savedEndpoint, null);

      performCallbackRequest(FINDING_URI + "/endpoints/" + savedEndpoint.getId() + "/search", input)
          .andExpect(jsonPath("$.content.[0].finding_asset.asset_id").value(savedEndpoint.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_type").value(savedFinding.getType().getLabel()))
          .andExpect(jsonPath("$.content.[0].finding_value").value("text_value"));
    }

    @Nested
    @DisplayName("When the finding type holds secret material")
    class WhenTheFindingIsSensitive {

      private Finding persistSensitiveFinding() {
        Finding finding =
            findingComposer
                .forFinding(FindingFixture.createDefaultFindingCredentials())
                .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
                .withInject(injectWrapper)
                .persist()
                .get();
        entityManager.flush();
        entityManager.clear();
        return finding;
      }

      @Test
      @DisplayName("Should mask the value when reading the finding")
      void given_aSensitiveFinding_should_maskTheValueOnRead() throws Exception {
        // -------- Arrange --------
        Finding finding = persistSensitiveFinding();

        // -------- Act & Assert --------
        mvc.perform(get(FINDING_URI + "/" + finding.getId()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finding_value").value("admin:ad" + MASK));
      }

      @Test
      @DisplayName("Should keep the cleartext value in database")
      void given_aSensitiveFinding_should_keepTheCleartextValueInDatabase() {
        // -------- Arrange --------
        Finding finding = persistSensitiveFinding();

        // -------- Act --------
        // findings is a v2-activated table (#7856): this read does not go through HTTP, so no
        // handler sets the scope, the statement inspector fail-closes the query and the row comes
        // back missing rather than unmasked - which would make this assertion pass for the wrong
        // reason if it ever asserted absence.
        scopeToAmbientTenant();
        Object storedValue =
            entityManager
                .createNativeQuery("SELECT finding_value FROM findings WHERE finding_id = :id")
                .setParameter("id", finding.getId())
                .getSingleResult();

        // -------- Assert --------
        assertThat(storedValue).isEqualTo("admin:admin");
      }

      @Test
      @DisplayName("Should leave the value of a non sensitive finding untouched")
      void given_aNonSensitiveFinding_should_notMaskTheValue() throws Exception {
        // -------- Arrange --------
        Finding finding =
            findingComposer
                .forFinding(FindingFixture.createDefaultTextFinding())
                .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
                .withInject(injectWrapper)
                .persist()
                .get();
        entityManager.flush();
        entityManager.clear();

        // -------- Act & Assert --------
        mvc.perform(get(FINDING_URI + "/" + finding.getId()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finding_value").value("text_value"));
      }
    }

    @Nested
    @DisplayName("When querying the value of a sensitive finding (#7893)")
    class WhenQueryingTheValueOfASensitiveFinding {

      private static final String SECRET_CREDENTIALS = "admin:motdepasse";
      private static final String DISPLAYED_CREDENTIALS = "admin:mo" + MASK;

      // Text search also reads the inject title, and the default fixture titles an inject
      // "inject-<random UUID>": a UUID holds the hex pair "ab" about one run in nine, which made
      // the "bob:abc" / "ab" case match through the title instead of the value (main red on
      // 3838cec2). A fixed title that shares no searched fragment keeps every case about the value.
      private static final String INJECT_TITLE = "Sensitive finding inject";

      private Finding persistFinding(Finding finding) {
        injectWrapper.get().setTitle(INJECT_TITLE);
        return findingComposer
            .forFinding(finding)
            .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
            .withInject(injectWrapper)
            .persist()
            .get();
      }

      private SearchPaginationInput textSearch(String text) {
        SearchPaginationInput input = new SearchPaginationInput();
        input.setTextSearch(text);
        return input;
      }

      private SearchPaginationInput valueFilter(Filters.FilterOperator operator, String value) {
        SearchPaginationInput input = new SearchPaginationInput();
        Filters.FilterGroup group = new Filters.FilterGroup();
        group.setMode(Filters.FilterMode.and);
        group.setFilters(List.of(buildFilter("finding_value", operator, List.of(value))));
        input.setFilterGroup(group);
        return input;
      }

      private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
      }

      @Test
      @DisplayName("Should not find a finding by the hidden part of its value")
      void given_theHiddenSecret_should_notFindTheFindingByTextSearch() throws Exception {
        // -------- Arrange --------
        persistFinding(FindingFixture.createCredentialsFinding(SECRET_CREDENTIALS));
        flushAndClear();

        // -------- Act & Assert --------
        performCallbackRequest(FINDING_URI + "/search", textSearch("motdepasse"))
            .andExpect(jsonPath("$.totalElements").value(0));
      }

      @ParameterizedTest(name = "searching \"{0}\"")
      @ValueSource(strings = {"admin", "mo"})
      @DisplayName("Should find a finding by a part of its value the platform displays")
      void given_aDisplayedPart_should_findTheFindingByTextSearch(String displayedPart)
          throws Exception {
        // -------- Arrange --------
        persistFinding(FindingFixture.createCredentialsFinding(SECRET_CREDENTIALS));
        flushAndClear();

        // -------- Act & Assert --------
        performCallbackRequest(FINDING_URI + "/search", textSearch(displayedPart))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content.[0].finding_value").value(DISPLAYED_CREDENTIALS));
      }

      @ParameterizedTest(name = "{0} \"{1}\"")
      @CsvSource({"contains, motdepasse", "starts_with, admin:mot", "eq, admin:motdepasse"})
      @DisplayName("Should match nothing with a filter on the hidden part of a value")
      void given_aFilterOnTheHiddenSecret_should_matchNothing(
          Filters.FilterOperator operator, String guess) throws Exception {
        // -------- Arrange --------
        persistFinding(FindingFixture.createCredentialsFinding(SECRET_CREDENTIALS));
        flushAndClear();

        // -------- Act & Assert --------
        performCallbackRequest(FINDING_URI + "/search", valueFilter(operator, guess))
            .andExpect(jsonPath("$.totalElements").value(0));
      }

      @Test
      @DisplayName("Should match the displayed value with an equality filter")
      void given_anEqualityFilterOnTheDisplayedValue_should_matchTheFinding() throws Exception {
        // -------- Arrange --------
        persistFinding(FindingFixture.createCredentialsFinding(SECRET_CREDENTIALS));
        flushAndClear();

        // -------- Act & Assert --------
        performCallbackRequest(
                FINDING_URI + "/search",
                valueFilter(Filters.FilterOperator.eq, DISPLAYED_CREDENTIALS))
            .andExpect(jsonPath("$.totalElements").value(1));
      }

      @Test
      @DisplayName("Should sort on the displayed value, so a planted value reveals nothing")
      void given_aPlantedValue_should_notLandBetweenTwoSecretsDisplayedAlike() throws Exception {
        // -------- Arrange --------
        // Both secrets are displayed "admin:mo******". Sorted on the cleartext, the planted value
        // would land between them (motdepasse < mou < mozzzzzz) and tell their hidden parts apart.
        persistFinding(FindingFixture.createCredentialsFinding(SECRET_CREDENTIALS));
        persistFinding(FindingFixture.createCredentialsFinding("admin:mozzzzzz"));
        Finding planted = FindingFixture.createDefaultTextFinding();
        planted.setValue("admin:mou");
        persistFinding(planted);
        flushAndClear();

        SearchPaginationInput input = new SearchPaginationInput();
        input.setSorts(
            List.of(SortField.builder().property("finding_value").direction("asc").build()));

        // -------- Act --------
        String response =
            performCallbackRequest(FINDING_URI + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        // -------- Assert --------
        List<String> sortedValues = JsonPath.read(response, "$.content[*].finding_value");
        assertThat(sortedValues)
            .containsExactlyInAnyOrder(DISPLAYED_CREDENTIALS, DISPLAYED_CREDENTIALS, "admin:mou");
        assertThat(sortedValues.indexOf("admin:mou")).isNotEqualTo(1);
      }

      @Test
      @DisplayName(
          "Should list every occurrence of a displayed value, as the Occurrences drawer does")
      void given_theDisplayedValue_should_listEveryOccurrence() throws Exception {
        // -------- Arrange --------
        persistFinding(FindingFixture.createCredentialsFinding("jdoe:Su111"));
        persistFinding(FindingFixture.createCredentialsFinding("jdoe:Su222"));
        flushAndClear();

        // -------- Act & Assert --------
        performCallbackRequest(
                FINDING_URI + "/search", valueFilter(Filters.FilterOperator.eq, "jdoe:Su" + MASK))
            .andExpect(jsonPath("$.totalElements").value(2));
      }

      @ParameterizedTest(name = "\"{0}\" searched with \"{1}\" -> {2}")
      @CsvSource({
        "bob:abc, ab, 0",
        "bob:abc, bob, 1",
        "alice:pa:ss:word, word, 0",
        "alice:pa:ss:word, alice, 1",
        "admin:motdepasse, ******, 1"
      })
      @DisplayName("Should only search what the platform displays, for unusual values too")
      void given_anUnusualValue_should_onlyBeSearchableByItsDisplayedForm(
          String secret, String search, int expectedMatches) throws Exception {
        // -------- Arrange --------
        persistFinding(FindingFixture.createCredentialsFinding(secret));
        flushAndClear();

        // -------- Act & Assert --------
        performCallbackRequest(FINDING_URI + "/search", textSearch(search))
            .andExpect(jsonPath("$.totalElements").value(expectedMatches));
      }

      @Test
      @DisplayName("Should keep a non sensitive value searchable and filterable as a whole")
      void given_aNonSensitiveValue_should_stayQueryableOnItsFullValue() throws Exception {
        // -------- Arrange --------
        persistFinding(FindingFixture.createDefaultTextFinding());
        flushAndClear();

        // -------- Act & Assert --------
        performCallbackRequest(FINDING_URI + "/search", textSearch("text_value"))
            .andExpect(jsonPath("$.totalElements").value(1));
        performCallbackRequest(
                FINDING_URI + "/search", valueFilter(Filters.FilterOperator.eq, "text_value"))
            .andExpect(jsonPath("$.totalElements").value(1));
      }
    }

    @Test
    @DisplayName("The summary reads first/last seen, not the row's update time (#9822)")
    void summaryAggregate_readsLastSeenNotUpdateDate() {
      scopeToAmbientTenant();
      Finding detected =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withInject(injectWrapper)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();
      Finding legacy =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withInject(injectWrapper2)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();

      // A migration rewrote the detected row late: its update time is not a detection.
      Instant rewrittenAt = Instant.parse("2026-11-24T22:28:00Z");
      setFindingDates(detected.getId(), Instant.parse("2026-01-05T00:00:00Z"), rewrittenAt);
      setFindingSeen(
          detected.getId(),
          Instant.parse("2026-01-01T00:00:00Z"),
          Instant.parse("2026-03-02T00:00:00Z"));
      // A row without first/last seen falls back to its creation and update times.
      setFindingDates(
          legacy.getId(),
          Instant.parse("2026-02-01T00:00:00Z"),
          Instant.parse("2026-02-02T00:00:00Z"));
      setFindingSeen(legacy.getId(), null, null);

      entityManager.flush();
      entityManager.clear();

      FindingRepository.FindingSeenAggregate seen =
          findingRepository.findSeenAggregate(
              detected.getType(), detected.getValueDisplay(), TenantContext.getCurrentTenant());

      assertThat(seen.getOccurrences()).isEqualTo(2);
      assertThat(seen.getFirstSeen()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
      assertThat(seen.getLastSeen()).isEqualTo(Instant.parse("2026-03-02T00:00:00Z"));
    }

    @Test
    @DisplayName("Get, search and summary expose detection times, not write time (#9846)")
    void given_aWriteAfterTheLastDetection_should_exposeLastSeenOnGetSearchAndSummary()
        throws Exception {
      Finding detected =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withInject(injectWrapper)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();
      // Last detection at T1, a later write of the row (status, asset link, migration) at T2.
      Instant createdAt = Instant.parse("2026-01-05T00:00:00Z");
      Instant firstSeen = Instant.parse("2026-01-01T00:00:00Z");
      Instant lastSeenT1 = Instant.parse("2026-03-02T00:00:00Z");
      Instant writtenT2 = Instant.parse("2026-11-24T22:28:00Z");
      setFindingDates(detected.getId(), createdAt, writtenT2);
      setFindingSeen(detected.getId(), firstSeen, lastSeenT1);

      entityManager.flush();
      entityManager.clear();

      String getBody =
          mvc.perform(get(FINDING_URI + "/" + detected.getId()).with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(readInstant(getBody, "$.finding_first_seen")).isEqualTo(firstSeen);
      assertThat(readInstant(getBody, "$.finding_last_seen")).isEqualTo(lastSeenT1);
      assertThat(readInstant(getBody, "$.finding_created_at")).isEqualTo(createdAt);
      assertThat(readInstant(getBody, "$.finding_updated_at")).isEqualTo(writtenT2);

      String searchBody =
          performCallbackRequest(FINDING_URI + "/search?distinct=true", new SearchPaginationInput())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(JsonPath.<Integer>read(searchBody, "$.content.length()")).isEqualTo(1);
      assertThat(readInstant(searchBody, "$.content[0].finding_first_seen")).isEqualTo(firstSeen);
      assertThat(readInstant(searchBody, "$.content[0].finding_last_seen")).isEqualTo(lastSeenT1);
      assertThat(readInstant(searchBody, "$.content[0].finding_updated_at")).isEqualTo(writtenT2);

      String summaryBody =
          mvc.perform(get(FINDING_URI + "/" + detected.getId() + "/summary").with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(readInstant(summaryBody, "$.finding_last_seen")).isEqualTo(lastSeenT1);
      assertThat(readInstant(summaryBody, "$.finding_first_seen")).isEqualTo(firstSeen);
    }

    @Test
    @DisplayName("Get and search fall back to creation and update times (#9846)")
    void given_noDetectionTimes_should_fallBackToCreationAndUpdateTimes() throws Exception {
      Finding legacy =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withInject(injectWrapper)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();
      Instant createdAt = Instant.parse("2026-02-01T00:00:00Z");
      Instant updatedAt = Instant.parse("2026-02-02T00:00:00Z");
      setFindingDates(legacy.getId(), createdAt, updatedAt);
      setFindingSeen(legacy.getId(), null, null);

      entityManager.flush();
      entityManager.clear();

      String getBody =
          mvc.perform(get(FINDING_URI + "/" + legacy.getId()).with(csrf()))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(readInstant(getBody, "$.finding_first_seen")).isEqualTo(createdAt);
      assertThat(readInstant(getBody, "$.finding_last_seen")).isEqualTo(updatedAt);

      String searchBody =
          performCallbackRequest(FINDING_URI + "/search?distinct=true", new SearchPaginationInput())
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(readInstant(searchBody, "$.content[0].finding_first_seen")).isEqualTo(createdAt);
      assertThat(readInstant(searchBody, "$.content[0].finding_last_seen")).isEqualTo(updatedAt);
    }

    private Instant readInstant(String json, String path) {
      return Instant.parse(JsonPath.<String>read(json, path));
    }

    private void setFindingSeen(String findingId, Instant firstSeen, Instant lastSeen) {
      entityManager
          .createNativeQuery(
              "UPDATE findings SET finding_first_seen = CAST(:firstSeen AS timestamptz),"
                  + " finding_last_seen = CAST(:lastSeen AS timestamptz) WHERE finding_id = :id")
          .setParameter("firstSeen", firstSeen == null ? null : firstSeen.toString())
          .setParameter("lastSeen", lastSeen == null ? null : lastSeen.toString())
          .setParameter("id", findingId)
          .executeUpdate();
    }

    private void setFindingDates(String findingId, Instant createdAt, Instant updatedAt) {
      entityManager
          .createNativeQuery(
              "UPDATE findings SET finding_created_at = :createdAt, finding_updated_at = :updatedAt"
                  + " WHERE finding_id = :id")
          .setParameter("createdAt", createdAt)
          .setParameter("updatedAt", updatedAt)
          .setParameter("id", findingId)
          .executeUpdate();
    }
  }

  private SearchPaginationInput buildDefaultFilters(
      ContractOutputType type,
      Finding finding,
      Exercise simulation,
      Scenario scenario,
      Endpoint endpoint,
      AssetGroup assetGroup) {
    SearchPaginationInput input = new SearchPaginationInput();
    Filters.FilterGroup group = new Filters.FilterGroup();
    group.setMode(Filters.FilterMode.and);

    Instant now = Instant.now().minus(1, ChronoUnit.DAYS);

    List<Filters.Filter> filters = new ArrayList<>();

    filters.add(
        buildFilter("finding_type", Filters.FilterOperator.contains, List.of(type.getLabel())));
    filters.add(
        buildFilter("finding_created_at", Filters.FilterOperator.gt, List.of(now.toString())));
    filters.add(
        buildFilter(
            "finding_tags",
            Filters.FilterOperator.contains,
            List.of(finding.getTags().stream().findFirst().get().getId())));
    filters.add(
        buildFilter(
            "finding_inject_id",
            Filters.FilterOperator.contains,
            List.of(finding.getInject().getId())));

    if (assetGroup != null) {
      filters.add(
          buildFilter(
              "finding_asset_groups",
              Filters.FilterOperator.contains,
              List.of(assetGroup.getId())));
    }
    if (endpoint != null) {
      filters.add(
          buildFilter(
              "finding_assets", Filters.FilterOperator.contains, List.of(endpoint.getId())));
    }
    if (simulation != null) {
      filters.add(
          buildFilter(
              "finding_simulation", Filters.FilterOperator.contains, List.of(simulation.getId())));
    }
    if (scenario != null) {
      filters.add(
          buildFilter(
              "finding_scenario", Filters.FilterOperator.contains, List.of(scenario.getId())));
    }

    group.setFilters(filters);
    input.setFilterGroup(group);
    return input;
  }

  private Filters.Filter buildFilter(
      String key, Filters.FilterOperator operator, List<String> values) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setMode(Filters.FilterMode.and);
    filter.setOperator(operator);
    filter.setValues(values);
    return filter;
  }

  private FindingLocation persistAssetLocation(Asset asset) {
    FindingLocation location = new FindingLocation();
    location.setAsset(asset);
    location.setLocationKey(FindingLocationKeys.keyOf(asset.getId(), null));
    entityManager.persist(location);
    return location;
  }

  private ResultActions performCallbackRequest(String uri, SearchPaginationInput input)
      throws Exception {
    return mvc.perform(
            post(uri)
                .content(asJsonString(input))
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .with(csrf()))
        .andExpect(status().is2xxSuccessful());
  }

  // The findings of an inject are those with an occurrence on it
  private List<Finding> findingsOf(Inject inject) {
    return findingRepository.findAllByInjectId(inject.getId());
  }
}
