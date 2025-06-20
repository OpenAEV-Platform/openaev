package io.openbas.rest.finding;

import static io.openbas.helper.StreamHelper.fromIterable;
import static io.openbas.utils.JsonUtils.asJsonString;
import static io.openbas.utils.fixtures.FindingFixture.TEXT_FIELD;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openbas.IntegrationTest;
import io.openbas.database.model.*;
import io.openbas.database.repository.FindingRepository;
import io.openbas.rest.finding.form.FindingOutput;
import io.openbas.utils.FindingMapper;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.*;
import io.openbas.utils.mockUser.WithMockAdminUser;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.annotation.Resource;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import net.javacrumbs.jsonunit.core.Option;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@TestInstance(PER_CLASS)
@Transactional
@WithMockAdminUser
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
  @Autowired private EntityManager entityManager;

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
                      .withInjector(injectorFixture.getWellKnownObasImplantInjector())));
      injects.put(
          secondInjectName,
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(injectorFixture.getWellKnownObasImplantInjector())));
      injects.put(
          thirdInjectName,
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(injectorFixture.getWellKnownObasImplantInjector())));
      injects.put(
          fourthInjectName,
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withInjectorContract(
                  injectorContractComposer
                      .forInjectorContract(InjectorContractFixture.createDefaultInjectorContract())
                      .withInjector(injectorFixture.getWellKnownObasImplantInjector())));

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
              findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue()),
              findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue()),
              findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue()),
              findingComposer.forFinding(
                  FindingFixture.createDefaultTextFindingWithRandomValue())));
    }

    @Nested
    @DisplayName("When searching globally for findings")
    class WhenSearchingGloballyForFindings {
      @Test
      @DisplayName("Returns only findings for latest simulation of each scenario")
      public void ReturnsOnlyFindingsForLatestSimulationOfEachScenario() throws Exception {
        List<ScenarioComposer.Composer> scenarioWrappers =
            List.of(getScenarioWithSimulationsWrapper(), getScenarioWithSimulationsWrapper());

        // latest findings
        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();

        // add latest simulation to each scenario
        for (ScenarioComposer.Composer scenarioWrapper : scenarioWrappers) {
          Hashtable<String, InjectComposer.Composer> latestSimulationInjectWrappers =
              attachSimulationToScenario(
                  scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());
          for (Map.Entry<String, InjectComposer.Composer> entry :
              latestSimulationInjectWrappers.entrySet()) {
            FindingComposer.Composer findingWrapper =
                findingComposer.forFinding(
                    FindingFixture.createDefaultTextFindingWithRandomValue());
            latestFindingWrappers.add(findingWrapper);
            entry.getValue().withFinding(findingWrapper);
          }
          scenarioWrapper.persist();
        }

        // add injects (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withFinding(findingWrapper)
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }

      @Test
      @DisplayName("Returns only findings for latest finished simulation of each scenario")
      public void ReturnsOnlyFindingsForLatestFinishedSimulationOfEachScenario() throws Exception {
        List<ScenarioComposer.Composer> scenarioWrappers =
            List.of(getScenarioWithSimulationsWrapper(), getScenarioWithSimulationsWrapper());

        // latest findings
        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();

        // add latest simulations to each scenario
        for (ScenarioComposer.Composer scenarioWrapper : scenarioWrappers) {
          ///  FINISHED simulation
          Hashtable<String, InjectComposer.Composer> latestSimulationInjectWrappers =
              attachSimulationToScenario(
                  scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());
          for (Map.Entry<String, InjectComposer.Composer> entry :
              latestSimulationInjectWrappers.entrySet()) {
            FindingComposer.Composer findingWrapper =
                findingComposer.forFinding(
                    FindingFixture.createDefaultTextFindingWithRandomValue());
            latestFindingWrappers.add(findingWrapper);
            entry.getValue().withFinding(findingWrapper);
          }

          /// RUNNING simulation with no findings
          attachSimulationToScenario(
              scenarioWrapper, ExerciseFixture.createRunningAttackExercise());
          scenarioWrapper.persist();
        }

        // add injects (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withFinding(findingWrapper)
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toFindingOutput)
                .limit(input.getSize())
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
      @DisplayName("Returns only findings for latest simulation")
      public void ReturnsOnlyFindingsForLatestSimulation() throws Exception {
        ScenarioComposer.Composer scenarioWrapper = getScenarioWithSimulationsWrapper();

        // latest findings
        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();

        // add latest simulation to scenario
        Hashtable<String, InjectComposer.Composer> latestSimulationInjectWrappers =
            attachSimulationToScenario(
                scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());
        for (Map.Entry<String, InjectComposer.Composer> entry :
            latestSimulationInjectWrappers.entrySet()) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          entry.getValue().withFinding(findingWrapper);
        }
        scenarioWrapper.persist();

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/scenarios/" + scenarioWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toFindingOutput)
                .limit(input.getSize())
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

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(FINDING_URI + "/exercises/" + ex.getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        ex.getInjects().stream()
                            .flatMap(inject -> inject.getFindings().stream().map(Finding::getId))
                            .toList()))
                .stream()
                .map(findingMapper::toFindingOutput)
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

        List<FindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        inject.getFindings().stream().map(Finding::getId).toList()))
                .stream()
                .map(findingMapper::toFindingOutput)
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
      @DisplayName("Returns all findings for latest simulations involving endpoint")
      public void ReturnsAllFindingsForLatestSimulationsInvolvingEndpoint() throws Exception {
        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        ScenarioComposer.Composer scenarioWrapper = getScenarioWithSimulationsWrapper();

        // hack findings to attach to endpoint
        for (Exercise ex : scenarioWrapper.get().getExercises()) {
          for (Inject inject : ex.getInjects()) {
            for (Finding finding : inject.getFindings()) {
              finding.setAssets(List.of(endpointWrapper.get()));
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
                  .forFinding(FindingFixture.createDefaultTextFindingWithRandomValue())
                  .withEndpoint(endpointWrapper);
          entry.getValue().withFinding(findingWrapper);
          latestFindingWrappers.add(findingWrapper);
        }
        scenarioWrapper.persist();

        // add injects (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withFinding(findingWrapper.withEndpoint(endpointWrapper))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/endpoints/" + endpointWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }

      @Test
      @DisplayName(
          "Returns all unsolved findings for latest finished simulations involving endpoint")
      public void ReturnsAllUnsolvedFindingsForLatestFinishedSimulationsInvolvingEndpoint()
          throws Exception {
        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        ScenarioComposer.Composer scenarioWrapper = getScenarioWithSimulationsWrapper();

        // hack findings to attach to endpoint
        for (Exercise ex : scenarioWrapper.get().getExercises()) {
          for (Inject inject : ex.getInjects()) {
            for (Finding finding : inject.getFindings()) {
              finding.setAssets(List.of(endpointWrapper.get()));
            }
          }
        }

        List<FindingComposer.Composer> latestFindingWrappers = new ArrayList<>();
        // add finished simulation to scenario with no findings (= all previous findings solved)
        attachSimulationToScenario(scenarioWrapper, ExerciseFixture.createFinishedAttackExercise());

        scenarioWrapper.persist();

        // add injects (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withFinding(findingWrapper.withEndpoint(endpointWrapper))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/endpoints/" + endpointWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }

      @Test
      @DisplayName("Returns all findings for latest finished simulations involving endpoint")
      public void ReturnsAllFindingsForLatestFinishedSimulationsInvolvingEndpoint()
          throws Exception {
        EndpointComposer.Composer endpointWrapper =
            endpointComposer.forEndpoint(EndpointFixture.createEndpoint()).persist();
        ScenarioComposer.Composer scenarioWrapper = getScenarioWithSimulationsWrapper();

        // hack findings to attach to endpoint
        for (Exercise ex : scenarioWrapper.get().getExercises()) {
          for (Inject inject : ex.getInjects()) {
            for (Finding finding : inject.getFindings()) {
              finding.setAssets(List.of(endpointWrapper.get()));
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
                  .forFinding(FindingFixture.createDefaultTextFindingWithRandomValue())
                  .withEndpoint(endpointWrapper);
          entry.getValue().withFinding(findingWrapper);
          latestFindingWrappers.add(findingWrapper);
        }

        attachSimulationToScenario(scenarioWrapper, ExerciseFixture.createRunningAttackExercise());

        scenarioWrapper.persist();

        // add injects (atomic testing) with findings too
        for (int i = 0; i < 2; i++) {
          FindingComposer.Composer findingWrapper =
              findingComposer.forFinding(FindingFixture.createDefaultTextFindingWithRandomValue());
          latestFindingWrappers.add(findingWrapper);
          injectComposer
              .forInject(InjectFixture.getDefaultInject())
              .withFinding(findingWrapper.withEndpoint(endpointWrapper))
              .persist();
        }

        SearchPaginationInput input = PaginationFixture.getDefault().build();

        entityManager.flush();
        entityManager.clear();

        String response =
            performCallbackRequest(
                    FINDING_URI + "/endpoints/" + endpointWrapper.get().getId() + "/search", input)
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<FindingOutput> expectedFindings =
            fromIterable(
                    findingRepository.findAllById(
                        latestFindingWrappers.stream()
                            .map(wrapper -> wrapper.get().getId())
                            .toList()))
                .stream()
                .map(findingMapper::toFindingOutput)
                .limit(input.getSize())
                .toList();

        assertThatJson(response)
            .when(Option.IGNORING_ARRAY_ORDER)
            .node("content")
            .isEqualTo(mapper.writeValueAsString(expectedFindings));
      }
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
              "Text",
              savedFinding,
              savedSimulation,
              savedScenario,
              savedEndpoint,
              savedAssetGroup);

      entityManager.flush();
      entityManager.clear();

      performCallbackRequest(FINDING_URI + "/search", input)
          .andExpect(jsonPath("$.content.[0].finding_type").value(savedFinding.getType().label))
          .andExpect(jsonPath("$.content.[0].finding_name").value("Text"))
          .andExpect(jsonPath("$.content.[0].finding_field").value(TEXT_FIELD))
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
              ContractOutputType.IPv6,
              "IPv6",
              savedFinding,
              savedSimulation,
              null,
              savedEndpoint,
              null);

      performCallbackRequest(
              FINDING_URI + "/exercises/" + savedSimulation.getId() + "/search", input)
          .andExpect(jsonPath("$.content.[0].finding_type").value(savedFinding.getType().label))
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
              "Credentials",
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
          .andExpect(jsonPath("$.content.[0].finding_type").value(savedFinding.getType().label))
          .andExpect(jsonPath("$.content.[0].finding_value").value("admin:admin"));
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
              ContractOutputType.Text, "Text", savedFinding, null, null, savedEndpoint, null);

      performCallbackRequest(FINDING_URI + "/endpoints/" + savedEndpoint.getId() + "/search", input)
          .andExpect(
              jsonPath("$.content.[0].finding_assets.[0].asset_id").value(savedEndpoint.getId()))
          .andExpect(jsonPath("$.content.[0].finding_type").value(savedFinding.getType().label))
          .andExpect(jsonPath("$.content.[0].finding_value").value("text_value"));
    }
  }

  private SearchPaginationInput buildDefaultFilters(
      ContractOutputType type,
      String name,
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

    filters.add(buildFilter("finding_type", Filters.FilterOperator.contains, List.of(type.label)));
    filters.add(buildFilter("finding_name", Filters.FilterOperator.contains, List.of(name)));
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
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is2xxSuccessful());
  }
}
