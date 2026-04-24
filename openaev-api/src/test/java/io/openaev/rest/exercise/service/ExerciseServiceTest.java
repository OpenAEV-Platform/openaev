package io.openaev.rest.exercise.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.database.model.*;
import io.openaev.database.model.Exercise;
import io.openaev.database.repository.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.inject.service.InjectDuplicateService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.*;
import io.openaev.service.FileService;
import io.openaev.service.LessonsService;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.scenario.ScenarioRecurrenceService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.fixtures.*;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ScenarioFixture;
import io.openaev.utils.fixtures.composers.ExerciseComposer;
import io.openaev.utils.fixtures.composers.ScenarioComposer;
import io.openaev.utils.mapper.ExerciseMapper;
import io.openaev.utils.mapper.InjectExpectationMapper;
import io.openaev.utils.mapper.InjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

@Transactional
class ExerciseServiceTest extends IntegrationTest {

  @Autowired private ScenarioComposer scenarioComposer;
  @Autowired private ExerciseComposer exerciseComposer;
  @Autowired private EntityManager entityManager;
  @Autowired private ExerciseService actualExerciseService;

  @Autowired private LessonsService lessonsService;
  @Autowired private FileService fileService;
  @Autowired private PauseExerciseService pauseExerciseService;
  @Autowired private ScenarioRecurrenceService scenarioRecurrenceService;

  @Mock private EnterpriseEditionService enterpriseEditionService;
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
  @Mock private PauseRepository pauseRepository;
  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @Mock private LessonsQuestionRepository lessonsQuestionRepository;
  @Mock private LessonsAnswerRepository lessonsAnswerRepository;
  @Mock private ExerciseTeamUserRepository exerciseTeamUserRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private LessonsCategoryRepository lessonsCategoryRepository;
  @Mock private PreviewFeatureService previewFeatureService;
  @Mock private WorkflowService workflowService;
  @Mock private GrantService grantService;
  @Mock private ExerciseTeamUserService exerciseTeamUserService;
  @Mock private io.openaev.healthcheck.utils.HealthCheckUtils healthCheckUtils;

  @Mock private InjectExpectationMapper injectExpectationMapper;

  @InjectMocks private ExerciseService mockedExerciseService;
  @Autowired private InjectStatusRepository injectStatusRepository;
  @Autowired private StepService stepService;

  @BeforeEach
  void setUp() {
    mockedExerciseService =
        new ExerciseService(
            enterpriseEditionService,
            injectDuplicateService,
            teamService,
            variableService,
            tagRuleService,
            documentService,
            injectService,
            userService,
            grantService,
            exerciseTeamUserService,
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
            injectStatusRepository,
            pauseRepository,
            lessonsQuestionRepository,
            teamRepository,
            userRepository,
            exerciseTeamUserRepository,
            injectRepository,
            lessonsAnswerRepository,
            lessonsCategoryRepository,
            lessonsService,
            injectExpectationMapper,
            scenarioRecurrenceService,
            workflowService,
            previewFeatureService,
            pauseExerciseService,
            fileService,
            stepService,
            healthCheckUtils);

    scenarioComposer.reset();
    exerciseComposer.reset();
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
}
