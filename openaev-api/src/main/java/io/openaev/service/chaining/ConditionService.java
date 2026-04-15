package io.openaev.service.chaining;

import io.openaev.api.chaining.ConditionMapper;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.database.model.*;
import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.Step;
import io.openaev.database.model.Workflow;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ChainingException;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.stream.Collectors;
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
    if (input == null) {
      throw new BadRequestException("Input must not be null");
    }
    List<ConditionCreateInput> conditionInputs = input.getConditions();
    if (conditionInputs == null || conditionInputs.isEmpty()) {
      throw new BadRequestException("At least one condition is required");
    }

    ConditionCreateInput rootInput = findRootConditionInput(conditionInputs);

    Condition root =
        Condition.builder()
            .workflowId(input.getWorkflowId())
            .name(input.getName())
            .description(input.getDescription())
            .type(rootInput.getType())
            .keyType(rootInput.getKeyType())
            .keySubtype(rootInput.getKeySubtype())
            .build();

    return persistConditionTree(
        conditionInputs,
        root,
        rootInput,
        (childInput, parent) -> {
          Condition child = ConditionMapper.toCondition(childInput, parent);
          child.setWorkflowId(input.getWorkflowId());
          return child;
        },
        (condition, isRoot) -> {
          if (isRoot) {
            linkStepsToRoot(condition, input.getStepIds());
          }
        },
        null);
  }

  /**
   * Creates a condition tree from a flat list of {@link ConditionCreateInput} using custom
   * factories.
   *
   * <p>This overload is used by {@link StepService#stepConditionTemplate} where conditions are
   * created inline on a step template rather than via the Event API.
   *
   * @param conditionInputs flat list of condition inputs (root and children)
   * @param rootFactory creates the root {@link Condition} from the root input
   * @param childFactory creates a child {@link Condition} from input and resolved parent
   * @param linkCondition optional callback to link each condition (a root flag distinguishes root
   *     from child)
   * @param afterRootSaved optional callback invoked after the root is persisted
   */
  public void createConditionTree(
      List<ConditionCreateInput> conditionInputs,
      java.util.function.Function<ConditionCreateInput, Condition> rootFactory,
      BiFunction<ConditionCreateInput, Condition, Condition> childFactory,
      BiConsumer<Condition, Boolean> linkCondition,
      Consumer<Condition> afterRootSaved) {
    if (conditionInputs == null || conditionInputs.isEmpty()) {
      throw new BadRequestException("At least one condition is required");
    }

    ConditionCreateInput rootInput = findRootConditionInput(conditionInputs);
    Condition root = rootFactory.apply(rootInput);

    if (root == null) {
      throw new BadRequestException("Root condition must not be null");
    }

    persistConditionTree(
        conditionInputs, root, rootInput, childFactory, linkCondition, afterRootSaved);
  }

  /**
   * Persists a condition tree in parent-before-children order.
   *
   * <p>The root condition is persisted first, then child conditions are saved level by level.
   */
  private Condition persistConditionTree(
      List<ConditionCreateInput> conditionInputs,
      Condition root,
      ConditionCreateInput rootInput,
      BiFunction<ConditionCreateInput, Condition, Condition> childFactory,
      BiConsumer<Condition, Boolean> linkCondition,
      Consumer<Condition> afterRootSaved) {

    if (conditionInputs == null || conditionInputs.isEmpty() || root == null || rootInput == null) {
      throw new BadRequestException("At least one condition is required");
    }

    if (childFactory == null) {
      throw new BadRequestException("Child factory must not be null");
    }

    if (linkCondition != null) {
      linkCondition.accept(root, true);
    }

    root = conditionRepository.save(root);

    if (afterRootSaved != null) {
      afterRootSaved.accept(root);
    }

    // Keep track of temp ids -> persisted entities
    Map<String, Condition> savedConditionsByTemporaryId = new HashMap<>();
    savedConditionsByTemporaryId.put(rootInput.getTemporaryId(), root);

    // Group children by parent temporary id
    Map<String, List<ConditionCreateInput>> childrenByParentTemporaryId =
        conditionInputs.stream()
            .filter(condition -> condition.getTemporaryIdConditionParent() != null)
            .collect(Collectors.groupingBy(ConditionCreateInput::getTemporaryIdConditionParent));

    // BFS traversal
    Queue<String> queue = new LinkedList<>();
    queue.add(rootInput.getTemporaryId());

    while (!queue.isEmpty()) {
      String currentTemporaryId = queue.poll();

      List<ConditionCreateInput> children =
          childrenByParentTemporaryId.getOrDefault(currentTemporaryId, Collections.emptyList());

      for (ConditionCreateInput childInput : children) {
        Condition parent =
            savedConditionsByTemporaryId.get(childInput.getTemporaryIdConditionParent());

        if (parent == null) {
          throw new BadRequestException(
              "Parent condition not found for temporary id: "
                  + childInput.getTemporaryIdConditionParent());
        }

        Condition child = childFactory.apply(childInput, parent);

        if (child == null) {
          throw new BadRequestException("Child condition must not be null");
        }

        if (linkCondition != null) {
          linkCondition.accept(child, false);
        }

        child = conditionRepository.save(child);

        // Keep the in-memory graph consistent for API mapping/tests.
        if (parent.getConditionChildren() == null) {
          parent.setConditionChildren(new ArrayList<>());
        }
        parent.getConditionChildren().add(child);

        savedConditionsByTemporaryId.put(childInput.getTemporaryId(), child);
        queue.add(childInput.getTemporaryId());
      }
    }

    return root;
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
    if (input == null) {
      throw new BadRequestException("Input must not be null");
    }

    List<ConditionCreateInput> conditionInputs = input.getConditions();
    if (conditionInputs == null || conditionInputs.isEmpty()) {
      throw new BadRequestException("At least one condition is required");
    }

    Condition root = findConditionRootById(conditionRootId);
    ConditionCreateInput rootInput = findRootConditionInput(conditionInputs);

    root.setName(input.getName());
    root.setDescription(input.getDescription());
    root.setWorkflowId(input.getWorkflowId());
    root.setType(rootInput.getType());
    root.setKeyType(rootInput.getKeyType());
    root.setKeySubtype(rootInput.getKeySubtype());

    if (root.getConditionChildren() != null) {
      root.getConditionChildren().clear();
    }
    if (root.getConditionSteps() != null) {
      root.getConditionSteps().clear();
    }

    return persistConditionTree(
        conditionInputs,
        root,
        rootInput,
        (childInput, parent) -> {
          Condition child = ConditionMapper.toCondition(childInput, parent);
          child.setWorkflowId(input.getWorkflowId());
          return child;
        },
        (condition, isRoot) -> {
          if (isRoot) {
            linkStepsToRoot(condition, input.getStepIds());
          }
        },
        null);
  }

  // -- CONDITION TREE GET --
  /**
   * Finds a condition tree root by its ID.
   *
   * @param conditionRootId the root condition ID
   * @return the root {@link Condition}
   */
  @Transactional(readOnly = true)
  public Condition findConditionRootById(String conditionRootId) {
    Condition condition =
        conditionRepository
            .findById(conditionRootId)
            .orElseThrow(
                () -> new EntityNotFoundException("Condition root not found: " + conditionRootId));
    if (condition.getConditionParent() != null) {
      throw new EntityNotFoundException("Condition root not found: " + conditionRootId);
    }
    return condition;
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
   * Returns all persisted conditions across workflows.
   *
   * @return list of all conditions
   */
  @Transactional(readOnly = true)
  public List<Condition> findAll() {
    return conditionRepository.findAll();
  }

  // -- CONDITION TREE DELETE --
  /**
   * Deletes a condition tree root and all its children (cascade).
   *
   * @param conditionRootId the root condition ID
   */
  public void deleteConditionTree(String conditionRootId) {
    if (conditionRootId == null || conditionRootId.isBlank()) {
      throw new BadRequestException("conditionRootId must not be null or blank");
    }

    if (!conditionRepository.existsById(conditionRootId)) {
      throw new EntityNotFoundException("Condition not found: " + conditionRootId);
    }
    conditionRepository.deleteById(conditionRootId);
  }

  /**
   * Deletes conditions linked to a given step. Rules: - Always remove the current condition-step
   * link for this step. - Delete the condition only if, after unlinking, it has no more
   * condition-step links and no children.
   *
   * @param stepId step identifier
   */
  public void deleteAllConditionsByStepId(String stepId) {
    List<Condition> conditions = findAllConditionsByStepId(stepId);
    if (conditions.isEmpty()) {
      return;
    }

    for (Condition condition : conditions) {
      unlinkFromStep(condition, stepId);
      Condition persisted = conditionRepository.save(condition);

      boolean hasNoStepLinks =
          persisted.getConditionSteps() == null || persisted.getConditionSteps().isEmpty();
      boolean hasNoChildren =
          persisted.getConditionChildren() == null || persisted.getConditionChildren().isEmpty();
      if (hasNoStepLinks && hasNoChildren) {
        conditionRepository.delete(persisted);
      }
    }
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
    return conditionRepository.findAllLinkedToStepId(stepId);
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
   * <p>TODO: this is for legacy behavior only (compare from start of workflow instead of previous
   * step.
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

    // No condition means direct execution:
    if (conditionTemplate == null || conditionTemplate.isEmpty()) return new ArrayList<>();

    List<Condition> conditionsExecution = new ArrayList<>();
    List<Condition> timeConditions =
        conditionTemplate.stream().filter(this::isTimeCondition).toList();

    // Time conditions
    // TODO manage multi time condition (AND, OR: g C1 BEFORE OR C2 AFTER)
    // Compute expected start time for the condition to be considered as valid
    for (Condition condition : timeConditions) {
      Instant now = Instant.now();
      Instant start = workflowRun.getWorkflowCreatedAt();
      // TODO: can this happen ? Shouldn't it throw an exception instead?
      if (start == null) start = now;
      long value = Long.parseLong(condition.getValue());
      Instant goal = start.plus(value, ChronoUnit.MILLIS);

      if (isTimeConditionValid(condition, now, goal)) {
        conditionsExecution.add(ConditionFactory.executionOf(condition, goal));
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
    List<Condition> filterConditions =
        conditionTemplate.stream().filter(this::isFilterCondition).toList();

    for (Condition condition : filterConditions) {
      Condition filterConditionValid =
          isFilterConditionValid(condition, input, nextStepTemplateToExecute.getData());
      if (filterConditionValid == null) {
        // todo condition not valid break analyse
      } else {
        conditionsExecution.add(filterConditionValid);
      }
    }

    // Mapper conditions
    List<Condition> mapperConditions =
        conditionTemplate.stream().filter(this::isMapperCondition).toList();

    for (Condition condition : mapperConditions) {
      Condition mapperConditionValid =
          isMapperConditionValid(condition, input, nextStepTemplateToExecute.getData());
      if (mapperConditionValid == null) {
        // todo condition not valid break analyse
      } else {
        conditionsExecution.add(mapperConditionValid);
      }
    }

    // StepFrom (DEPEND_ON) conditions
    List<Condition> stepFrom =
        conditionTemplate.stream().filter(condition -> condition.getStepFrom() != null).toList();
    for (Condition condition : stepFrom) {
      String idStepFromTemplate = condition.getStepFrom().getId();
      List<Step> dependOnStepsRunByTemplateIdAndWorkflowRunId =
          stepService
              .findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
                  idStepFromTemplate, workflowRun.getId())
              .stream()
              .filter(step -> step.getOutput() != null)
              .toList();

      // Count of current step template already run into this workflow run
      int stepExecutedCount =
          stepService.countExecutedStep(workflowRun.getId(), nextStepTemplateToExecute.getId());

      boolean hasDependencyOutput = !dependOnStepsRunByTemplateIdAndWorkflowRunId.isEmpty();
      boolean underExecutionLimit =
          stepExecutedCount < nextStepTemplateToExecute.getLimitExecution();

      // todo : change : !dependOnStepsRunByTemplateIdAndWorkflowRunId.isEmpty()
      // ( means at least 1 stepFrom is/has been running),
      // to implement: check if input/output as already be used into the next stepToExecute
      // This condition means:
      // - the previews one has been executed and contain output
      // - and the next one not reach his limit of execution
      if (hasDependencyOutput && underExecutionLimit) {
        conditionsExecution.add(isDependOn(idStepFromTemplate));
      } else {
        // Todo : condition not valid break analyse
        return null;
      }
    }
    // todo Mapped input-data step
    return conditionsExecution;
  }

  /**
   * Creates a DEPEND_ON condition for a step template dependency.
   *
   * @param idStepFromTemplate identifier of the dependent step template
   * @return the DEPEND_ON condition
   */
  public Condition isDependOn(String idStepFromTemplate) {
    return ConditionFactory.dependOn(idStepFromTemplate);
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
      linkToStep(root, step, true);
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

    // Remove duplicates while preserving order
    List<String> uniqueStepIds = new ArrayList<>(new LinkedHashSet<>(stepIds));

    List<Step> steps = stepRepository.findAllById(uniqueStepIds);

    if (steps.size() != uniqueStepIds.size()) {
      Set<String> found = steps.stream().map(Step::getId).collect(Collectors.toSet());

      List<String> missing = uniqueStepIds.stream().filter(id -> !found.contains(id)).toList();

      throw new EntityNotFoundException("Steps not found: " + missing);
    }

    steps.forEach(step -> linkToStep(root, step, true));
  }

  /**
   * Identifies the root condition input — the one with no parent reference.
   *
   * @param inputs flat list of condition inputs
   * @return the root {@link ConditionCreateInput}
   */
  public ConditionCreateInput findRootConditionInput(List<ConditionCreateInput> inputs) {
    return inputs.stream()
        .filter(
            conditionCreateInput -> conditionCreateInput.getTemporaryIdConditionParent() == null)
        .reduce(
            (a, b) -> {
              throw new IllegalArgumentException(
                  "New step (TEMPLATE): Only 1 condition can be first parent");
            })
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "New step (TEMPLATE): Only 1 condition can be first parent"));
  }

  public void linkToStep(Condition condition, Step step, boolean isRoot) {
    if (condition == null || step == null || step.getId() == null) {
      throw new BadRequestException("Steps must have a valid condition or step id");
    }

    List<ConditionStep> conditionSteps = condition.getConditionSteps();
    if (conditionSteps == null) {
      conditionSteps = new ArrayList<>();
      condition.setConditionSteps(conditionSteps);
    }

    ConditionStep existingLink =
        conditionSteps.stream()
            .filter(link -> link.getStep() != null)
            .filter(link -> Objects.equals(link.getStep().getId(), step.getId()))
            .findFirst()
            .orElse(null);

    if (existingLink != null) {
      // A link between this condition and step already exists.
      // We update the root flag instead of creating a duplicate link.
      existingLink.setRoot(isRoot);
      return;
    }

    ConditionStep link = new ConditionStep();
    link.setCondition(condition);
    link.setStep(step);
    link.setRoot(isRoot);
    conditionSteps.add(link);
  }

  public void unlinkFromStep(Condition condition, String stepId) {
    if (condition == null || stepId == null || stepId.isBlank()) {
      return;
    }

    List<ConditionStep> conditionSteps = condition.getConditionSteps();
    if (conditionSteps == null || conditionSteps.isEmpty()) {
      return;
    }

    conditionSteps.removeIf(
        link -> link.getStep() != null && Objects.equals(link.getStep().getId(), stepId));
  }
}
