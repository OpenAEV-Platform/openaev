package io.openaev.service.targets.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.EndpointTarget;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectTarget;
import io.openaev.database.repository.ExecutionTraceRepository;
import io.openaev.database.repository.InjectRepository;
import io.openaev.service.InjectExpectationService;
import io.openaev.utils.InjectUtils;
import io.openaev.utils.fixtures.InjectExpectationFixture;
import io.openaev.utils.mapper.InjectExpectationMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HelperTargetSearchAdaptorTest {

  @Mock private InjectExpectationService injectExpectationService;
  @Mock private ExecutionTraceRepository executionTraceRepository;
  @Mock private InjectRepository injectRepository;
  @Mock private InjectUtils injectUtils;

  @Test
  @DisplayName("given a logged target should report a logging status apart from detection")
  void given_logged_target_should_report_logging_status() {
    // Arrange
    HelperTargetSearchAdaptor adaptor =
        new HelperTargetSearchAdaptor(
            injectExpectationService,
            new InjectExpectationMapper(injectRepository, new ObjectMapper(), injectUtils),
            executionTraceRepository);
    Inject inject = new Inject();
    inject.setId("inject-id");
    InjectTarget target = new EndpointTarget("endpoint-id", "endpoint", Set.of(), null);
    when(injectExpectationService.findMergedExpectationsByInjectAndTargetAndTargetType(
            "inject-id", "endpoint-id", "ASSETS"))
        .thenReturn(
            List.of(
                InjectExpectationFixture.createExpectationForTypeAndScoreAndExpectedScore(
                    BaseInjectExpectation.EXPECTATION_TYPE.LOGGING, 100.0, 100.0)));

    // Act
    InjectTarget result = adaptor.buildTargetWithExpectations(inject, () -> target, false);

    // Assert
    assertEquals(BaseInjectExpectation.EXPECTATION_STATUS.SUCCESS, result.getTargetLoggingStatus());
    assertNull(result.getTargetDetectionStatus());
  }
}
