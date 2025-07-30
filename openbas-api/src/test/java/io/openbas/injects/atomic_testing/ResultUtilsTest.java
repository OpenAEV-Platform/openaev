package io.openbas.injects.atomic_testing;

import static io.openbas.expectation.ExpectationType.DETECTION;
import static io.openbas.expectation.ExpectationType.HUMAN_RESPONSE;
import static io.openbas.expectation.ExpectationType.PREVENTION;
import static io.openbas.expectation.ExpectationType.VULNERABILITY;
import static io.openbas.utils.fixtures.ExpectationResultByTypeFixture.createDefaultExpectationResultsByType;
import static io.openbas.utils.fixtures.RawInjectExpectationFixture.createDefaultInjectExpectation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import io.openbas.database.model.InjectExpectation;
import io.openbas.database.raw.RawInjectExpectation;
import io.openbas.database.repository.InjectExpectationRepository;
import io.openbas.database.repository.InjectRepository;
import io.openbas.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openbas.utils.InjectUtils;
import io.openbas.utils.ResultUtils;
import io.openbas.utils.mapper.InjectExpectationMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResultUtilsTest {

  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private InjectUtils injectUtils;

  private InjectExpectationMapper injectExpectationMapper;
  private ResultUtils resultUtils;

  @BeforeEach
  void before() {
    injectExpectationMapper = new InjectExpectationMapper(injectRepository, injectUtils);
    resultUtils = new ResultUtils(injectExpectationRepository, injectExpectationMapper);
  }

  @Test
  @DisplayName("Should get calculated global scores for injects")
  void getExercisesGlobalScores() {
    String injectId1 = "103da74a-055b-40e2-a934-9605cd3e4191";
    String injectId2 = "1838c23d-3bbe-4d8e-ba40-aa8b5fd1614d";

    Set<String> injectIds = Set.of(injectId1, injectId2);

    List<RawInjectExpectation> expectations =
        List.of(
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.PREVENTION.toString(), 100.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.DETECTION.toString(), 100.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.VULNERABILITY.toString(), 100.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.MANUAL.toString(), 0.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.PREVENTION.toString(), 50.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.DETECTION.toString(), 100.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.VULNERABILITY.toString(), 100.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.MANUAL.toString(), 0.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.PREVENTION.toString(), 0.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.DETECTION.toString(), 100.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.VULNERABILITY.toString(), 100.0, 100.0),
            createDefaultInjectExpectation(
                InjectExpectation.EXPECTATION_TYPE.MANUAL.toString(), 0.0, 100.0));
    when(injectExpectationRepository.rawForComputeGlobalByInjectIds(injectIds))
        .thenReturn(expectations);

    var result = resultUtils.computeGlobalExpectationResults(injectIds);

    ExpectationResultsByType expectedPreventionResult =
        createDefaultExpectationResultsByType(
            PREVENTION, InjectExpectation.EXPECTATION_STATUS.PARTIAL, 1, 0, 1, 1);
    ExpectationResultsByType expectedDetectionResult =
        createDefaultExpectationResultsByType(
            DETECTION, InjectExpectation.EXPECTATION_STATUS.SUCCESS, 3, 0, 0, 0);
    ExpectationResultsByType expectedVulnerabilityResult =
        createDefaultExpectationResultsByType(
            VULNERABILITY, InjectExpectation.EXPECTATION_STATUS.SUCCESS, 3, 0, 0, 0);
    ExpectationResultsByType expectedHumanResponseResult =
        createDefaultExpectationResultsByType(
            HUMAN_RESPONSE, InjectExpectation.EXPECTATION_STATUS.FAILED, 0, 0, 0, 3);

    List<ExpectationResultsByType> expectedPreventionResult1 =
        List.of(
            expectedPreventionResult,
            expectedDetectionResult,
            expectedVulnerabilityResult,
            expectedHumanResponseResult);

    assertEquals(expectedPreventionResult1, result);
  }
}
