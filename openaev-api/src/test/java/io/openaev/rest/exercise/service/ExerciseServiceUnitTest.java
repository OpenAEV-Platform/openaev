package io.openaev.rest.exercise.service;

import static io.openaev.utils.InjectExpectationResultUtils.getResultDetail;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.openaev.config.OpenAEVPrincipal;
import io.openaev.config.SessionHelper;
import io.openaev.config.cache.LicenseCacheManager;
import io.openaev.context.TenantContext;
import io.openaev.database.model.*;
import io.openaev.database.repository.*;
import io.openaev.ee.EnterpriseEditionService;
import io.openaev.expectation.ExpectationType;
import io.openaev.rest.document.DocumentService;
import io.openaev.rest.exception.ChainingException;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.exercise.form.ExercisesGlobalScoresInput;
import io.openaev.rest.inject.service.InjectDuplicateService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.*;
import io.openaev.service.chaining.WorkflowService;
import io.openaev.service.period.CronService;
import io.openaev.telemetry.metric_collectors.ActionMetricCollector;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.TargetType;
import io.openaev.utils.fixtures.AssetGroupFixture;
import io.openaev.utils.fixtures.ExerciseFixture;
import io.openaev.utils.fixtures.ExpectationResultsByTypeFixture;
import io.openaev.utils.fixtures.TagFixture;
import io.openaev.utils.mapper.ExerciseMapper;
import io.openaev.utils.mapper.InjectExpectationMapper;
import io.openaev.utils.mapper.InjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ExerciseServiceUnitTest {

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

  @Mock
  private io.openaev.service.attackpath.ingestion.AttackPathExecutionIngestionService
      attackPathExecutionService;

  @Mock private TeamRepository teamRepository;
  @Mock private UserRepository userRepository;
  @Mock private ExerciseTeamUserRepository exerciseTeamUserRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private LessonsCategoryRepository lessonsCategoryRepository;
  @Mock private CronService cronService;
  @Mock private InjectExpectationMapper injectExpectationMapper;

  @Mock private WorkflowService workflowService;
  @Mock private LessonsService lessonsService;
  @Mock private ApplicationEventPublisher eventPublisher;

  @Spy @InjectMocks private ExerciseService mockedExerciseService;

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
    exercise.setTags(new HashSet<>(Set.of(tag1, tag2)));
    Set<Tag> currentTags = new HashSet<>(Set.of(tag2, tag3));
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
    exercise.setTags(new HashSet<>(Set.of(tag1, tag2)));
    Set<Tag> currentTags = new HashSet<>(Set.of(tag2, tag3));
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
    exercise.setTags(new HashSet<>(Set.of(tag1, tag2)));
    Set<Tag> currentTags = new HashSet<>(Set.of(tag2, tag3));

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
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
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
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, lowerScores)));
    Map<ExpectationType, ExpectationResultsByType> secondLastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
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
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.HUMAN_RESPONSE,
            new ExpectationResultsByType(
                ExpectationType.HUMAN_RESPONSE,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, lowerScores)));
    Map<ExpectationType, ExpectationResultsByType> secondLastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.HUMAN_RESPONSE,
            new ExpectationResultsByType(
                ExpectationType.HUMAN_RESPONSE,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
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
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.HUMAN_RESPONSE,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                BaseInjectExpectation.EXPECTATION_STATUS.PENDING,
                getResultDetail(ExpectationType.PREVENTION, lowerScores)));
    Map<ExpectationType, ExpectationResultsByType> secondLastResultsMap =
        Map.of(
            ExpectationType.DETECTION,
            new ExpectationResultsByType(
                ExpectationType.DETECTION,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.DETECTION, scores)),
            ExpectationType.PREVENTION,
            new ExpectationResultsByType(
                ExpectationType.PREVENTION,
                BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS,
                getResultDetail(ExpectationType.PREVENTION, scores)));
  }

  private AssetGroup getAssetGroup(String name) {
    AssetGroup assetGroup = AssetGroupFixture.createDefaultAssetGroup(name);
    assetGroup.setId(name);
    return assetGroup;
  }

  /* ============================================================
   * createSimulation - chaining engine function
   * ============================================================ */
  @Nested
  class CreateSimulationChainingFunction {

    private Exercise exercise;

    @BeforeEach
    void setup() {
      exercise = mock(Exercise.class);
    }

    @ParameterizedTest(name = "chaining={0}")
    @ValueSource(booleans = {true, false})
    void shouldCreateSimulation_withOrWithoutChaining(boolean chaining) throws ChainingException {

      Exercise saved = mock(Exercise.class);
      doReturn(saved).when(mockedExerciseService).createExercise(exercise);
      Exercise result;
      if (chaining) {
        doNothing().when(workflowService).isPreviewFeatureChainingEnable();
        result = mockedExerciseService.createSimulationChaining(exercise);
      } else {
        result = mockedExerciseService.createExercise(exercise);
      }

      assertEquals(saved, result);
      verify(mockedExerciseService).createExercise(exercise);

      if (chaining) {
        verify(workflowService).creationWorkflow(saved);
      } else {
        verify(workflowService, never()).creationWorkflow(any(Exercise.class));
      }
    }
  }

  /* ============================================================
   * findById
   * ============================================================ */
  @Nested
  class FindById {

    private Exercise exercise;

    @BeforeEach
    void setup() {
      exercise = mock(Exercise.class);
    }

    @Test
    void shouldReturnExerciseWhenFound() {
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn("tenant-1");
        when(exerciseRepository.findByIdAndTenantId("id", "tenant-1"))
            .thenReturn(Optional.of(exercise));

        Exercise result = mockedExerciseService.findById("id");

        assertEquals(exercise, result);
      }
    }

    @Test
    void shouldThrowWhenNotFound() {
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn("tenant-1");
        when(exerciseRepository.findByIdAndTenantId("id", "tenant-1")).thenReturn(Optional.empty());

        assertThrows(ElementNotFoundException.class, () -> mockedExerciseService.findById("id"));
      }
    }
  }

  /* ============================================================
   * findAllById / save / delete
   * ============================================================ */
  @Nested
  class SimpleRepositoryDelegations {

    private Exercise exercise;

    @BeforeEach
    void setup() {
      exercise = mock(Exercise.class);
    }

    @Test
    void findAllById_shouldDelegate() {
      List<String> ids = List.of("1", "2");
      List<Exercise> list = List.of(exercise);

      when(exerciseRepository.findAllById(ids)).thenReturn(list);

      assertEquals(list, mockedExerciseService.findAllById(ids));
    }

    @Test
    void saveSimulation_shouldDelegate() {
      when(exerciseRepository.save(any())).thenAnswer(i -> i.getArgument(0));
      assertEquals(exercise, mockedExerciseService.saveSimulation(exercise));
    }

    @Test
    void deleteById_shouldCheckTenantAndDelegate() {
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class);
          MockedStatic<SessionHelper> sh = mockStatic(SessionHelper.class)) {
        // Arrange
        tc.when(TenantContext::getCurrentTenant).thenReturn("tenant-1");
        when(exerciseRepository.existsByIdAndTenantId("id", "tenant-1")).thenReturn(true);
        OpenAEVPrincipal principal = mock(OpenAEVPrincipal.class);
        when(principal.getId()).thenReturn("user-1");
        sh.when(SessionHelper::currentUser).thenReturn(principal);

        // Act
        mockedExerciseService.deleteById("id");

        // Assert
        verify(exerciseRepository).existsByIdAndTenantId("id", "tenant-1");
        verify(exerciseRepository).deleteById("id");
      }
    }

    @Test
    void deleteById_shouldThrowWhenExerciseNotInTenant() {
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        // Arrange
        tc.when(TenantContext::getCurrentTenant).thenReturn("tenant-1");
        when(exerciseRepository.existsByIdAndTenantId("id", "tenant-1")).thenReturn(false);

        // Act & Assert
        assertThrows(ElementNotFoundException.class, () -> mockedExerciseService.deleteById("id"));
        verify(exerciseRepository, never()).deleteById(anyString());
      }
    }
  }

  /* ============================================================
   * removeTeams
   * ============================================================ */
  @Nested
  class RemoveTeams {

    @Test
    void shouldRemoveTeamsFromAllAssociations() {
      String exerciseId = "exercise-123";
      List<String> teamIds = List.of("team-1", "team-2");

      mockedExerciseService.removeTeams(exerciseId, teamIds);

      verify(exerciseRepository).removeTeams(exerciseId, teamIds);
      verify(exerciseTeamUserRepository).deleteByExerciseIdAndTeamIds(exerciseId, teamIds);
      verify(injectService).removeTeamsForSimulation(exerciseId, teamIds);
      verify(lessonsService).removeTeamsForSimulation(exerciseId, teamIds);
      verify(teamService).find(any());
    }
  }

  /* ============================================================
   * throwIfExerciseNotLaunchable
   * ============================================================ */
  @Nested
  class ThrowIfExerciseNotLaunchable {

    @Test
    void shouldSkipValidationWhenLicenseIsActive() {
      Exercise exercise = mock(Exercise.class);
      when(enterpriseEditionService.isLicenseActive(any())).thenReturn(true);

      mockedExerciseService.throwIfExerciseNotLaunchable(exercise);

      verify(enterpriseEditionService).isLicenseActive(any());
      verify(exercise, never()).getInjects();
      verify(injectService, never()).throwIfInjectNotLaunchable(any());
    }

    @Test
    void shouldValidateInjectsWhenLicenseIsNotActive() {
      Exercise exercise = mock(Exercise.class);
      Inject inject1 = mock(Inject.class);
      Inject inject2 = mock(Inject.class);
      when(exercise.getInjects()).thenReturn(List.of(inject1, inject2));
      when(enterpriseEditionService.isLicenseActive(any())).thenReturn(false);

      mockedExerciseService.throwIfExerciseNotLaunchable(exercise);

      verify(enterpriseEditionService).isLicenseActive(any());
      verify(injectService).throwIfInjectNotLaunchable(inject1);
      verify(injectService).throwIfInjectNotLaunchable(inject2);
    }
  }

  /* ============================================================
   * replaceTeams
   * ============================================================ */
  @Nested
  class ReplaceTeams {

    @Test
    void shouldFullyRemoveDeselectedTeamAndEnableOnlyNewTeams() {
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn("tenant-1");
        String exerciseId = "exercise-123";

        Team existingTeam1 = new Team();
        existingTeam1.setId("team-1");
        existingTeam1.setUsers(new ArrayList<>());

        Team existingTeam2 = new Team();
        existingTeam2.setId("team-2");
        existingTeam2.setUsers(new ArrayList<>());

        User newPlayer = new User();
        newPlayer.setId("user-1");

        Team newTeam = new Team();
        newTeam.setId("team-3");
        newTeam.setUsers(List.of(newPlayer));

        Exercise exercise = new Exercise();
        exercise.setId(exerciseId);
        exercise.setTeams(new ArrayList<>(List.of(existingTeam1, existingTeam2)));

        when(exerciseRepository.findByIdAndTenantId(exerciseId, "tenant-1"))
            .thenReturn(Optional.of(exercise));
        when(teamRepository.findAllById(any()))
            .thenAnswer(
                invocation -> {
                  Iterable<String> ids = invocation.getArgument(0);
                  Map<String, Team> teamsById = Map.of("team-2", existingTeam2, "team-3", newTeam);
                  List<Team> result = new ArrayList<>();
                  ids.forEach(
                      id -> {
                        Team team = teamsById.get(id);
                        if (team != null) {
                          result.add(team);
                        }
                      });
                  return result;
                });
        when(userRepository.findById("user-1")).thenReturn(Optional.of(newPlayer));
        when(exerciseTeamUserRepository.existsByExerciseIdAndTeamIdAndUserId(
                exerciseId, "team-3", "user-1"))
            .thenReturn(false);
        when(teamService.find(any())).thenReturn(List.of());

        mockedExerciseService.replaceTeams(exerciseId, List.of("team-2", "team-3", "team-3"));

        verify(exerciseTeamUserRepository)
            .deleteByExerciseIdAndTeamIds(
                eq(exerciseId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));
        verify(injectRepository)
            .removeTeamsForExercise(
                eq(exerciseId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));
        verify(lessonsCategoryRepository)
            .removeTeamsForExercise(
                eq(exerciseId), argThat(ids -> ids.size() == 1 && ids.contains("team-1")));

        verify(exerciseTeamUserRepository)
            .existsByExerciseIdAndTeamIdAndUserId(exerciseId, "team-3", "user-1");
        verify(exerciseTeamUserRepository, never())
            .existsByExerciseIdAndTeamIdAndUserId(exerciseId, "team-2", "user-1");

        assertEquals(2, exercise.getTeams().size());
        assertTrue(exercise.getTeams().stream().anyMatch(team -> "team-2".equals(team.getId())));
        assertTrue(exercise.getTeams().stream().anyMatch(team -> "team-3".equals(team.getId())));
      }
    }

    @Test
    void shouldNotCallCleanupWhenNoTeamIsRemoved() {
      try (MockedStatic<TenantContext> tc = mockStatic(TenantContext.class)) {
        tc.when(TenantContext::getCurrentTenant).thenReturn("tenant-1");
        String exerciseId = "exercise-123";

        Team existingTeam = new Team();
        existingTeam.setId("team-1");
        existingTeam.setUsers(new ArrayList<>());

        Exercise exercise = new Exercise();
        exercise.setId(exerciseId);
        exercise.setTeams(new ArrayList<>(List.of(existingTeam)));

        when(exerciseRepository.findByIdAndTenantId(exerciseId, "tenant-1"))
            .thenReturn(Optional.of(exercise));
        when(teamRepository.findAllById(any())).thenReturn(List.of(existingTeam));
        when(teamService.find(any())).thenReturn(List.of());

        mockedExerciseService.replaceTeams(exerciseId, List.of("team-1"));

        verify(exerciseTeamUserRepository, never()).deleteByExerciseIdAndTeamIds(any(), any());
        verify(injectRepository, never()).removeTeamsForExercise(any(), any());
        verify(lessonsCategoryRepository, never()).removeTeamsForExercise(any(), any());
      }
    }
  }
}
