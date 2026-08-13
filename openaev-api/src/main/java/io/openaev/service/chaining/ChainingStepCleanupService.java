package io.openaev.service.chaining;

import io.openaev.database.model.Step;
import io.openaev.database.repository.StepRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cascade-cleans chaining step templates when their injector contract - or the payload behind it -
 * is deleted from the threat arsenal.
 *
 * <p>A regular inject references its injector contract through the {@code inject_injector_contract}
 * foreign key, declared {@code ON DELETE CASCADE}, so the inject row dies with the contract (and
 * transitively with the payload) at the database level. A chaining step has no such link: the
 * contract is frozen as a JSON snapshot inside {@code step_data}, with no foreign key for the
 * database cascade to act on. Nothing therefore removed the authored logic-map nodes when their
 * contract disappeared - the Logic graph kept rendering the stale snapshot and the edit drawer
 * 404'd on the (now missing) live-contract fetch, leaving an un-runnable ghost step that also fails
 * the chain at run time.
 *
 * <p>This service is the application-level compensation for that gap, mirroring the inject
 * semantics ("the inject no longer makes sense", migration {@code V4_88}) for TEMPLATE steps. It is
 * wired into every path that removes a contract or payload ({@code PayloadService}, {@code
 * InjectorContractService}, {@code PhishingLandingPageService}), the same way {@code
 * InjectIndexCleanupService} compensates the search index for those same cascades.
 *
 * <p>Only TEMPLATE steps are swept: RUN steps carry immutable execution history and must survive
 * their contract's deletion, following the TEMPLATE-only rule of {@code
 * WorkflowScopeRuleCascadeListener}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChainingStepCleanupService {

  private final StepRepository stepRepository;
  private final ConditionService conditionService;

  /**
   * Deletes every TEMPLATE step - and the conditions left dangling by its removal - whose frozen
   * {@code step_data} references any of the given injector contract ids. No-op for a null or empty
   * input.
   *
   * <p>Each step is torn down the same way a manual logic-map delete is ({@link
   * ConditionService#deleteAllConditionsByStepId(String)} then the step itself), so edges are
   * unlinked and orphaned condition trees are pruned. The logic-map editability assertion is
   * deliberately skipped: this is a system-driven cascade triggered by a threat-arsenal deletion,
   * not a user edit, and must never be blocked by a workflow's editability state.
   *
   * @param injectorContractIds the deleted injector contract ids to sweep from chaining logic maps
   * @return the number of step templates removed
   */
  @Transactional
  public int deleteTemplateStepsByInjectorContractIds(Collection<String> injectorContractIds) {
    if (injectorContractIds == null || injectorContractIds.isEmpty()) {
      return 0;
    }
    List<Step> steps = stepRepository.findTemplateStepsByInjectorContractIds(injectorContractIds);
    for (Step step : steps) {
      conditionService.deleteAllConditionsByStepId(step.getId());
      stepRepository.delete(step);
    }
    if (!steps.isEmpty()) {
      log.debug(
          "[Chaining] Cascade-removed {} orphaned step template(s) referencing deleted injector"
              + " contract(s): {}",
          steps.size(),
          injectorContractIds);
    }
    return steps.size();
  }
}
