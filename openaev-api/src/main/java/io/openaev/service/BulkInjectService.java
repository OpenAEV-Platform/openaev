package io.openaev.service;

import io.openaev.context.BulkOperationContext;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Inject;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.inject.form.InjectBulkProcessingInput;
import io.openaev.rest.inject.form.InjectBulkUpdateInputs;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.utils.BulkOperationMonitor;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

/**
 * Orchestrates bulk inject operations (update / delete) with progress monitoring and stream-event
 * suppression. Delegates to {@link InjectService} for the actual DB work so that Spring's
 * {@code @Transactional} proxy is honoured (no self-invocation).
 */
@Service
@RequiredArgsConstructor
public class BulkInjectService {

  private final InjectService injectService;
  private final BulkOperationMonitor bulkOperationMonitor;

  public List<Inject> bulkUpdateWithMonitoring(InjectBulkUpdateInputs input) {
    List<Inject> injectsToUpdate = resolveTargets(input);
    String operationId = bulkOperationMonitor.start("update", "injects", injectsToUpdate.size());
    try {
      List<Inject> updated =
          BulkOperationContext.runSuppressed(
              () -> injectService.bulkUpdateInject(injectsToUpdate, input.getUpdateOperations()));
      bulkOperationMonitor.complete(operationId);
      return updated;
    } catch (RuntimeException e) {
      bulkOperationMonitor.fail(operationId);
      throw e;
    }
  }

  public List<Inject> bulkDeleteWithMonitoring(InjectBulkProcessingInput input) {
    List<Inject> injectsToDelete = resolveTargets(input);
    String operationId = bulkOperationMonitor.start("delete", "injects", injectsToDelete.size());
    try {
      List<String> injectIds = injectsToDelete.stream().map(Inject::getId).toList();
      BulkOperationContext.runSuppressed(
          () -> {
            injectService.deleteAllByIds(injectIds);
            return null;
          });
      bulkOperationMonitor.complete(operationId);
    } catch (RuntimeException e) {
      bulkOperationMonitor.fail(operationId);
      throw e;
    }
    return injectsToDelete;
  }

  /**
   * Resolves the injects targeted by the input within the caller's scope and grants. When the
   * targets are selected by explicit IDs, every requested ID must resolve; otherwise the whole
   * operation is aborted with a not-found error before any entity is touched, so a mixed
   * valid/invalid request can never be partially applied.
   */
  private List<Inject> resolveTargets(InjectBulkProcessingInput input) {
    List<Inject> injects =
        injectService.getInjectsAndCheckPermission(input, Grant.GRANT_TYPE.PLANNER);
    if (!CollectionUtils.isEmpty(input.getInjectIDsToProcess())) {
      Set<String> resolvedIds = injects.stream().map(Inject::getId).collect(Collectors.toSet());
      List<String> missingIds =
          input.getInjectIDsToProcess().stream().filter(id -> !resolvedIds.contains(id)).toList();
      if (!missingIds.isEmpty()) {
        throw new ElementNotFoundException(
            "Inject not found with id: " + String.join(", ", missingIds));
      }
    }
    return injects;
  }
}
