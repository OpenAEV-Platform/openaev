package io.openbas.rest.exercise.service;

import static io.openbas.utils.InjectExpectationResultUtils.getResultDetail;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openbas.IntegrationTest;
import io.openbas.config.cache.LicenseCacheManager;
import io.openbas.database.model.*;
import io.openbas.database.repository.*;
import io.openbas.ee.Ee;
import io.openbas.expectation.ExpectationType;
import io.openbas.rest.dashboard.DashboardService;
import io.openbas.rest.document.DocumentService;
import io.openbas.rest.exercise.form.ExercisesGlobalScoresInput;
import io.openbas.rest.inject.service.InjectDuplicateService;
import io.openbas.rest.inject.service.InjectService;
import io.openbas.service.GrantService;
import io.openbas.service.TagRuleService;
import io.openbas.service.TeamService;
import io.openbas.service.UserService;
import io.openbas.service.VariableService;
import io.openbas.service.cron.CronService;
import io.openbas.telemetry.metric_collectors.ActionMetricCollector;
import io.openbas.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openbas.utils.ResultUtils;
import io.openbas.utils.TargetType;
import io.openbas.utils.fixtures.*;
import io.openbas.utils.fixtures.composers.ExerciseComposer;
import io.openbas.utils.fixtures.composers.ScenarioComposer;
import io.openbas.utils.mapper.ExerciseMapper;
import io.openbas.utils.mapper.InjectExpectationMapper;
import io.openbas.utils.mapper.InjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional
@ExtendWith(MockitoExtension.class)
class ExerciseServiceTest extends IntegrationTest {

  @Mock private Ee eeService;
  @Mock private GrantService grantService;
  @Mock private InjectDuplicateService injectDuplicateService;
  @Mock private TeamService teamService;
  @Mock private VariableService variableService;
  @Mock private TagRuleService tagRuleService;
  @Mock private DocumentService documentService;
  @Mock private InjectService injectService;
  @Mock private UserService userService;

  @Mock private ExerciseMapper exerciseMapper;
  @Mock private InjectMapper injectMapper;
  @Mock private ResultUtils resultUtils;
  @Mock private ActionMetricCollector actionMetricCollector;
  @Mock private LicenseCacheManager licenseCacheManager;

  @Mock private AssetRepository assetRepository;
  @Mock private AssetGroupRepository assetGroupRepository;
  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private ArticleRepository articleRepository;
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @Mock private ExerciseTeamUserRepository exerciseTeamUserRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private LessonsCategoryRepository lessonsCategoryRepository;
  @Mock private CronService cronService;

  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private ExerciseService actualExerciseService;

  @Mock private DashboardService dashboardService;
  @Mock private InjectExpectationMapper injectExpectationMapper;

  @InjectMocks private ExerciseService mockedExerciseService;

  @BeforeEach
  void setUp() {
    mockedExerciseService =
        new ExerciseService(
            eeService,
            grantService,
            injectDuplicateService,
            teamService,
            variableService,
            tagRuleService,
            documentService,
            injectService,
            cronService,
            userService,
            dashboardService,
            exerciseMapper,
            injectMapper,
            resultUtils,
            actionMetricCollector,
            licenseCacheManager,
            assetRepository,
            assetGroupRepository,
            injectExpectationRepository,
            articleRepository,
            exerciseRepository,
            teamRepository,
            userRepository,
            exerciseTeamUserRepository,
            injectRepository,
            lessonsCategoryRepository,
            injectExpectationMapper);

    scenarioComposer.reset();
    exerciseComposer.reset();
  }

  @Test
  @DisplayName("Should get exercises global scores")
  void getExercisesGlobalScores() {
    String exerciseId1 = "3e95b1ea-8957-4452-b0f7-edf4003eaa98";
    String exerciseId2 = "c740797e-e34c-4066-a16c-a8baad9058f9";

    String injectId1 = "103da74a-055b-40e2-a934-9605cd3e4191";
    String injectId2 = "1838c23d-3bbe-4d8e-ba40-aa8b5fd1614d";
    String injectId3 = "0f728b68-ec1f-4a5d-a2e5-53d897c7a7fd";
    String injectId4 = "bf05a17a-af6b-4238-9c3e-296db7f07d00";

    Set<String> exercise1InjectIds = Set.of(injectId1, injectId2, injectId3);
    Set<String> exercise2InjectIds = Set.of(injectId4);

    when(exerciseRepository.findInjectsByExercise(exerciseId1)).thenReturn(exercise1InjectIds);
    when(exerciseRepository.findInjectsByExercise(exerciseId2)).thenReturn(exercise2InjectIds);

    when(resultUtils.computeGlobalExpectationResults(exercise1InjectIds))
        .thenReturn(ExpectationResultsByTypeFixture.exercise1GlobalScores);
    when(resultUtils.computeGlobalExpectationResults(exercise2InjectIds))
        .thenReturn(ExpectationResultsByTypeFixture.exercise2GlobalScores);

    var results =
        mockedExerciseService.getExercisesGlobalScores(
            new ExercisesGlobalScoresInput(List.of(exerciseId1, exerciseId2)));

    assertEquals(
        results.globalScoresByExerciseIds(),
        Map.of(
            exerciseId1, ExpectationResultsByTypeFixture.exercise1GlobalScores,
            exerciseId2, ExpectationResultsByTypeFixture.exercise2GlobalScores));
  }

