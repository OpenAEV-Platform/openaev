package io.openaev.rest.scenario.service;

import static io.openaev.expectation.ExpectationType.*;
import static io.openaev.utils.fixtures.RawFinishedExerciseWithInjectsFixture.createDefaultRawFinishedExerciseWithInjects;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import io.openaev.IntegrationTest;
import io.openaev.database.raw.RawFinishedExerciseWithInjects;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.expectation.ExpectationType;
import io.openaev.rest.scenario.response.GlobalScoreBySimulationEndDate;
import io.openaev.rest.scenario.response.ScenarioStatistic;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.fixtures.ExpectationResultsByTypeFixture;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScenarioStatisticServiceTest extends IntegrationTest {
  @Mock private ExerciseRepository exerciseRepository;
  @Mock private ResultUtils resultUtils;

  @InjectMocks private ScenarioStatisticService scenarioStatisticService;

  @BeforeEach
  void setUp() {
    scenarioStatisticService = new ScenarioStatisticService(exerciseRepository, resultUtils);
  }

  @Test
  @DisplayName("Should get scenario statistics")
  void getScenarioStatistics() {
    String scenarioId = "e6773fee-b901-47af-8050-033b4d387fb6";
    String injectId1 = "103da74a-055b-40e2-a934-9605cd3e4191";
    String injectId2 = "1838c23d-3bbe-4d8e-ba40-aa8b5fd1614d";
    String injectId3 = "0f728b68-ec1f-4a5d-a2e5-53d897c7a7fd";
    String injectId4 = "92b0531d-b32f-4a22-9bfd-65c773c30e61";

    Set<String> exercise1InjectIds = Set.of(injectId1, injectId2, injectId3);
    Set<String> exercise2InjectIds = Set.of(injectId4);

    Instant exercise1EndDate = Instant.parse("2023-12-12T10:15:30.00Z");
    Instant exercise2EndDate = Instant.parse("2023-12-10T11:15:30.00Z");

    RawFinishedExerciseWithInjects rawFinishedExerciseWithInjects1 =
        createDefaultRawFinishedExerciseWithInjects(exercise1EndDate, exercise1InjectIds);
    RawFinishedExerciseWithInjects rawFinishedExerciseWithInjects2 =
        createDefaultRawFinishedExerciseWithInjects(exercise2EndDate, exercise2InjectIds);

    when(exerciseRepository.rawLatestFinishedExercisesWithInjectsByScenarioId(scenarioId))
        .thenReturn(List.of(rawFinishedExerciseWithInjects1, rawFinishedExerciseWithInjects2));

    when(resultUtils.computeGlobalExpectationResults(exercise1InjectIds))
        .thenReturn(ExpectationResultsByTypeFixture.exercise1GlobalScores);
    when(resultUtils.computeGlobalExpectationResults(exercise2InjectIds))
        .thenReturn(ExpectationResultsByTypeFixture.exercise2GlobalScores);

    ScenarioStatistic result = scenarioStatisticService.getStatistics(scenarioId);

    Map<ExpectationType, List<GlobalScoreBySimulationEndDate>> expected =
        Map.of(
            ExpectationType.PREVENTION,
            List.of(
                new GlobalScoreBySimulationEndDate(exercise2EndDate, 0),
                new GlobalScoreBySimulationEndDate(exercise1EndDate, 33.4F)),
            DETECTION,
            List.of(
                new GlobalScoreBySimulationEndDate(exercise2EndDate, 0),
                new GlobalScoreBySimulationEndDate(exercise1EndDate, 100)));
    assertEquals(expected, result.simulationsResultsLatest().globalScoresByExpectationType());
  }
}
