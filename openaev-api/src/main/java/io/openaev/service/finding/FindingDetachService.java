package io.openaev.service.finding;

import io.openaev.database.repository.FindingRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Detaches findings from the injects, simulations or scenarios about to be deleted, so they survive
 * the delete (opt-in keep_findings on the delete endpoints).
 *
 * <p>Every method runs only inside the caller's transaction ({@link Propagation#MANDATORY}): the
 * delete entry points of ExerciseService, ScenarioService and InjectService, whose transaction is
 * already scoped to the request's tenant. It never opens a transaction of its own, so it never
 * reaches the findings table without that scope.
 */
@Service
@RequiredArgsConstructor
public class FindingDetachService {

  private final FindingRepository findingRepository;

  /** Detaches the findings of the given injects. */
  @Transactional(propagation = Propagation.MANDATORY)
  public int detachFromInjects(List<String> injectIds) {
    return findingRepository.detachFromInjects(injectIds);
  }

  /** Detaches the findings of the injects of the given simulations. */
  @Transactional(propagation = Propagation.MANDATORY)
  public int detachFromSimulations(List<String> simulationIds) {
    return findingRepository.detachFromSimulations(simulationIds);
  }

  /** Detaches the findings of the injects of the given scenarios. */
  @Transactional(propagation = Propagation.MANDATORY)
  public int detachFromScenarios(List<String> scenarioIds) {
    return findingRepository.detachFromScenarios(scenarioIds);
  }
}