  @Test
  public void testUpdateExercise_WITH_apply_rule_true() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    Inject inject1 = new Inject();
    inject1.setId("1");
    Inject inject2 = new Inject();
    inject1.setId("2");
    Exercise exercise = ExerciseFixture.getExercise(null);
    exercise.setInjects(List.of(inject1, inject2));
    exercise.setTags(Set.of(tag1, tag2));
    Set<Tag> currentTags = Set.of(tag2, tag3);
    List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

    when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
        .thenReturn(assetGroupsToAdd);
    when(exerciseRepository.save(exercise)).thenReturn(exercise);
    when(injectService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS))).thenReturn(true);

    mockedExerciseService.updateExercice(exercise, currentTags, true);

    exercise
        .getInjects()
        .forEach(
            inject ->
                verify(injectService)
                    .applyDefaultAssetGroupsToInject(inject.getId(), assetGroupsToAdd));
    verify(exerciseRepository).save(exercise);
  }

  @Test
  public void testUpdateExercise_WITH_apply_rule_true_and_manual_inject() {
    AssetGroup assetGroup1 = getAssetGroup("assetgroup1");
    AssetGroup assetGroup2 = getAssetGroup("assetgroup2");
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    Inject inject1 = new Inject();
    inject1.setId("1");
    Inject inject2 = new Inject();
    inject1.setId("2");
    Exercise exercise = ExerciseFixture.getExercise(null);
    exercise.setInjects(List.of(inject1, inject2));
    exercise.setTags(Set.of(tag1, tag2));
    Set<Tag> currentTags = Set.of(tag2, tag3);
    List<AssetGroup> assetGroupsToAdd = List.of(assetGroup1, assetGroup2);

    when(tagRuleService.getAssetGroupsFromTagIds(List.of(tag1.getId())))
        .thenReturn(assetGroupsToAdd);
    when(exerciseRepository.save(exercise)).thenReturn(exercise);
    when(injectService.canApplyTargetType(any(), eq(TargetType.ASSETS_GROUPS))).thenReturn(false);

    mockedExerciseService.updateExercice(exercise, currentTags, true);

    verify(injectService, never()).applyDefaultAssetGroupsToInject(any(), any());
    verify(exerciseRepository).save(exercise);
  }

  @Test
  public void testUpdateExercise_WITH_apply_rule_false() {
    Tag tag1 = TagFixture.getTag("Tag1");
    Tag tag2 = TagFixture.getTag("Tag2");
    Tag tag3 = TagFixture.getTag("Tag3");
    Inject inject1 = new Inject();
    inject1.setId("1");
    Inject inject2 = new Inject();
    inject1.setId("2");
    Exercise exercise = ExerciseFixture.getExercise(null);
    exercise.setInjects(List.of(inject1, inject2));
    exercise.setTags(Set.of(tag1, tag2));
    Set<Tag> currentTags = Set.of(tag2, tag3);

    when(exerciseRepository.save(exercise)).thenReturn(exercise);

    mockedExerciseService.updateExercice(exercise, currentTags, false);

    verify(injectService, never()).applyDefaultAssetGroupsToInject(any(), any());
  }

  @Test
  public void test_isThereAScoreDegradation_with_same_results() {
    List<Double> scores = List.of(1.0, 1.0, 0.0, 0.5);

    Map<ExpectationType, ExpectationResultsByType> resultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, scores)));

    assertFalse(mockedExerciseService.isThereAScoreDegradation(resultsMap, resultsMap));
  }

  @Test
  public void test_isThereAScoreDegradation_with_lower_result() {
    List<Double> scores = List.of(1.0, 1.0, 0.0, 0.5, 1.0);
    List<Double> lowerScores = List.of(1.0, 1.0, 0.0, 0.5, 0.0);

    Map<ExpectationType, ExpectationResultsByType> lastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, lowerScores)));
    Map<ExpectationType, ExpectationResultsByType> secondLastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, scores)));
    assertTrue(
        mockedExerciseService.isThereAScoreDegradation(lastResultsMap, secondLastResultsMap));
  }

  @Test
  public void test_isThereAScoreDegradation_WITH_manual_expectation() {
    List<Double> scores = List.of(1.0, 1.0, 0.0, 0.5, 1.0);
    List<Double> lowerScores = List.of(1.0, 1.0, 0.0, 0.5, 0.0);
    Map<ExpectationType, ExpectationResultsByType> lastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.HUMAN_RESPONSE,
            new ExpectationResultsByType(
                ExpectationType.HUMAN_RESPONSE,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, lowerScores)));
    Map<ExpectationType, ExpectationResultsByType> secondLastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.HUMAN_RESPONSE,
            new ExpectationResultsByType(
                ExpectationType.HUMAN_RESPONSE,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, scores)));
    assertFalse(
        mockedExerciseService.isThereAScoreDegradation(lastResultsMap, secondLastResultsMap));
  }

  @Test
  public void test_isThereAScoreDegradation_WITH_expectation_pending() {
    List<Double> scores = List.of(1.0, 1.0, 0.0, 0.5, 1.0);
    List<Double> lowerScores = List.of(1.0, 1.0, 0.0, 0.5, 0.0);
    Map<ExpectationType, ExpectationResultsByType> lastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.HUMAN_RESPONSE,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                InjectExpectation.EXPECTATION_STATUS.PENDING,
                getResultDetail(ExpectationType.PREVENTION, lowerScores)));
    Map<ExpectationType, ExpectationResultsByType> secondLastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                InjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, scores)));
  }

  @Nested
  @DisplayName("Tests for latest validity date")
  public class LatestValidityDate {
    @Nested
    @DisplayName("With recurring scenario")
    public class WithRecurringScenario {
      private ScenarioComposer.Composer scenarioWrapper;

      @BeforeEach
      public void setup() {
        scenarioWrapper =
            scenarioComposer.forScenario(
                ScenarioFixture.getScenarioWithRecurrence("56 43 10 * * *"));
      }

      @Nested
      @DisplayName("With successor Simulation")
      public class WithSuccessorSimulation {
        private ExerciseComposer.Composer successorSimulationWrapper;
        private final Instant successorSimulationStartTime = Instant.parse("2022-04-24T01:02:03Z");

        @BeforeEach
        public void setup() {
          Exercise successorFixture = ExerciseFixture.createDefaultExercise();
          successorFixture.setStart(successorSimulationStartTime);
          successorSimulationWrapper = exerciseComposer.forExercise(successorFixture);
        }

        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Is valid until successor start date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isPresent().get().isEqualTo(successorSimulationStartTime);
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }

      @Nested
      @DisplayName("Without successor Simulation")
      public class WithoutSuccessorSimulation {
        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Is valid until next scenario trigger")
          public void isValidUntilNextScenarioTrigger() {
            scenarioWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Instant expected = Instant.parse("2022-04-23T10:43:56Z");

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isPresent().get().isEqualTo(expected);
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }
    }

    @Nested
    @DisplayName("With non-recurring scenario")
    public class WithNonRecurringScenario {
      private ScenarioComposer.Composer scenarioWrapper;

      @BeforeEach
      public void setup() {
        scenarioWrapper = scenarioComposer.forScenario(ScenarioFixture.getScenario());
      }

      @Nested
      @DisplayName("With successor Simulation")
      public class WithSuccessorSimulation {
        private ExerciseComposer.Composer successorSimulationWrapper;
        private final Instant successorSimulationStartTime = Instant.parse("2022-04-24T01:02:03Z");

        @BeforeEach
        public void setup() {
          Exercise successorFixture = ExerciseFixture.createDefaultExercise();
          successorFixture.setStart(successorSimulationStartTime);
          successorSimulationWrapper = exerciseComposer.forExercise(successorFixture);
        }

        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Is valid until successor start date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isPresent().get().isEqualTo(successorSimulationStartTime);
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper
                .withSimulation(consideredSimulationWrapper)
                .withSimulation(successorSimulationWrapper)
                .persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }

      @Nested
      @DisplayName("Without successor Simulation")
      public class WithoutSuccessorSimulation {
        @Nested
        @DisplayName("When considered simulation has started")
        public class WhenConsideredSimulationHasStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;
          private final Instant consideredSimulationStartTime =
              Instant.parse("2022-04-23T01:02:03Z");

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredFixture.setStart(consideredSimulationStartTime);
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilNextScenarioTrigger() {
            scenarioWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }

        @Nested
        @DisplayName("When considered simulation has not started")
        public class WhenConsideredSimulationHasNotStarted {
          ExerciseComposer.Composer consideredSimulationWrapper;

          @BeforeEach
          public void setup() {
            Exercise consideredFixture = ExerciseFixture.createDefaultExercise();
            consideredSimulationWrapper = exerciseComposer.forExercise(consideredFixture);
          }

          @Test
          @DisplayName("Has no validity date")
          public void isValidUntilSuccessorStartDate() {
            scenarioWrapper.withSimulation(consideredSimulationWrapper).persist();
            entityManager.flush();
            scenarioComposer.generatedItems.forEach(e -> entityManager.refresh(e));
            exerciseComposer.generatedItems.forEach(e -> entityManager.refresh(e));

            Optional<Instant> latestValidity =
                actualExerciseService.getLatestValidityDate(consideredSimulationWrapper.get());
            assertThat(latestValidity).isEmpty();
          }
        }
      }
    }
  }

  private AssetGroup getAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup(name);
    assetGroup.setId(name);
    return assetGroup;
  }
}
