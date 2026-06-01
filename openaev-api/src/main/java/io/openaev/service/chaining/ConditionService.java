package io.openaev.service.chaining;

import static io.openaev.api.chaining.ConditionMapper.resolveMappingType;
import static io.openaev.utils.JsonUtils.gson;

import io.openaev.api.chaining.ConditionMapper;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.StepRepository;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.exception.ChainingException;
import io.openaev.utils.ConditionUtils;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
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

  private final WorkflowStateService workflowStateService;

  private final ConditionUtils conditionUtils;

  private final ConditionRepository conditionRepository;
  private final StepRepository stepRepository;

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
            .mappingType(resolveMappingType(rootInput))
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

    List<ConditionCreateInput> rootInputs = findRootConditionInputs(conditionInputs);

    // Multiple roots are only allowed when all roots are MAPPER conditions
    if (rootInputs.size() > 1) {
      boolean allMapper = rootInputs.stream().allMatch(r -> r.getType() == ConditionType.MAPPER);
      if (!allMapper) {
        throw new IllegalArgumentException(
            "New step (TEMPLATE): Only 1 condition can be first parent");
      }
    }

    for (ConditionCreateInput rootInput : rootInputs) {
      Condition root = rootFactory.apply(rootInput);

      if (root == null) {
        throw new BadRequestException("Root condition must not be null");
      }

      persistConditionTree(
          conditionInputs, root, rootInput, childFactory, linkCondition, afterRootSaved);
    }
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
    root.setMappingType(resolveMappingType(rootInput));

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
   * Returns all event condition tree roots for a given workflow, excluding mapper conditions.
   *
   * @param workflowId the workflow identifier
   * @return list of root event conditions for that workflow
   */
  @Transactional(readOnly = true)
  public List<Condition> findEventRootsByWorkflowId(String workflowId) {
    return conditionRepository.findAllByWorkflowIdAndConditionParentIsNull(workflowId).stream()
        .filter(c -> !conditionUtils.isMapperCondition(c))
        .toList();
  }

  /**
   * Returns all condition tree roots for a given workflow (conditions with no parent).
   *
   * @param workflowId the workflow identifier
   * @return list of root conditions for that workflow
   */
  @Transactional(readOnly = true)
  public List<Condition> findNonMapperConditionsByWorkflowId(String workflowId) {
    return conditionRepository.findAllByWorkflowIdAndConditionParentIsNullAndTypeNot(
        workflowId, ConditionType.MAPPER);
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
    deleteAllConditionsByStepId(stepId, List.of());
  }

  /**
   * Deletes conditions linked to a given step, excluding specific condition IDs. Rules: - Always
   * remove the current condition-step link for this step. - Delete the condition only if, after
   * unlinking, it has no more condition-step links and no children. - Conditions whose IDs are in
   * {@code excludedConditionIds} are unlinked but never deleted, so they can be re-linked later.
   *
   * @param stepId step identifier
   * @param excludedConditionIds condition IDs to preserve (unlink only, never delete)
   */
  public void deleteAllConditionsByStepId(String stepId, List<String> excludedConditionIds) {
    List<Condition> conditions = findAllConditionsByStepId(stepId);
    if (conditions.isEmpty()) {
      return;
    }

    Set<String> excluded =
        excludedConditionIds == null ? Set.of() : new HashSet<>(excludedConditionIds);

    for (Condition condition : conditions) {
      unlinkFromStep(condition, stepId);
      Condition persisted = conditionRepository.save(condition);

      if (excluded.contains(persisted.getId())) {
        continue;
      }

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
   * Evaluates all conditions for a step template and returns valid ones for execution.
   *
   * @param nextStepTemplateToExecute the step to evaluate
   * @param input input data for the step
   * @param workflowRun the running workflow
   * @return valid conditions, empty list if none required, or null if execution should be deferred
   */
  public List<ConditionService.ExecutionBatch> checkCondition(
      Step nextStepTemplateToExecute, Workflow workflowRun, String input) throws ChainingException {

    List<Condition> conditionTemplate =
        findAllConditionsByStepId(nextStepTemplateToExecute.getId());

    // No condition means direct execution:
    if (conditionTemplate == null || conditionTemplate.isEmpty()) {
      return List.of(new ExecutionBatch(input, new ArrayList<>()));
    }

    //    // TIME CONDITIONS
    //    // TODO manage multi time condition (AND, OR: g C1 BEFORE OR C2 AFTER)
    //    // Compute expected start time for the condition to be considered as valid
    //     List<Condition> timeConditions =
    //        conditionTemplate.stream().filter(this::isTimeCondition).toList();
    //     for (Condition condition : timeConditions) {
    //      Instant now = Instant.now();
    //      Instant start = workflowRun.getWorkflowCreatedAt();
    //      // TODO: can this happen ? Shouldn't it throw an exception instead?
    //      if (start == null) start = now;
    //      long value = Long.parseLong(condition.getValue());
    //      Instant goal = start.plus(value, ChronoUnit.MILLIS);
    //
    //      if (isTimeConditionValid(condition, now, goal)) {
    //        conditionsExecution.add(ConditionFactory.executionOf(condition, goal));
    //        continue;
    //      }
    //      if (condition.getType().equals(ConditionType.AFTER)) {
    //        long delay = ChronoUnit.MILLIS.between(now, goal);
    //
    //        stepDelayQueueService.pushStepTemplateIntoStepDelayQueue(
    //            nextStepTemplateToExecute, now, input, delay, workflowRun, goal);
    //        return null;
    //      }
    //    }

    // Task ConditionExecution
    // Check event was already validated
    // Check event filters
    // Create pool local if needed
    //
    //    List<Condition> filterConditions =
    //        conditionTemplate.stream().filter(this::isFilterCondition).toList();
    //
    //    for (Condition condition : filterConditions) {
    //      Condition filterConditionValid =
    //          isFilterConditionValid(input, condition);
    //      if (filterConditionValid == null) {
    //        // todo condition not valid break analyse
    //      } else {
    //        conditionsExecution.add(filterConditionValid);
    //      }
    //    }
    //

    // MAPPER CONDITIONS
    List<Condition> mapperConditions =
        conditionTemplate.stream().filter(conditionUtils::isMapperCondition).toList();

    return prepareInputsForStepExecution(nextStepTemplateToExecute, workflowRun, mapperConditions);

    //    // StepFrom (DEPEND_ON) conditions
    //    List<Condition> stepFrom =
    //        conditionTemplate.stream().filter(condition -> condition.getStepFrom() !=
    // null).toList();
    //    for (Condition condition : stepFrom) {
    //      String idStepFromTemplate = condition.getStepFrom().getId();
    //      List<Step> dependOnStepsRunByTemplateIdAndWorkflowRunId =
    //          stepService
    //              .findAllStepExecutedByStepTemplateIdAndWorkflowRunId(
    //                  idStepFromTemplate, workflowRun.getId())
    //              .stream()
    //              .filter(step -> step.getOutput() != null)
    //              .toList();
    //
    //      // Count of current step template already run into this workflow run
    //      int stepExecutedCount =
    //          stepService.countExecutedStep(workflowRun.getId(),
    // nextStepTemplateToExecute.getId());
    //
    //      boolean hasDependencyOutput = !dependOnStepsRunByTemplateIdAndWorkflowRunId.isEmpty();
    //      boolean underExecutionLimit =
    //          stepExecutedCount < nextStepTemplateToExecute.getLimitExecution();
    //
    //      // todo : change : !dependOnStepsRunByTemplateIdAndWorkflowRunId.isEmpty()
    //      // ( means at least 1 stepFrom is/has been running),
    //      // to implement: check if input/output as already be used into the next stepToExecute
    //      // This condition means:
    //      // - the previews one has been executed and contain output
    //      // - and the next one not reach his limit of execution
    //      if (hasDependencyOutput && underExecutionLimit) {
    //        conditionsExecution.add(isDependOn(idStepFromTemplate));
    //      } else {
    //        // Todo : condition not valid break analyse
    //        return null;
    //      }
    //    }
    // todo Mapped input-data step
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
   * Returns {@code true} if the given step has at least one condition of type {@link
   * ConditionType#MAPPER}.
   *
   * @param step the step to inspect
   * @return {@code true} if a mapper condition is linked to the step, {@code false} otherwise
   */
  public boolean hasConditionMapper(Step step) {
    return step.getConditions().stream().anyMatch(conditionUtils::isMapperCondition);
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

    conditionRepository.save(root);
  }

  /**
   * Identifies the root condition input — the one with no parent reference.
   *
   * <p>For non-MAPPER conditions, exactly one root is expected. For MAPPER conditions, multiple
   * roots are allowed (each mapper is independent).
   *
   * @param inputs flat list of condition inputs
   * @return the root {@link ConditionCreateInput}
   * @throws IllegalArgumentException if zero or more than one non-MAPPER root is found
   */
  public ConditionCreateInput findRootConditionInput(List<ConditionCreateInput> inputs) {
    List<ConditionCreateInput> roots = findRootConditionInputs(inputs);
    if (roots.size() != 1) {
      throw new IllegalArgumentException(
          "New step (TEMPLATE): Only 1 condition can be first parent");
    }
    return roots.getFirst();
  }

  /**
   * Identifies all root condition inputs — those with no parent reference.
   *
   * <p>For MAPPER conditions, multiple roots are allowed since each mapper is an independent
   * mapping. For other condition types, the caller should validate that exactly one root exists.
   *
   * @param inputs flat list of condition inputs
   * @return list of root {@link ConditionCreateInput}s
   * @throws IllegalArgumentException if no root is found
   */
  public List<ConditionCreateInput> findRootConditionInputs(List<ConditionCreateInput> inputs) {
    List<ConditionCreateInput> roots =
        inputs.stream().filter(c -> c.getTemporaryIdConditionParent() == null).toList();
    if (roots.isEmpty()) {
      throw new IllegalArgumentException(
          "New step (TEMPLATE): At least 1 condition must be a root (no parent)");
    }
    return roots;
  }

  /**
   * Links a condition to a step via the join table, or updates the root flag if already linked.
   *
   * @param condition the condition to link
   * @param step the target step (must have an ID)
   * @param isRoot true if this is the root condition for the step
   */
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

  /**
   * Removes the link between a condition and a step.
   *
   * @param condition the condition to unlink
   * @param stepId the step ID to remove from the condition's step list
   */
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

  /**
   * Builds execution batches for a template step from workflow global/local mapper states.
   *
   * <p>For each mapper on the template, this method collects candidate values from the relevant
   * partition (GLOBAL or LOCAL), computes the Cartesian product of all dynamic values, merges
   * DEFAULT mapper values, and keeps only combinations that satisfy required execution keys. Unique
   * combinations are tracked via hash to avoid duplicate executions, persisted into LOCAL state,
   * and returned as ready-to-run input batches with resolved mapper conditions.
   *
   * @param stepTemplate step template for which input combinations are generated
   * @param workflowRun active workflow run used to resolve global/local workflow states
   * @return list of execution batches; empty when no mapper-driven execution is currently possible
   */
  public List<ConditionService.ExecutionBatch> prepareInputsForStepExecution(
      Step stepTemplate, Workflow workflowRun, List<Condition> mappers) {

    // No mappers means a default execution batch
    if (mappers.isEmpty()) {
      return List.of(new ConditionService.ExecutionBatch(null, List.of()));
    }

    // Fetch and Parse State
    WorkflowContext context = fetchWorkflowContext(workflowRun, stepTemplate);

    // Prepare Inputs
    MapperInputPreparation preparation =
        prepareMapperInputs(mappers, context.localEntries(), context.globalEntries());

    if (preparation.hasMissingDynamicValues()) {
      return Collections.emptyList();
    }

    Set<String> requiredKeys = extractRequiredExecutionKeys(mappers);

    // Build execution batches which be used as input for the step execution.
    List<ExecutionBatch> batches =
        buildExecutionBatches(
            mappers,
            context.localEntries(),
            preparation.dynamicPairs(),
            preparation.staticValues(),
            requiredKeys);

    saveLocalState(context);
    return batches;
  }

  /** Handles the complexity of fetching and deserializing the workflow states. */
  private WorkflowContext fetchWorkflowContext(Workflow workflowRun, Step stepTemplate) {
    WorkflowState globalState =
        workflowStateService.getGlobalStateByWorkflowId(workflowRun.getId());
    WorkflowState localState =
        workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun);

    WorkflowStateEntries localEntries = deserializeEntries(localState.getEntries());
    WorkflowStateEntries globalEntries = deserializeEntries(globalState.getEntries());

    return new WorkflowContext(localState, localEntries, globalEntries);
  }

  /** Converts JSON strings to WorkflowStateEntries objects. */
  private WorkflowStateEntries deserializeEntries(String json) {
    return gson.fromJson(json, WorkflowStateEntries.class);
  }

  /** Syncs the POJO back to the entity and saves it. */
  private void saveLocalState(WorkflowContext context) {
    String json = gson.toJson(context.localEntries());
    context.localStateEntity().setEntries(json);
    workflowStateService.save(context.localStateEntity());
  }

  /** Returns the set of key-type names that must be present in every execution combo. */
  private Set<String> extractRequiredExecutionKeys(List<Condition> mappers) {
    return mappers.stream()
        .filter(mapper -> mapper.getMappingType() != MappingType.DEFAULT)
        .map(mapper -> mapper.getKeyType().name())
        .collect(Collectors.toSet());
  }

  /**
   * Collects candidate values for each mapper: DEFAULT values go to staticValues, GLOBAL/LOCAL
   * values go to dynamic pairs. Returns early if any dynamic mapper has no values.
   */
  private MapperInputPreparation prepareMapperInputs(
      List<Condition> mappers,
      WorkflowStateEntries localEntries,
      WorkflowStateEntries globalEntries) {
    List<List<WorkflowStateEntries.Pair>> allPairsList = new ArrayList<>();
    Map<String, String> staticValues = new HashMap<>();

    for (Condition mapper : mappers) {
      String key = mapper.getKeyType().name();

      if (mapper.getMappingType() == MappingType.DEFAULT) {
        staticValues.put(key, mapper.getValue());
        continue;
      }

      Set<String> values =
          (mapper.getMappingType() == MappingType.GLOBAL)
              ? globalEntries.getInputByKey(key).getValues()
              : localEntries.getInputByKey(key).getValues();

      if (values == null || values.isEmpty()) {
        return new MapperInputPreparation(List.of(), Map.of(), true);
      }

      allPairsList.add(
          values.stream().map(value -> new WorkflowStateEntries.Pair(key, value)).toList());
    }

    return new MapperInputPreparation(allPairsList, staticValues, false);
  }

  /**
   * Computes the Cartesian product of dynamic values, filters duplicates via hash, merges static
   * values, and returns ready-to-run execution batches.
   */
  private List<ConditionService.ExecutionBatch> buildExecutionBatches(
      List<Condition> mappers,
      WorkflowStateEntries localEntries,
      List<List<WorkflowStateEntries.Pair>> allPairsList,
      Map<String, String> staticValues,
      Set<String> requiredKeys) {

    List<List<WorkflowStateEntries.Pair>> product = localEntries.cartesianProduct(allPairsList);
    List<ConditionService.ExecutionBatch> batches = new ArrayList<>();

    for (List<WorkflowStateEntries.Pair> comboPairs : product) {
      Map<String, String> comboMap = new TreeMap<>();
      comboPairs.forEach(pair -> comboMap.put(pair.key(), pair.value()));

      if (!localEntries.comboContainAllExecutionKeys(requiredKeys, comboMap)) {
        continue;
      }

      String hash = localEntries.hashCombo(comboMap);
      if (localEntries.getHashExecution().contains(hash)) {
        continue;
      }

      Map<String, String> fullInput = new HashMap<>(comboMap);
      fullInput.putAll(staticValues);

      List<Condition> resolvedMappers =
          mappers.stream()
              .map(mapperTemplate -> toResolvedMapper(mapperTemplate, fullInput))
              .collect(Collectors.toList());

      batches.add(new ConditionService.ExecutionBatch(gson.toJson(fullInput), resolvedMappers));
      localEntries.getHashExecution().add(hash);
    }

    return batches;
  }

  /** Creates a resolved copy of a mapper condition with its value filled from the input map. */
  private Condition toResolvedMapper(Condition template, Map<String, String> fullInput) {
    Condition resolved = new Condition();
    resolved.setType(ConditionType.MAPPER);
    resolved.setKey(template.getKey());
    resolved.setKeyType(template.getKeyType());
    resolved.setMappingType(template.getMappingType());
    resolved.setDescription(template.getDescription());
    resolved.setKeySubtype(template.getKeySubtype());
    resolved.setName(template.getName());
    resolved.setWorkflowId(template.getWorkflowId());
    resolved.setCreationDate(Instant.now());
    resolved.setUpdateDate(Instant.now());
    resolved.setValue(fullInput.get(template.getKeyType().name()));
    return resolved;
  }

  /**
   * Input payload and mapper conditions for one executable data-chaining batch.
   *
   * @param inputString resolved JSON input used to create a READY step
   * @param usedMappers mapper conditions used to build this input
   */
  public record ExecutionBatch(String inputString, List<Condition> usedMappers) {}

  private record MapperInputPreparation(
      List<List<WorkflowStateEntries.Pair>> dynamicPairs,
      Map<String, String> staticValues,
      boolean hasMissingDynamicValues) {}

  private record WorkflowContext(
      WorkflowState localStateEntity,
      WorkflowStateEntries localEntries,
      WorkflowStateEntries globalEntries) {}
}
