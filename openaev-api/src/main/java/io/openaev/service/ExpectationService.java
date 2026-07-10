package io.openaev.service;

import io.openaev.expectation.ExpectationBuilderService;
import io.openaev.model.inject.form.Expectation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ExpectationService {

  private final ExpectationBuilderService expectationBuilderService;

  /**
   * Build the full set of available expectations for a technical inject.
   *
   * <p>Technical injects always expose the three standard technical expectation types (DETECTION,
   * PREVENTION, VULNERABILITY) as available choices, regardless of which subset is predefined on
   * the payload.
   *
   * @return the fixed list of technical available expectations
   */
  public List<Expectation> buildAvailableExpectationsForTechnicalInject() {
    return List.of(
        expectationBuilderService.buildDetectionExpectation(),
        expectationBuilderService.buildPreventionExpectation(),
        expectationBuilderService.buildVulnerabilityExpectation());
  }
}
