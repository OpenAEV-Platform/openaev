package io.openaev.service.expectation;
import io.openaev.database.model.BaseInjectExpectation;
import io.openaev.database.model.PreventionInjectExpectation;
import io.openaev.rest.collector.service.CollectorService;
import org.springframework.stereotype.Component;
/**
 * Behavior implementation for {@link PreventionInjectExpectation}.
 *
 * <p>Handles the full lifecycle of a prevention expectation: leaf result computation,
 * result initialization from security-platform collectors, and score propagation
 * up the agent -> asset -> asset-group hierarchy.
 *
 * <p>All logic is inherited from {@link AbstractTechnicalBehavior}; this class only
 * declares which expectation type it owns via {@link #supports(BaseInjectExpectation)}.
 *
 * <p><strong>Dead code -- not wired into any service yet.</strong>
 */
@Component
public class PreventionBehavior extends AbstractTechnicalBehavior {
  public PreventionBehavior(CollectorService collectorService) {
    super(collectorService);
  }
  @Override
  public boolean supports(BaseInjectExpectation expectation) {
    return expectation instanceof PreventionInjectExpectation;
  }
}
