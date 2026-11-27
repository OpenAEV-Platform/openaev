package io.openaev.service.expectation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.LoggingInjectExpectation;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.database.repository.InjectExpectationResultRepository;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.service.InjectService;
import org.springframework.stereotype.Component;

/** Behavior implementation for {@link LoggingInjectExpectation}. */
@Component
public class LoggingBehavior extends AbstractTechnicalBehavior {

  public LoggingBehavior(
      CollectorService collectorService,
      InjectService injectService,
      InjectExpectationRepository injectExpectationRepository,
      InjectExpectationResultRepository injectExpectationResultRepository) {
    super(
        collectorService,
        injectService,
        injectExpectationRepository,
        injectExpectationResultRepository);
  }

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof LoggingInjectExpectation;
  }

  @Override
  public boolean supportsFormExpectationType(BaseInjectExpectation.EXPECTATION_TYPE type) {
    return type == BaseInjectExpectation.EXPECTATION_TYPE.LOGGING;
  }

  @Override
  protected TechnicalInjectExpectation newTechnicalExpectation() {
    return new LoggingInjectExpectation();
  }
}
