package io.openaev.service.expectation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.DetectionInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.service.InjectService;
import org.springframework.stereotype.Component;

/**
 * Behavior implementation for {@link DetectionInjectExpectation}.
 *
 * <p><strong>Dead code — not wired into any service yet.</strong> Part of the {@code
 * InjectExpectation} refactoring (Vertical 2).
 */
@Component
public class DetectionBehavior extends AbstractTechnicalBehavior {

  public DetectionBehavior(
      CollectorService collectorService,
      InjectService injectService,
      InjectExpectationRepository injectExpectationRepository) {
    super(collectorService, injectService, injectExpectationRepository);
  }

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof DetectionInjectExpectation;
  }
}
