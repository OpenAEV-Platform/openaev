package io.openaev.service.expectation;

import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.database.model.TechnicalInjectExpectation;
import io.openaev.database.repository.InjectExpectationRepository;
import io.openaev.rest.collector.service.CollectorService;
import io.openaev.rest.inject.service.InjectService;
import org.springframework.stereotype.Component;

/** Behavior implementation for {@link PreventionInjectExpectation}. */
@Component
public class PreventionBehavior extends AbstractTechnicalBehavior {

  public PreventionBehavior(
      CollectorService collectorService,
      InjectService injectService,
      InjectExpectationRepository injectExpectationRepository) {
    super(collectorService, injectService, injectExpectationRepository);
  }

  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof PreventionInjectExpectation;
  }

  @Override
  public boolean supportsFormExpectationType(BaseInjectExpectation.EXPECTATION_TYPE type) {
    return type == BaseInjectExpectation.EXPECTATION_TYPE.PREVENTION;
  }

  @Override
  protected TechnicalInjectExpectation newTechnicalExpectation() {
    return new PreventionInjectExpectation();
  }
}
