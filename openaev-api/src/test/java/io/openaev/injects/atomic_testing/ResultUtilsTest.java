package io.openaev.injects.atomic_testing;

import static io.openaev.expectation.ExpectationType.DETECTION;
import static io.openaev.expectation.ExpectationType.HUMAN_RESPONSE;
import static io.openaev.expectation.ExpectationType.PREVENTION;
import static io.openaev.expectation.ExpectationType.VULNERABILITY;
import static io.openaev.utils.fixtures.ExpectationResultByTypeFixture.createDefaultExpectationResultsByType;
import static io.openaev.utils.fixtures.RawInjectExpectationFixture.createDefaultInjectExpectation;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.IntegrationTest;
import io.openaev.database.model.InjectExpectation;
import io.openaev.database.raw.RawInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.utils.InjectExpectationResultUtils.ExpectationResultsByType;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.ResultUtils;
import io.openaev.utils.mapper.InjectExpectationMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResultUtilsTest extends IntegrationTest {

  @Mock private InjectExpectationRepository injectExpectationRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private ObjectMapper objectMapper;
  @Mock private InjectUtils injectUtils;

  private InjectExpectationMapper injectExpectationMapper;
  private ResultUtils resultUtils;

  @BeforeEach
  void before() {
    injectExpectationMapper =
        new InjectExpectationMapper(injectRepository, objectMapper, injectUtils);
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
