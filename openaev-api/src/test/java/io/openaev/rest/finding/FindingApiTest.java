package io.openaev.rest.finding;

import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.rest.finding.FindingService.MASK;
import static io.openaev.utils.JsonTestUtils.asJsonString;
import static io.openaev.utils.fixtures.FindingFixture.createDefaultTextFindingWithRandomValue;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.database.specification.FindingSpecification;
import io.openaev.rest.finding.form.AggregatedFindingOutput;
import io.openaev.rest.finding.form.FindingInput;
import io.openaev.rest.finding.form.RelatedFindingOutput;
import io.openaev.utils.TenantIsolationTestHelper;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.composers.*;
import io.openaev.utils.mapper.FindingMapper;
import io.openaev.utils.mockUser.WithMockUser;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.openaev.utils.pagination.SortField;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@TestInstance(PER_CLASS)
@Transactional
@WithMockUser(isAdmin = true)
@DisplayName("Findings search tests")
class FindingApiTest extends IntegrationTest {

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
  @Autowired private FindingDistinctSearchService findingDistinctSearchService;
  @Autowired private EntityManager entityManager;
  @Autowired private TenantIsolationTestHelper tenantIsolationHelper;

  @BeforeEach
  void setUp() {
    scenarioComposer.reset();
    simulationComposer.reset();
    injectComposer.reset();
    tagComposer.reset();
    agentComposer.reset();
    findingComposer.reset();
    endpointComposer.reset();
    assetGroupComposer.reset();
    injectorContractComposer.reset();
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
        List<RelatedFindingOutput> expectedFindings =
            fromIterable(findingRepository.findAll()).stream()
                .map(findingMapper::toRelatedFindingOutput)
                .toList();

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
        List<RelatedFindingOutput> expectedFindings =
            fromIterable(findingRepository.findAll()).stream()
                .map(findingMapper::toRelatedFindingOutput)
                .toList();

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
                .flatMap(inject -> inject.getFindings().stream())
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

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(findingRepository.findAllById(expectedFindingIds)).stream()
                .map(findingMapper::toRelatedFindingOutput)
                .toList();

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

        Exercise ex = scenarioWrapper.get().getExercises().getFirst();

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

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        ex.getInjects().stream()
                            .flatMap(inject -> inject.getFindings().stream().map(Finding::getId))
                            .toList()))
                .stream()
                .map(findingMapper::toRelatedFindingOutput)
                .sorted(
                    (o1, o2) -> {
                      if (o1.getCreationDate().equals(o2.getCreationDate())) {
                        return o1.getValue().compareTo(o2.getValue());
                      }
                      return o1.getCreationDate().compareTo(o2.getCreationDate());
                    })
                .limit(input.getSize())
                .toList();

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

        Inject inject = scenarioWrapper.get().getExercises().getFirst().getInjects().getFirst();

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/injects/" + inject.getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        inject.getFindings().stream().map(Finding::getId).toList()))
                .stream()
                .map(findingMapper::toRelatedFindingOutput)
                .limit(input.getSize())
                .toList();

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
            for (Finding finding : inject.getFindings()) {
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

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(findingRepository.findAllById(expectedFindingIds)).stream()
                .map(findingMapper::toRelatedFindingOutput)
                .toList();

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
            for (Finding finding : inject.getFindings()) {
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

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(findingRepository.findAllById(expectedFindingIds)).stream()
                .map(findingMapper::toRelatedFindingOutput)
                .toList();

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
            for (Finding finding : inject.getFindings()) {
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

        List<RelatedFindingOutput> expectedFindings =
            fromIterable(findingRepository.findAllById(expectedFindingIds)).stream()
                .map(findingMapper::toRelatedFindingOutput)
                .toList();

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

    @Test
    @DisplayName("Finding created in tenant X should NOT be updatable from tenant Y")
    void given_findingInTenantX_should_notBeUpdatableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_FINDINGS, Capability.ACCESS_FINDINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.MANAGE_FINDINGS, Capability.ACCESS_FINDINGS));

      Finding finding = createFindingInTenant(tenantX.getId());
      entityManager.flush();
      entityManager.clear();

      FindingInput updateInput = new FindingInput();
      updateInput.setField("updated_field");
      updateInput.setType(ContractOutputType.Text);
      updateInput.setValue("updated_value");
      updateInput.setInjectId(finding.getInject().getId());

      // -------- Act --------
      int responseStatus =
          mvc.perform(
                  put("/api/tenants/" + tenantY.getId() + "/findings/" + finding.getId())
                      .content(asJsonString(updateInput))
                      .contentType(MediaType.APPLICATION_JSON)
                      .accept(MediaType.APPLICATION_JSON)
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
    }

    @Test
    @DisplayName("Finding created in tenant X should NOT be deletable from tenant Y")
    void given_findingInTenantX_should_notBeDeletableFromTenantY() throws Exception {
      // -------- Arrange --------
      Tenant tenantX =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant X", Set.of(Capability.MANAGE_FINDINGS, Capability.ACCESS_FINDINGS));
      Tenant tenantY =
          tenantIsolationHelper.createTenantWithCapabilities(
              "Tenant Y", Set.of(Capability.DELETE_FINDINGS, Capability.ACCESS_FINDINGS));

      Finding finding = createFindingInTenant(tenantX.getId());
      entityManager.flush();
      entityManager.clear();

      // -------- Act --------
      int responseStatus =
          mvc.perform(
                  delete("/api/tenants/" + tenantY.getId() + "/findings/" + finding.getId())
                      .with(csrf()))
              .andReturn()
              .getResponse()
              .getStatus();

      // -------- Assert --------
      assertThat(responseStatus).isEqualTo(HttpStatus.NOT_FOUND.value());
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

      savedSimulation = savedScenario.getExercises().getFirst();
    }

    @DisplayName("Search global findings")
    @Test
    public void given_a_search_input_should_return_page_of_findings() throws Exception {
      Finding savedFinding =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
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
              jsonPath("$.content.[0].finding_inject.inject_id")
                  .value(savedFinding.getInject().getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_simulation.exercise_id")
                  .value(savedSimulation.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_scenario.scenario_id").value(savedScenario.getId()));
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
          .andExpect(
              jsonPath("$.content.[0].finding_scenario.scenario_id").value(savedScenario.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_type").value(savedFinding.getType().getLabel()))
          // Credentials are sensitive: the API never returns the cleartext value.
          .andExpect(jsonPath("$.content.[0].finding_value").value("ad" + MASK))
          .andExpect(jsonPath("$.content.[0].finding_is_sensitive").value(true));
    }

    @Test
    @DisplayName("Search findings by endpoint")
    void should_return_findings_by_endpoint() throws Exception {
      Finding savedFinding =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .withInject(injectWrapper)
              .withTag(tagComposer.forTag(TagFixture.getTagWithText("Finding Text")))
              .persist()
              .get();
      SearchPaginationInput input =
          buildDefaultFilters(
              ContractOutputType.Text, savedFinding, null, null, savedEndpoint, null);

      performCallbackRequest(FINDING_URI + "/endpoints/" + savedEndpoint.getId() + "/search", input)
          .andExpect(
              jsonPath("$.content.[0].finding_assets.[0].asset_id").value(savedEndpoint.getId()))
          .andExpect(
              jsonPath("$.content.[0].finding_type").value(savedFinding.getType().getLabel()))
          .andExpect(jsonPath("$.content.[0].finding_value").value("text_value"));
    }

    @Test
    void distinctTypeValueWithFilter_returnsDistinctFindings() {
      // Create two findings with the same type and value (duplicates)
      Finding f1 =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withInject(injectWrapper)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();

      Finding f2 =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withInject(injectWrapper2)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();

      // Create a unique finding with different type or value
      Finding f3 =
          findingComposer
              .forFinding(FindingFixture.createDefaultIPV6Finding())
              .withInject(injectWrapper)
              .persist()
              .get();

      // base specification can be null (no additional filtering)
      Specification<Finding> baseSpec = null;

      Specification<Finding> distinctSpec =
          FindingSpecification.distinctTypeValueWithFilter(baseSpec);

      List<Finding> results = findingRepository.findAll(distinctSpec);

      // Should return only 2 distinct findings (f1/f2 collapse to one)
      assertThat(results).hasSize(2);

      Set<String> distinctPairs =
          results.stream()
              .map(f -> f.getType().getLabel() + "::" + f.getValue())
              .collect(Collectors.toSet());

      assertThat(distinctPairs)
          .containsExactlyInAnyOrder(
              f1.getType().getLabel() + "::" + f1.getValue(),
              f3.getType().getLabel() + "::" + f3.getValue());
    }

    @Test
    @DisplayName("Distinct list uses the most recent occurrence as representative (issue #7273)")
    void distinctList_usesMostRecentOccurrenceAsRepresentative() {
      // Group A: the SAME (type, value) reported by two injects, i.e. two runs. finding_updated_at
      // is set via native SQL because the JPA listeners overwrite it on persist.
      Finding olderA =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withInject(injectWrapper)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();
      Finding newerA =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withInject(injectWrapper2)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();
      // Group B: a single, different (type, value) whose last seen sits between group A's two runs,
      // so a correct sort by finding_updated_at DESC must place group A (latest run) before it.
      Finding onlyB =
          findingComposer
              .forFinding(FindingFixture.createDefaultIPV6Finding())
              .withInject(injectWrapper)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();

      Instant aCreatedEarliest = Instant.parse("2026-01-01T00:00:00Z");
      Instant aUpdatedOlder = Instant.parse("2026-01-02T00:00:00Z");
      Instant aCreatedLater = Instant.parse("2026-03-01T00:00:00Z");
      Instant aUpdatedNewest = Instant.parse("2026-03-02T00:00:00Z");
      Instant bUpdated = Instant.parse("2026-02-01T00:00:00Z");
      setFindingDates(olderA.getId(), aCreatedEarliest, aUpdatedOlder);
      setFindingDates(newerA.getId(), aCreatedLater, aUpdatedNewest);
      setFindingDates(onlyB.getId(), bUpdated, bUpdated);

      entityManager.flush();
      entityManager.clear();

      SearchPaginationInput input = PaginationFixture.getDefault().size(500).build();
      input.setSorts(List.of(new SortField("finding_updated_at", "desc", null)));

      Page<AggregatedFindingOutput> page =
          findingDistinctSearchService.searchDistinctFindings(input);

      // Exactly two groups, none duplicated
      assertThat(page.getContent()).hasSize(2);
      List<AggregatedFindingOutput> groupA =
          page.getContent().stream().filter(o -> o.getValue().equals(olderA.getValue())).toList();
      assertThat(groupA).as("group A must appear exactly once").hasSize(1);
      AggregatedFindingOutput representativeA = groupA.getFirst();

      // (a) the representative is the MOST RECENT occurrence
      assertThat(representativeA.getId()).isEqualTo(newerA.getId());
      // (b) displayed last seen == latest updateDate, first seen == earliest creationDate
      assertThat(representativeA.getUpdateDate()).isEqualTo(aUpdatedNewest);
      assertThat(representativeA.getCreationDate()).isEqualTo(aCreatedEarliest);
      // (c) sort by finding_updated_at DESC orders groups by their group-wide last seen: group A
      // (last seen 2026-03-02) must come before group B (last seen 2026-02-01)
      assertThat(page.getContent().getFirst().getValue()).isEqualTo(olderA.getValue());
      assertThat(page.getContent().getLast().getValue()).isEqualTo(onlyB.getValue());
    }

    @Test
    @DisplayName("A group does not vanish when a filter matches only an older occurrence (#7273)")
    void distinctList_groupSurvivesFilterMatchingOnlyOlderOccurrence() {
      Finding olderA =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withInject(injectWrapper)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();
      Finding newerA =
          findingComposer
              .forFinding(FindingFixture.createDefaultTextFinding())
              .withInject(injectWrapper2)
              .withEndpoint(endpointComposer.forEndpoint(savedEndpoint))
              .persist()
              .get();

      setFindingDates(
          olderA.getId(),
          Instant.parse("2026-01-01T00:00:00Z"),
          Instant.parse("2026-01-02T00:00:00Z"));
      setFindingDates(
          newerA.getId(),
          Instant.parse("2026-03-01T00:00:00Z"),
          Instant.parse("2026-03-02T00:00:00Z"));

      entityManager.flush();
      entityManager.clear();

      // Filter matches only the OLDER occurrence (its own inject). The group must still appear,
      // with
      // the older occurrence promoted to representative (most recent among the matching rows).
      SearchPaginationInput input = new SearchPaginationInput();
      Filters.FilterGroup group = new Filters.FilterGroup();
      group.setMode(Filters.FilterMode.and);
      group.setFilters(
          List.of(
              buildFilter(
                  "finding_inject_id",
                  Filters.FilterOperator.contains,
                  List.of(olderA.getInject().getId()))));
      input.setFilterGroup(group);

      Page<AggregatedFindingOutput> page =
          findingDistinctSearchService.searchDistinctFindings(input);

      assertThat(page.getContent()).hasSize(1);
      assertThat(page.getContent().getFirst().getId()).isEqualTo(olderA.getId());
    }

    @Nested
    @DisplayName("When the finding is sensitive")
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
      @DisplayName("Should redact the value when reading the finding")
      void given_aSensitiveFinding_should_redactTheValueOnRead() throws Exception {
        // -------- Arrange --------
        Finding finding = persistSensitiveFinding();

        // -------- Act & Assert --------
        mvc.perform(get(FINDING_URI + "/" + finding.getId()).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finding_value").value("ad" + MASK))
            .andExpect(jsonPath("$.finding_is_sensitive").value(true));
      }

      @Test
      @DisplayName("Should redact the value in the finding summary")
      void given_aSensitiveFinding_should_redactTheValueInTheSummary() throws Exception {
        // -------- Arrange --------
        Finding finding = persistSensitiveFinding();

        // -------- Act & Assert --------
        mvc.perform(get(FINDING_URI + "/" + finding.getId() + "/summary").with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.finding_value").value("ad" + MASK))
            .andExpect(jsonPath("$.finding_is_sensitive").value(true));
      }

      @Test
      @DisplayName("Should keep the cleartext value in database")
      void given_aSensitiveFinding_should_keepTheCleartextValueInDatabase() {
        // -------- Arrange --------
        Finding finding = persistSensitiveFinding();

        // -------- Act --------
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
      void given_aNonSensitiveFinding_should_notRedactTheValue() throws Exception {
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
            .andExpect(jsonPath("$.finding_value").value("text_value"))
            .andExpect(jsonPath("$.finding_is_sensitive").value(false));
      }
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
}
