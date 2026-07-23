package io.openaev.rest.inject.service;

import io.openaev.database.audit.IndexEvent;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.repository.InjectRepository;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Compensates database-level {@code ON DELETE CASCADE} chains that bypass the JPA lifecycle.
 *
 * <p>{@code injects.inject_injector_contract} references {@code injectors_contracts} with {@code ON
 * DELETE CASCADE}, and {@code injectors_contracts.injector_contract_payload} references {@code
 * payloads} the same way. Deleting an injector contract (directly, via a contract re-sync, or
 * transitively via a payload delete) therefore silently deletes its injects at the database level:
 * no {@code @PostRemove} fires for those injects, so without explicit compensation the search
 * engine would keep their documents — and, through the dependency cascade, the dependent
 * expectation and finding documents — forever, inflating every ES-backed statistic (coverage tiles,
 * dashboards, KPIs).
 *
 * <p>Usage contract: collect the doomed inject ids BEFORE the delete, run the delete, then notify.
 * The engine listener batches the events per transaction and flushes them after commit.
 */
@Service
@RequiredArgsConstructor
public class InjectIndexCleanupService {

  private final InjectRepository injectRepository;
  private final ApplicationEventPublisher eventPublisher;

  /** Inject ids that will be cascade-deleted when the given contracts are deleted. */
  public List<String> injectIdsByContractIds(Collection<String> contractIds) {
    if (contractIds == null || contractIds.isEmpty()) {
      return List.of();
    }
    return injectRepository.findInjectIdsByInjectorContractIds(contractIds);
  }

  /** Inject ids that will be cascade-deleted when the given payload is deleted. */
  public List<String> injectIdsByPayloadId(String payloadId) {
    return injectRepository.findInjectIdsByPayloadId(payloadId);
  }

  /**
   * Publishes a delete index event per inject: the engine cascade also removes the dependent
   * expectation / finding documents referencing each inject id in their dependencies.
   */
  public void notifyEngineOfDeletedInjects(Collection<String> injectIds) {
    injectIds.forEach(
        id -> eventPublisher.publishEvent(new IndexEvent(ModelBaseListener.DATA_DELETE, id)));
  }
}
