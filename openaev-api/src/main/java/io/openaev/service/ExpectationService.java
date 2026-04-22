package io.openaev.service;

import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.*;
import static io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE.VULNERABILITY;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.model.inject.form.Expectation;
import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ExpectationService {

  private final ExpectationBuilderService expectationBuilderService;

  @Resource protected ObjectMapper mapper;

  /**
   * Build available expectations from predefined expectations and inject type.
   *
   * @param predefinedExpectations predefined expectations already present in the contract
   * @param isHumanInject true when inject is human-oriented, false for technical injects
   * @return merged available expectations without duplicate default types
   */
  public List<Expectation> buildAvailableExpectationsForInject(
      List<Expectation> predefinedExpectations, boolean isHumanInject) {
    List<Expectation> availableExpectations = new ArrayList<>(predefinedExpectations);
    if (isHumanInject) {
      if (availableExpectations.stream()
          .noneMatch(expectation -> expectation.getType().equals(MANUAL))) {
        availableExpectations.add(expectationBuilderService.buildManualExpectation());
      }

      return availableExpectations;
    }

    if (availableExpectations.stream()
        .noneMatch(expectation -> expectation.getType().equals(DETECTION))) {
      availableExpectations.add(expectationBuilderService.buildDetectionExpectation());
    }
    if (availableExpectations.stream()
        .noneMatch(expectation -> expectation.getType().equals(PREVENTION))) {
      availableExpectations.add(expectationBuilderService.buildPreventionExpectation());
    }
    if (availableExpectations.stream()
        .noneMatch(expectation -> expectation.getType().equals(VULNERABILITY))) {
      availableExpectations.add(expectationBuilderService.buildVulnerabilityExpectation());
    }
    return availableExpectations;
  }
}
