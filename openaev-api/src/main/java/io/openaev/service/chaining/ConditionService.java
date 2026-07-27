package io.openaev.service.chaining;

import static io.openaev.api.chaining.ConditionMapper.resolveMappingType;
import static io.openaev.utils.JsonUtils.gson;

import io.openaev.api.chaining.ConditionMapper;
import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.api.chaining.dto.EventOutput;
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
import java.util.function.Supplier;
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

    // Allow one event/filter root plus any number of mapper roots.
    // In chaining, mapper conditions are independent root mappings.
    if (rootInputs.size() > 1) {
      long nonMapperRootCount =
          rootInputs.stream().filter(r -> r.getType() != ConditionType.MAPPER).count();
      if (nonMapperRootCount > 1) {
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
    root.setMappingType(resolveMappingType(rootInput));

    if (root.getConditionChildren() != null) {
      root.getConditionChildren().clear();
    }

    // Only clear step links if stepIds is explicitly provided (non-null);
    // null means "preserve existing links" (frontend may not send step_ids on simple edits)
    boolean hasExplicitStepIds = input.getStepIds() != null;
    if (hasExplicitStepIds && root.getConditionSteps() != null) {
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
          if (isRoot && hasExplicitStepIds) {
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
  public List<Condition> findNonMapperConditionsByWorkflowId(String workflowId) {
    return conditionRepository.findAllByWorkflowIdAndConditionParentIsNullAndTypeNot(
        workflowId, ConditionType.MAPPER);
  }

  /**
   * Returns all non-MAPPER conditions (roots AND descendants) for a given workflow.
   *
   * @param workflowId the workflow identifier
   * @return flat list of all non-MAPPER conditions belonging to the workflow
   */
  @Transactional(readOnly = true)
  public List<Condition> findAllNonMapperConditionsByWorkflowId(String workflowId) {
    return conditionRepository.findAllByWorkflowIdAndTypeNot(workflowId, ConditionType.MAPPER);
  }

  /**
   * Returns complete event outputs for a workflow, with full condition trees resolved from a flat
   * query. This avoids reliance on lazy-loaded {@code conditionChildren} collections, which may not
   * be initialized in a cold read transaction.
   *
   * @param workflowId the workflow identifier
   * @return list of fully populated {@link EventOutput} DTOs
   */
  @Transactional(readOnly = true)
  public List<EventOutput> findEventsByWorkflowId(String workflowId) {
    List<Condition> all =
        conditionRepository.findAllByWorkflowIdAndTypeNot(workflowId, ConditionType.MAPPER);

    // Index children by parent id for O(1) lookups
    Map<String, List<Condition>> childrenByParentId = new HashMap<>();
    for (Condition c : all) {
      Condition parent = c.getConditionParent();
      if (parent != null) {
        childrenByParentId.computeIfAbsent(parent.getId(), k -> new ArrayList<>()).add(c);
      }
    }

    List<Condition> roots = all.stream().filter(c -> c.getConditionParent() == null).toList();

    List<EventOutput> events = new ArrayList<>();
    for (Condition root : roots) {
      List<Condition> subtree = new ArrayList<>();
      collectSubtree(root, childrenByParentId, subtree);
      events.add(ConditionMapper.toOutput(root, subtree));
    }
    return events;
  }

  private static void collectSubtree(
      Condition node, Map<String, List<Condition>> childrenByParentId, List<Condition> acc) {
    acc.add(node);
    List<Condition> children = childrenByParentId.get(node.getId());
    if (children != null) {
      for (Condition child : children) {
        collectSubtree(child, childrenByParentId, acc);
      }
    }
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
      return List.of(new ExecutionBatch(input, new ArrayList<>(), null));
    }

    // DEPEND_ON CONDITIONS
    // Evaluate dependency conditions: verify that the referenced step template has been
    // executed at least once in the current workflow run before allowing this step to proceed.
    List<Condition> dependOnConditions =
        conditionTemplate.stream().filter(conditionUtils::isDependOnCondition).toList();

    if (!dependOnConditions.isEmpty()) {
      if (!evaluateDependOnConditions(dependOnConditions, workflowRun)) {
        log.debug(
            "[Chaining] Depend-on conditions not satisfied for step template {}",
            nextStepTemplateToExecute.getId());
        return Collections.emptyList();
      }
    }

    // FILTER CONDITIONS
    // Evaluate filter conditions against the current input AND the finding state (global state).
    // If another action produced a parameter needed by the filter, we look it up
    // in the workflow state pool.
    List<Condition> filterConditions =
        conditionTemplate.stream().filter(conditionUtils::isFilterCondition).toList();

    if (!filterConditions.isEmpty()) {
      if (!evaluateFilterConditions(filterConditions, workflowRun, nextStepTemplateToExecute)) {
        log.debug(
            "[Chaining] Filter conditions not satisfied for step template {}",
            nextStepTemplateToExecute.getId());
        return Collections.emptyList();
      }
    }

    // MAPPER CONDITIONS
    List<Condition> mapperConditions =
        conditionTemplate.stream().filter(conditionUtils::isMapperCondition).toList();

    // No mapper means direct execution with the original input
    if (mapperConditions.isEmpty()) {
      return List.of(new ExecutionBatch(input, new ArrayList<>(), null));
    }

    return prepareInputsForStepExecution(nextStepTemplateToExecute, workflowRun, mapperConditions);
  }

  /**
   * Evaluates all DEPEND_ON conditions for a step.
   *
   * <p>Each DEPEND_ON condition references a step template ID in its value. The condition is
   * satisfied if and only if that step template has been executed at least once in the given
   * workflow run.
   *
   * <p>All DEPEND_ON conditions must be satisfied
   *
   * @param dependOnConditions list of DEPEND_ON conditions to evaluate
   * @param workflowRun the running workflow (provides the run ID to scope the check)
   * @return {@code true} if all dependencies are satisfied
   */
  private boolean evaluateDependOnConditions(
      List<Condition> dependOnConditions, Workflow workflowRun) {
    for (Condition condition : dependOnConditions) {
      String dependentStepTemplateId = condition.getValue();
      if (dependentStepTemplateId == null || dependentStepTemplateId.isBlank()) {
        log.error(
            "[Chaining] DEPEND_ON condition has no step template ID value: {}", condition.getId());
        return false;
      }

      boolean executed =
          stepRepository.existsByStepTemplateIdAndWorkflowId(
              dependentStepTemplateId, workflowRun.getId());

      if (!executed) {
        log.debug(
            "[Chaining] DEPEND_ON not satisfied: step template {} has not been executed in workflow run {}",
            dependentStepTemplateId,
            workflowRun.getId());
        return false;
      }
    }
    return true;
  }

  /**
   * Evaluates all filter conditions for a step against the local and global state pools.
   *
   * <p>For each root filter condition tree, values are resolved at the leaf level (where each leaf
   * has its own keyType), not at the root level. This correctly handles AND/OR trees where the root
   * has no keyType, but children do.
   *
   * <p>Values are resolved in this order per leaf:
   *
   * <ol>
   *   <li>From the workflow local state pool (step-specific accumulated values from propagation)
   *   <li>From the workflow global state pool (values produced by other actions)
   * </ol>
   *
   * <p>All root filter conditions must be satisfied (AND semantics at the top level).
   *
   * @param filterConditions list of root filter conditions to evaluate
   * @param workflowRun the running workflow for accessing the state pools
   * @param stepTemplate the step template for accessing local state
   * @return {@code true} if all filter conditions are satisfied
   */
  private boolean evaluateFilterConditions(
      List<Condition> filterConditions, Workflow workflowRun, Step stepTemplate) {

    Supplier<WorkflowContext> contextSupplier =
        new Supplier<>() {
          private WorkflowContext cached;

          @Override
          public WorkflowContext get() {
            if (cached == null) {
              cached = fetchWorkflowContext(workflowRun, stepTemplate);
            }
            return cached;
          }
        };

    for (Condition filterCondition : filterConditions) {
      if (!isFilterTreeSatisfied(filterCondition, contextSupplier)) {
        log.info(
            "[Chaining] Filter tree NOT satisfied: rootId={}, rootType={}, rootKeyType={}, childrenCount={}",
            filterCondition.getId(),
            filterCondition.getType(),
            filterCondition.getKeyType(),
            filterCondition.getConditionChildren() != null
                ? filterCondition.getConditionChildren().size()
                : 0);
        return false;
      }
    }

    return true;
  }

  /**
   * Recursively evaluates a filter condition tree, resolving values at the leaf level.
   *
   * <p>For AND/OR nodes, recurses into children. For leaf nodes (EQ, NEQ, etc.), resolves the
   * actual value using the leaf's own keyType from the local and global state pools and compares it
   * against the leaf's target value.
   *
   * @param condition the condition node to evaluate (may be AND/OR or a leaf)
   * @param contextSupplier lazy supplier for the workflow context (loaded on first leaf access)
   * @return {@code true} if the condition tree is satisfied
   */
  private boolean isFilterTreeSatisfied(
      Condition condition, Supplier<WorkflowContext> contextSupplier) {

    if (condition == null) {
      return true;
    }

    // AND node: all children must be satisfied
    if (condition.getType() == ConditionType.AND) {
      if (condition.getConditionChildren() == null || condition.getConditionChildren().isEmpty()) {
        // A childless AND cannot be produced by any creation path: an action with no condition
        // creates no Condition at all, and an AND group is always created with >=1 child. This case
        // means an abnormal/corrupted condition tree.
        // The fail-safe is to keep the gate closed, and so, we return false here.
        // Consistent with the empty-OR case, which also returns false.
        return false;
      }
      return condition.getConditionChildren().stream()
          .allMatch(child -> isFilterTreeSatisfied(child, contextSupplier));
    }

    // OR node: at least one child must be satisfied
    if (condition.getType() == ConditionType.OR) {
      if (condition.getConditionChildren() == null || condition.getConditionChildren().isEmpty()) {
        return false;
      }
      return condition.getConditionChildren().stream()
          .anyMatch(child -> isFilterTreeSatisfied(child, contextSupplier));
    }

    // Leaf node: load context, resolve values, then evaluate.
    // A leaf is satisfied if ANY value from the resolved pool matches the condition.
    WorkflowContext context = contextSupplier.get();
    List<String> valuesToCheck =
        resolveAllFilterValues(condition, context.globalEntries(), context.localEntries());

    boolean result =
        valuesToCheck.stream()
            .anyMatch(val -> conditionUtils.evaluateLeafCondition(val, condition));
    if (!result) {
      log.info(
          "[Chaining] Filter leaf NOT satisfied: type={}, keyType={}, conditionValue={}, resolvedValues={}",
          condition.getType(),
          condition.getKeyType(),
          condition.getValue(),
          valuesToCheck);
    }
    return result;
  }

  /**
   * Resolves all candidate values for a filter condition by looking at: - The local state
   * (step-specific accumulated values from propagation) - The global state (finding pool from other
   * actions)
   *
   * <p>This method returns ALL available values so that the caller can check if ANY of them
   * satisfies the condition. This is necessary when multiple values are stored under the same key
   * type.
   *
   * @param condition the filter condition whose keyType identifies the field
   * @param globalEntries global workflow state entries (may be null if not yet loaded)
   * @param localEntries local workflow state entries (may be null if not yet loaded)
   * @return list of all candidate values (may be empty, never null)
   */
  private List<String> resolveAllFilterValues(
      Condition condition, WorkflowStateEntries globalEntries, WorkflowStateEntries localEntries) {

    String key =
        condition.getKeyType() != null ? condition.getKeyType().name() : condition.getKey();

    List<String> candidates = new ArrayList<>();

    // 1. Try local state pool (step-specific accumulated values from propagation)
    Set<String> localValues = getAllValuesFromEntries(localEntries, key);
    candidates.addAll(localValues);

    // 2. Try global state pool (finding pool from other actions)
    Set<String> globalValues = getAllValuesFromEntries(globalEntries, key);
    candidates.addAll(globalValues);

    return candidates;
  }

  /**
   * Returns all values for a given key from workflow state entries.
   *
   * @param entries workflow state entries (may be null)
   * @param key the key to look up
   * @return set of values (empty if not found, never null)
   */
  private Set<String> getAllValuesFromEntries(WorkflowStateEntries entries, String key) {
    if (entries == null || key == null) {
      return Set.of();
    }
    WorkflowStateEntries.Input input = entries.getInputByKey(key);
    if (input != null && input.getValues() != null && !input.getValues().isEmpty()) {
      return input.getValues();
    }
    return Set.of();
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
   * Identifies the root condition input, the one with no parent reference.
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
   * Identifies all root condition inputs, those with no parent reference.
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
   * combinations are tracked via hash to avoid duplicate executions and returned as ready-to-run
   * input batches with resolved mapper conditions. Hashes are only prepared in memory here and are
   * committed later by the caller through {@link #commitHashes(WorkflowState, List)}.
   *
   * @param stepTemplate step template for which input combinations are generated
   * @param workflowRun active workflow run used to resolve global/local workflow states
   * @return list of execution batches; empty when no mapper-driven execution is currently possible
   */
  public List<ConditionService.ExecutionBatch> prepareInputsForStepExecution(
      Step stepTemplate, Workflow workflowRun, List<Condition> mappers) {

    // No mappers means a default execution batch
    if (mappers.isEmpty()) {
      return List.of(new ConditionService.ExecutionBatch(null, List.of(), null));
    }

    // Fetch and Parse State
    WorkflowContext context = fetchWorkflowContext(workflowRun, stepTemplate);

    // Prepare Inputs
    MapperInputPreparation preparation =
        prepareMapperInputs(mappers, context.localEntries(), context.globalEntries());

    // Invalid dynamic mapper definitions cannot produce executable combinations.
    if (preparation.hasMissingDynamicValues()) {
      return Collections.emptyList();
    }

    Set<String> requiredKeys = extractRequiredExecutionKeys(mappers);

    // Build execution batches used as inputs for step execution.
    // Hashes are not committed here. The caller commits only hashes of batches
    // that actually proceed (i.e. are not rate-limited) via commitHashes().
    List<ExecutionBatch> batches =
        buildExecutionBatches(
            mappers, context.localEntries(), context.globalEntries(), preparation, requiredKeys);

    return batches;
  }

  /** Handles the complexity of fetching and deserializing the workflow states. */
  private WorkflowContext fetchWorkflowContext(Workflow workflowRun, Step stepTemplate) {
    WorkflowState globalState =
        workflowStateService.getGlobalStateByWorkflowId(workflowRun.getId());
    WorkflowState localState =
        workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun);

    WorkflowStateEntries emptyEntries =
        new WorkflowStateEntries(
            new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());

    WorkflowStateEntries localEntries =
        localState != null ? deserializeEntries(localState.getEntries()) : emptyEntries;
    WorkflowStateEntries globalEntries =
        globalState != null ? deserializeEntries(globalState.getEntries()) : emptyEntries;

    return new WorkflowContext(localState, localEntries, globalEntries);
  }

  /** Converts JSON strings to WorkflowStateEntries objects. */
  private WorkflowStateEntries deserializeEntries(String json) {
    return gson.fromJson(json, WorkflowStateEntries.class);
  }

  /**
   * Commits the given execution hashes into the local workflow state for the step template,
   * preventing those input combinations from being re-executed in the future.
   *
   * <p>Only hashes of batches that were actually turned into READY steps should be committed.
   * Batches that were delayed (e.g. due to rate limiting) must <b>not</b> have their hash committed
   * so that they can be retried later.
   *
   * @param stepTemplate the step template whose local state stores the hash set
   * @param workflowRun the running workflow
   * @param hashes the set of hashes to commit
   */
  public void commitHashes(Step stepTemplate, Workflow workflowRun, Set<String> hashes) {
    if (hashes == null || hashes.isEmpty()) {
      return;
    }
    WorkflowState localState =
        workflowStateService.loadOrBuildLocalState(stepTemplate, workflowRun);
    if (localState == null) {
      return;
    }
    WorkflowStateEntries entries = deserializeEntries(localState.getEntries());
    entries.getHashExecution().addAll(hashes);
    localState.setEntries(gson.toJson(entries));
    workflowStateService.save(localState);
  }

  /** Returns dynamic mapper keys required in every batch (DEFAULT mappers are excluded). */
  private Set<String> extractRequiredExecutionKeys(List<Condition> mappers) {
    return mappers.stream()
        .filter(mapper -> mapper.getMappingType() != MappingType.DEFAULT)
        .map(this::resolveMapperKey)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /**
   * Prepares mapper inputs.
   *
   * <p>DEFAULT values are stored as static values. Dynamic mapper values are collected from their
   * source pool when available. Missing dynamic values are allowed because correlated data may
   * still produce valid batches.
   */
  private MapperInputPreparation prepareMapperInputs(
      List<Condition> mappers,
      WorkflowStateEntries localEntries,
      WorkflowStateEntries globalEntries) {
    List<List<WorkflowStateEntries.Pair>> allPairsList = new ArrayList<>();
    Map<String, MappingType> keyToMappingType = new HashMap<>();
    Map<String, String> staticValues = new HashMap<>();

    for (Condition mapper : mappers) {
      String key = resolveMapperKey(mapper);
      if (key == null) {
        log.warn(
            "[Chaining] Skipping mapper {} because keyType and key are both missing",
            mapper.getId());
        return new MapperInputPreparation(List.of(), Map.of(), Map.of(), true);
      }

      if (mapper.getMappingType() == MappingType.DEFAULT) {
        staticValues.put(key, mapper.getValue());
        continue;
      }

      keyToMappingType.put(key, mapper.getMappingType());
      Set<String> values =
          resolveValuesByMappingType(key, mapper.getMappingType(), localEntries, globalEntries);

      if (values == null || values.isEmpty()) {
        continue;
      }

      allPairsList.add(
          values.stream().map(value -> new WorkflowStateEntries.Pair(key, value)).toList());
    }

    return new MapperInputPreparation(allPairsList, keyToMappingType, staticValues, false);
  }

  /**
   * Builds execution batches in two steps.
   *
   * <p>Step 1 uses correlated tuples when there are at least 2 dynamic keys. Covered keys come from
   * the tuple. Missing keys are completed from LOCAL or GLOBAL pools based on MappingType.
   *
   * <p>Step 2 runs the fallback cartesian product after step 1. Dedup removes duplicate combos.
   *
   * <p>DEFAULT values are added at the end for every batch. Hashes are not committed here. The
   * caller commits them with {@code commitHashes()}.
   */
  private List<ConditionService.ExecutionBatch> buildExecutionBatches(
      List<Condition> mappers,
      WorkflowStateEntries localEntries,
      WorkflowStateEntries globalEntries,
      MapperInputPreparation preparation,
      Set<String> requiredKeys) {

    List<ConditionService.ExecutionBatch> batches = new ArrayList<>();
    Set<String> pendingHashes = new HashSet<>();
    // Step 1: correlated-first (useful only with >= 2 dynamic inputs)
    if (requiredKeys.size() >= 2) {
      WorkflowStateEntries correlatedPool =
          preparation.hasAnyLocal() ? localEntries : globalEntries;
      List<WorkflowStateEntries.Correlated> candidates =
          correlatedPool.findCandidateCorrelated(requiredKeys);

      for (WorkflowStateEntries.Correlated tuple : candidates) {
        Map<String, String> covered = correlatedPool.projectTuple(tuple, requiredKeys);
        Set<String> uncoveredKeys = new HashSet<>(requiredKeys);
        uncoveredKeys.removeAll(covered.keySet());

        List<List<WorkflowStateEntries.Pair>> uncoveredValueLists = new ArrayList<>();
        boolean skipTuple = false;

        for (String uncoveredKey : uncoveredKeys) {
          MappingType source = preparation.keyToMappingType().get(uncoveredKey);
          Set<String> values =
              resolveValuesByMappingType(uncoveredKey, source, localEntries, globalEntries);

          if (values == null || values.isEmpty()) {
            log.warn(
                "[Chaining] Skipping correlated tuple of type '{}': uncovered key '{}' has no"
                    + " values in the {} pool",
                tuple.getType(),
                uncoveredKey,
                source);
            skipTuple = true;
            break;
          }

          uncoveredValueLists.add(
              values.stream().map(v -> new WorkflowStateEntries.Pair(uncoveredKey, v)).toList());
        }

        if (skipTuple) {
          continue;
        }

        for (List<WorkflowStateEntries.Pair> delta :
            localEntries.cartesianProduct(uncoveredValueLists)) {
          Map<String, String> combo = new TreeMap<>(covered);
          delta.forEach(pair -> combo.put(pair.key(), pair.value()));
          tryAddBatch(
              combo, preparation, localEntries, mappers, requiredKeys, pendingHashes, batches);
        }
      }
    }

    // Step 2: fallback cartesian (always runs; dedup skips duplicates from step 1)
    for (List<WorkflowStateEntries.Pair> comboPairs :
        localEntries.cartesianProduct(preparation.dynamicPairs())) {
      Map<String, String> combo = new TreeMap<>();
      comboPairs.forEach(pair -> combo.put(pair.key(), pair.value()));
      tryAddBatch(combo, preparation, localEntries, mappers, requiredKeys, pendingHashes, batches);
    }

    return batches;
  }

  /**
   * Resolves input values for a key from the correct state pool based on mapping type.
   *
   * <p>GLOBAL values come from workflow global state. LOCAL and other dynamic types are read from
   * the step local state.
   */
  private Set<String> resolveValuesByMappingType(
      String key,
      MappingType mappingType,
      WorkflowStateEntries localEntries,
      WorkflowStateEntries globalEntries) {
    return mappingType == MappingType.GLOBAL
        ? globalEntries.getInputByKey(key).getValues()
        : localEntries.getInputByKey(key).getValues();
  }

  /**
   * Adds one batch if valid and not duplicated.
   *
   * <p>Skips combos that miss required keys or are already known by hash. Static DEFAULT values are
   * merged after dedup.
   */
  private void tryAddBatch(
      Map<String, String> comboMap,
      MapperInputPreparation preparation,
      WorkflowStateEntries localEntries,
      List<Condition> mappers,
      Set<String> requiredKeys,
      Set<String> pendingHashes,
      List<ExecutionBatch> batches) {

    if (!comboMap.keySet().containsAll(requiredKeys)) {
      return;
    }

    String hash = localEntries.hashCombo(comboMap);
    if (localEntries.getHashExecution().contains(hash) || pendingHashes.contains(hash)) {
      return;
    }

    Map<String, String> fullInput = new HashMap<>(comboMap);
    fullInput.putAll(preparation.staticValues());

    List<Condition> resolvedMappers =
        mappers.stream()
            .map(template -> toResolvedMapper(template, fullInput))
            .collect(Collectors.toList());

    batches.add(new ConditionService.ExecutionBatch(gson.toJson(fullInput), resolvedMappers, hash));
    pendingHashes.add(hash);
  }

  /** Creates a resolved copy of a mapper condition with its value filled from the input map. */
  private Condition toResolvedMapper(Condition template, Map<String, String> fullInput) {
    Condition resolved = new Condition();
    resolved.setType(ConditionType.MAPPER);
    resolved.setKey(template.getKey());
    resolved.setKeyType(template.getKeyType());
    resolved.setMappingType(template.getMappingType());
    resolved.setDescription(template.getDescription());
    resolved.setName(template.getName());
    resolved.setWorkflowId(template.getWorkflowId());
    resolved.setCreationDate(Instant.now());
    resolved.setUpdateDate(Instant.now());
    String key = resolveMapperKey(template);
    resolved.setValue(key != null ? fullInput.get(key) : null);
    return resolved;
  }

  /** Returns the workflow-state key used by a mapper. */
  private String resolveMapperKey(Condition mapper) {
    return mapper.getKeyType() != null ? mapper.getKeyType().name() : null;
  }

  /**
   * Input payload and mapper conditions for one executable data-chaining batch.
   *
   * @param inputString resolved JSON input used to create a READY step
   * @param usedMappers mapper conditions used to build this input
   * @param hash deduplication hash for this input combination (nullable for non-mapper batches)
   */
  public record ExecutionBatch(String inputString, List<Condition> usedMappers, String hash) {}

  private record MapperInputPreparation(
      List<List<WorkflowStateEntries.Pair>> dynamicPairs,
      Map<String, MappingType> keyToMappingType,
      Map<String, String> staticValues,
      boolean hasMissingDynamicValues) {

    /** True when at least one dynamic mapper is LOCAL. */
    boolean hasAnyLocal() {
      return keyToMappingType.containsValue(MappingType.LOCAL);
    }
  }

  private record WorkflowContext(
      WorkflowState localStateEntity,
      WorkflowStateEntries localEntries,
      WorkflowStateEntries globalEntries) {}
}
