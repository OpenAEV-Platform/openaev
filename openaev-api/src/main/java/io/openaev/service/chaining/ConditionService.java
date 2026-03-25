package io.openaev.service.chaining;

import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.database.model.*;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.ChainingException;
import jakarta.persistence.EntityNotFoundException;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class ConditionService {
  private final ConditionRepository conditionRepository;
  private final StepRepository stepRepository;
  private final QueueChainingService queueChainingService;
  private final StepDelayQueueService stepDelayQueueService;

  // -- CONDITION TREE CREATE --

  /**
   * Creates a condition tree from an {@link EventInput} payload.
   *
   * <p>The frontend payload is named event, but it is persisted as conditions only: one root
   * condition (AND/OR) carrying name/description and child conditions linked by parent ID.
   *
   * @param input the condition-tree creation payload
   * @return the persisted root {@link Condition}
   */
  public Condition createConditionTree(EventInput input) {
    Objects.requireNonNull(input, "input must not be null");

    List<ConditionCreateInput> conditionInputs = input.getConditions();
    // Find the root condition (the one without a parent)
    ConditionCreateInput rootInput = findRootConditionInput(conditionInputs);

    // Create root condition entity
    Condition root = new Condition();
    root.setWorkflowId(input.getWorkflowId());
    root.setName(input.getName());
    root.setDescription(input.getDescription());
    root.setType(rootInput.getType());
    // Resolve and set the source step of the root condition
    root.setStepFrom(resolveStepFrom(input.getStepFrom()));
    // Persist root condition first (needed, so children can reference it)
    root = conditionRepository.save(root);

    // Link all related steps to the root condition
    linkStepsToRoot(root, input.getStepIds());

    // Map to keep track of temporary IDs -> persisted Condition entities
    Map<String, Condition> saved = new HashMap<>();
    saved.put(rootInput.getTemporaryId(), root);

    // Prepare remaining conditions (all except root)
    List<ConditionCreateInput> remaining = new ArrayList<>(conditionInputs);
    remaining.remove(rootInput);

    // Persist the rest of the tree in dependency order (parents before children).
    persistConditionTreeNodes(remaining, saved, input.getWorkflowId());

    return root;
  }

  // -- CONDITION TREE READ --

  /**
   * Finds a condition tree root by its ID.
   *
   * @param conditionRootId the root condition ID
   * @return the root {@link Condition}
   */
  public Condition findConditionRootById(String conditionRootId) {
    return conditionRepository
        .findById(conditionRootId)
        .orElseThrow(
            () -> new EntityNotFoundException("Condition root not found: " + conditionRootId));
  }

  /**
   * Returns all condition tree roots for a given workflow (conditions with no parent).
   *
   * @param workflowId the workflow identifier
   * @return list of root conditions for that workflow
   */
  @Transactional(readOnly = true)
  public List<Condition> findConditionRootsByWorkflowId(String workflowId) {
    return conditionRepository.findAllByWorkflowIdAndConditionParentIsNull(workflowId);
  }

  /**
   * Returns all condition tree roots across workflows.
   *
   * @return list of all root conditions associated to a workflow
   */
  @Transactional(readOnly = true)
  public List<Condition> findAll() {
    return conditionRepository.findAll();
  }

  // -- CONDITION TREE UPDATE --

  /**
   * Replaces an existing condition tree: updates root metadata and rebuilds child conditions.
   *
   * @param conditionRootId the root condition ID to update
   * @param input the updated condition-tree payload
   * @return the updated root {@link Condition}
   */
  @Transactional(rollbackFor = Exception.class)
  public Condition updateConditionTree(String conditionRootId, EventInput input) {
    Objects.requireNonNull(input, "input must not be null");

    Condition root = findConditionRootById(conditionRootId);

    // Extract all condition inputs and identify the root input
    List<ConditionCreateInput> conditionInputs = new ArrayList<>(input.getConditions());
    ConditionCreateInput rootInput = findRootConditionInput(conditionInputs);

    // Update root metadata + type in one shot, then save once.
    root.setName(input.getName());
    root.setDescription(input.getDescription());
    root.setWorkflowId(input.getWorkflowId());
    root.setType(rootInput.getType());
    root.setStepFrom(resolveStepFrom(input.getStepFrom()));

    // Clear existing relationships (children and linked steps)
    // This enables a full rebuild strategy (replace instead of partial update)
    root.getConditionChildren().clear();
    root.getConditionSteps().clear();
    root = conditionRepository.saveAndFlush(root);

    // Re-link steps.
    linkStepsToRoot(root, input.getStepIds());

    // Prepare to rebuild the condition tree
    // Remove root from inputs to only process children
    conditionInputs.remove(rootInput);

    // Map to track temporary IDs -> persisted Condition entities
    Map<String, Condition> saved = new HashMap<>();
    saved.put(rootInput.getTemporaryId(), root);

    // Recreate the full condition tree
    persistConditionTreeNodes(conditionInputs, saved, input.getWorkflowId());

    return root;
  }

  // -- CONDITION TREE DELETE --
  /**
   * Deletes a condition tree root and all its children (cascade).
   *
   * @param conditionRootId the root condition ID
   */
  public void deleteConditionTree(String conditionRootId) {
    conditionRepository.deleteById(conditionRootId);
  }

  // -- CONDITION PERSISTENCE HELPERS --

  /**
   * Saves a condition.
   *
   * @param condition condition to persist
   * @return the saved condition
   */
  public Condition saveCondition(Condition condition) {
    return conditionRepository.save(condition);
  }

  /**
   * Saves multiple conditions.
   *
   * @param conditions conditions to persist
   * @return the saved conditions
   */
  public List<Condition> saveAllConditions(List<Condition> conditions) {
    return conditionRepository.saveAll(conditions);
  }

  /**
   * Retrieves all conditions associated with a step.
   *
   * @param stepId step identifier
   * @return list of conditions linked to the step
   */
  @Transactional(readOnly = true)
  public List<Condition> findAllConditionsByStepId(String stepId) {
    return conditionRepository.findAllByStep_Id(stepId);
  }

  /**
   * Deletes all provided conditions.
   *
   * @param conditions conditions to delete
   */
  public void deleteAllConditions(List<Condition> conditions) {
    conditionRepository.deleteAll(conditions);
  }

  /**
   * Deletes conditions linked to a given step. Rules: - Always remove the current condition-step
   * link for this step. - Delete the condition only if, after unlinking, it has no more
   * condition-step links and no stepFrom.
   *
   * @param stepId step identifier
   */
  public void deleteAllConditionsByStepId(String stepId) {
    List<Condition> conditions = findAllConditionsByStepId(stepId);
    if (conditions.isEmpty()) {
      return;
    }

    for (Condition condition : conditions) {
      condition.unlinkFromStep(stepId);
      Condition persisted = conditionRepository.save(condition);

      boolean hasNoStepLinks =
          persisted.getConditionSteps() == null || persisted.getConditionSteps().isEmpty();
      boolean hasNoStepFrom = persisted.getStepFrom() == null;
      if (hasNoStepLinks && hasNoStepFrom) {
        conditionRepository.delete(persisted);
      }
    }
  }

  // -- CONDITION EVALUATION --

  /**
   * Checks whether the condition is a time-based condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if the condition type is AFTER or BEFORE
   */
  public boolean isTimeCondition(Condition condition) {
    return switch (condition.getType()) {
      case ConditionType.AFTER, ConditionType.BEFORE -> true;
      default -> false;
    };
  }

  /**
   * Checks whether the condition is a mapper condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if the condition type is MAPPER
   */
  public boolean isMapperCondition(Condition condition) {
    return condition.getType() == ConditionType.MAPPER;
  }

  /**
   * @return null (todo: implement)
   */
  public Condition isMapperConditionValid(Condition condition, String input, String data) {
    return null;
  }

  /**
   * Checks whether the condition is a filter condition.
   *
   * @param condition condition to evaluate
   * @return {@code true} if it is not a time or mapper condition
   */
  public boolean isFilterCondition(Condition condition) {
    return switch (condition.getType()) {
      case ConditionType.AFTER, ConditionType.BEFORE, ConditionType.MAPPER -> false;
      default -> true;
    };
  }

  /**
   * @return null (todo: implement)
   */
  public Condition isFilterConditionValid(Condition condition, String input, String data) {
    return null;
  }

  /**
   * Evaluates a time condition against the current time.
   *
   * @param conditionTemplate the condition to evaluate
   * @param now current instant
   * @param goal target instant
   * @return {@code true} if the condition is valid
   */
  public Boolean isTimeConditionValid(Condition conditionTemplate, Instant now, Instant goal) {
    if (conditionTemplate.getType().equals(ConditionType.AFTER)) {
      return now.isAfter(goal);
    } else if (conditionTemplate.getType().equals(ConditionType.BEFORE)) {
      return now.isBefore(goal);
    }
    return false;
  }

  /**
   * Evaluates all conditions for a step template and returns valid ones for execution.
   *
   * @param nextStepTemplateToExecute the step to evaluate
   * @param input input data for the step
   * @param workflowRun the running workflow
   * @param stepService service to interact with steps
   * @return valid conditions, empty list if none required, or null if execution should be deferred
   */
  public List<Condition> checkCondition(
      Step nextStepTemplateToExecute, String input, Workflow workflowRun, StepService stepService)
      throws ChainingException {
    List<Condition> conditionTemplate =
        findAllConditionsByStepId(nextStepTemplateToExecute.getId());
    if (conditionTemplate == null || conditionTemplate.isEmpty()) return new ArrayList<>();

    List<Condition> conditionsExecution = new ArrayList<>();

    // Time conditions
    for (Condition condition : conditionTemplate.stream().filter(this::isTimeCondition).toList()) {
      Instant now = Instant.now();
      Instant start = workflowRun.getWorkflowCreatedAt();
      if (start == null) start = now;
      long value = Long.parseLong(condition.getValue());
      Instant goal = start.plus(value, ChronoUnit.MILLIS);

      if (isTimeConditionValid(condition, now, goal)) {
        conditionsExecution.add(Condition.executionOf(condition, goal));
        continue;
      }
      if (condition.getType().equals(ConditionType.AFTER)) {
        long delay = ChronoUnit.MILLIS.between(now, goal);

        stepDelayQueueService.pushStepTemplateIntoStepDelayQueue(
            nextStepTemplateToExecute, now, input, delay, workflowRun, goal);
        return null;
      }
    }

    // Filter conditions
    for (Condition condition :
        conditionTemplate.stream().filter(this::isFilterCondition).toList()) {
      Condition valid =
          isFilterConditionValid(condition, input, nextStepTemplateToExecute.getData());
      if (valid != null) conditionsExecution.add(valid);
    }

    // Mapper conditions
    for (Condition condition :
        conditionTemplate.stream().filter(this::isMapperCondition).toList()) {
      Condition valid =
          isMapperConditionValid(condition, input, nextStepTemplateToExecute.getData());
      if (valid != null) conditionsExecution.add(valid);
    }

    // StepFrom (DEPEND_ON) conditions
    for (Condition condition :
        conditionTemplate.stream().filter(c -> c.getStepFrom() != null).toList()) {
      String idStepFromTemplate = condition.getStepFrom().getId();
      List<Step> dependOnRan =
          stepService
              .findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
                  idStepFromTemplate, workflowRun.getId())
              .stream()
              .filter(s -> s.getOutput() != null)
              .toList();
      int stepExecutedCount =
          stepService.countExecutedStep(workflowRun.getId(), nextStepTemplateToExecute.getId());
      if (!dependOnRan.isEmpty()
          && stepExecutedCount < nextStepTemplateToExecute.getLimitExecution()) {
        conditionsExecution.add(isDependOn(idStepFromTemplate));
      } else {
        return null;
      }
    }

    return conditionsExecution;
  }

  /**
   * Creates a DEPEND_ON condition for a step template dependency.
   *
   * @param idStepFromTemplate identifier of the dependent step template
   * @return the DEPEND_ON condition
   */
  public Condition isDependOn(String idStepFromTemplate) {
    return Condition.dependOn(idStepFromTemplate);
  }

  /**
   * Links existing condition roots to a step via the conditions_steps join table.
   *
   * @param step the step to link
   * @param conditionRootIds IDs of existing root conditions to link; ignored if null or empty
   */
  public void linkExistingConditionsToStep(Step step, List<String> conditionRootIds) {
    if (conditionRootIds == null || conditionRootIds.isEmpty()) {
      return;
    }
    for (String conditionRootId : conditionRootIds) {
      Condition root = findConditionRootById(conditionRootId);
      root.linkToStep(step, true);
      conditionRepository.save(root);
    }
  }

  // -- PRIVATE HELPERS --

  /**
   * Links a list of steps to a root condition via the conditions_steps join table.
   *
   * <p>Each step is linked with is_root=true since only the root condition carries the step
   * association at tree level.
   *
   * @param root the root condition to link
   * @param stepIds list of step IDs to link; ignored if null or empty
   */
  private void linkStepsToRoot(Condition root, List<String> stepIds) {
    if (stepIds == null || stepIds.isEmpty()) {
      return;
    }
    List<Step> steps = stepRepository.findAllById(stepIds);
    if (steps.size() != stepIds.size()) {
      List<String> found = steps.stream().map(Step::getId).toList();
      List<String> missing = stepIds.stream().filter(id -> !found.contains(id)).toList();
      throw new EntityNotFoundException("Steps not found: " + missing);
    }
    steps.forEach(step -> root.linkToStep(step, true));
    conditionRepository.save(root);
  }

  /**
   * Identifies the root condition input — the one with no parent reference.
   *
   * @param inputs flat list of condition inputs
   * @return the root {@link ConditionCreateInput}
   */
  private ConditionCreateInput findRootConditionInput(List<ConditionCreateInput> inputs) {
    return inputs.stream()
        .filter(c -> c.getTemporaryIdConditionParent() == null)
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No root condition found in input list"));
  }

  /**
   * Saves child conditions in dependency order (parent before children).
   *
   * @param remaining child condition inputs to persist
   * @param saved map of already-persisted conditions keyed by temporaryId
   * @param workflowId workflow identifier to stamp on each child
   */
  private void persistConditionTreeNodes(
      List<ConditionCreateInput> remaining, Map<String, Condition> saved, String workflowId) {
    // Safety limit to prevent infinite loops
    int maxIterations = remaining.size() + 1;
    int iteration = 0;

    // Continue until all conditions are processed or max iterations reached
    while (!remaining.isEmpty() && iteration < maxIterations) {
      iteration++;

      // List of conditions that cannot yet be processed (parent not resolved)
      List<ConditionCreateInput> retry = new ArrayList<>();
      for (ConditionCreateInput ci : remaining) {

        // Try to resolve parent using temporaryId mapping
        Condition parent = saved.get(ci.getTemporaryIdConditionParent());
        // If a parent is not yet created, retry in the next iteration
        if (parent == null) {
          retry.add(ci);
          continue;
        }
        // Create child condition entity
        Condition child = new Condition();
        child.setWorkflowId(workflowId);
        child.setKeyType(ci.getKeyType());
        child.setType(ci.getType());
        child.setValue(ci.getValue());
        child.setStepFrom(resolveStepFrom(ci.getStepFrom()));
        // Link a child to its parent
        child.setConditionParent(parent);
        Condition persistedChild = conditionRepository.save(child);

        // Keep the in-memory graph complete for response mapping.
        if (parent.getConditionChildren() == null) {
          parent.setConditionChildren(new ArrayList<>());
        }
        parent.getConditionChildren().add(persistedChild);

        saved.put(ci.getTemporaryId(), persistedChild);
      }
      // Retry unresolved conditions in next loop iteration
      remaining = retry;
    }
    if (!remaining.isEmpty()) {
      throw new IllegalArgumentException(
          "Circular or unresolved parent references in condition tree");
    }
  }

  private Step resolveStepFrom(String stepFromId) {
    if (stepFromId == null || stepFromId.isBlank()) {
      return null;
    }
    return stepRepository
        .findById(stepFromId)
        .orElseThrow(
            () ->
                new EntityNotFoundException(
                    "Condition references a non-existing step (field: step_from). Step ID: "
                        + stepFromId));
  }
}
