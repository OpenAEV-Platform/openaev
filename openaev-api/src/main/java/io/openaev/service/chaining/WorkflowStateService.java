package io.openaev.service.chaining;

import static io.openaev.utils.JsonUtils.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.database.repository.WorkflowStateRepository;
import io.openaev.utils.ConditionUtils;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class WorkflowStateService {
  private final ConditionUtils conditionUtils;
  private final PrimitiveValidationContextBuilder primitiveValidationContextBuilder;
  private final WorkflowStateRepository workflowStateRepository;
  private final ConditionRepository conditionRepository;

  /**
   * Syncs structured output data into the global workflow state entries and propagates matching
   * values to the local states of steps whose filter conditions are satisfied by the output.
   *
   * @param dataToSync JSON element containing output data to merge
   * @param typeMappings mapping from field name to resolved chaining mapped type
   * @param workflowRun the running workflow whose global state is updated
   */
  public void syncState(
      JsonElement dataToSync, Map<String, ChainingMappedType> typeMappings, Workflow workflowRun) {
    Map<String, ChainingMappedType> safeTypeMappings =
        typeMappings != null ? typeMappings : Collections.emptyMap();
    WorkflowState globalState = loadOrBuildGlobalState(workflowRun);

    WorkflowStateEntries entries =
        gson.fromJson(globalState.getEntries(), WorkflowStateEntries.class);

    // Process traces
    if (dataToSync.isJsonObject()) {
      IngestionResult ingestion =
          saveToEntries(entries, dataToSync.getAsJsonObject(), safeTypeMappings, workflowRun);
      propagateToLocalStates(ingestion, workflowRun);
    }

    // Save JSON back to DB
    globalState.setEntries(gson.toJson(entries));
    save(globalState);
  }

  /**
   * Propagates output values to the local states of step templates whose filter conditions (events)
   * are satisfied by the produced output. This ensures that when a step B has an event expecting a
   * certain value, and another step A produces that value, step B's local pool gets populated with
   * it.
   *
   * <p>In addition to primitive values, newly produced correlated tuples are also evaluated: if any
   * field of a tuple matches the step's event conditions, the full tuple is saved into the local
   * state so that downstream execution can use it as a correlated input set.
   *
   * @param ingestion the output of the ingestion phase for this sync call
   * @param workflowRun the running workflow execution
   */
  private void propagateToLocalStates(IngestionResult ingestion, Workflow workflowRun) {

    if (ingestion.isEmpty() || workflowRun.getWorkflowTemplate() == null) {
      return;
    }

    // parsedByType already contains the primitive subfields extracted from complex objects
    // (via saveCorrelatedObject), so deriving key types from it covers both scalar and
    // correlated-field origins.
    Set<PrimitiveType> outputKeyTypes = resolveOutputKeyTypes(ingestion.parsedByType().keySet());
    if (outputKeyTypes.isEmpty()) {
      return;
    }

    // Find steps that have event conditions matching the produced key types
    Map<Step, List<Condition>> stepToConditions =
        findStepsWithMatchingConditions(workflowRun.getWorkflowTemplate().getId(), outputKeyTypes);
    if (stepToConditions.isEmpty()) {
      return;
    }

    for (Map.Entry<Step, List<Condition>> stepEntry : stepToConditions.entrySet()) {
      propagateValuesToStep(stepEntry.getKey(), stepEntry.getValue(), ingestion, workflowRun);
    }
  }

  /**
   * Converts output type name strings to their corresponding {@link PrimitiveType} enum values,
   * ignoring any names that don't match a known enum constant.
   *
   * @param typeNames set of output type name strings
   * @return set of resolved PrimitiveType values
   */
  private Set<PrimitiveType> resolveOutputKeyTypes(Set<String> typeNames) {
    return typeNames.stream()
        .map(
            typeName -> {
              try {
                return PrimitiveType.valueOf(typeName);
              } catch (IllegalArgumentException e) {
                log.warn(
                    "[Chaining] Ignoring output key '{}' because it does not match any PrimitiveType",
                    typeName);
                return null;
              }
            })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  /**
   * Finds filter conditions in the workflow template that match the given output key types, and
   * groups them by the step templates they are linked to.
   *
   * @param workflowTemplateId the workflow template ID
   * @param outputKeyTypes the output key types to match against
   * @return map of step templates to their matching filter conditions
   */
  private Map<Step, List<Condition>> findStepsWithMatchingConditions(
      String workflowTemplateId, Set<PrimitiveType> outputKeyTypes) {

    // Find filter conditions in the workflow template that match the output key types
    Set<ConditionType> excludedTypes = Set.of(ConditionType.MAPPER, ConditionType.DEPEND_ON);
    List<Condition> matchingConditions =
        conditionRepository.findFilterConditionsByWorkflowId(workflowTemplateId, excludedTypes);

    matchingConditions =
        matchingConditions.stream()
            .filter(root -> rootHasMatchingLeafKeyTypes(root, outputKeyTypes))
            .toList();

    if (matchingConditions.isEmpty()) {
      return Map.of();
    }

    // Group conditions by the step templates they are linked to
    Map<Step, List<Condition>> stepToConditions = new HashMap<>();
    for (Condition condition : matchingConditions) {
      if (condition.getConditionSteps() == null) {
        continue;
      }
      for (ConditionStep conditionStep : condition.getConditionSteps()) {
        Step step = conditionStep.getStep();
        if (step != null) {
          stepToConditions.computeIfAbsent(step, k -> new ArrayList<>()).add(condition);
        }
      }
    }
    return stepToConditions;
  }

  /**
   * Propagates matching output values to the local state of a single step template, filtering only
   * values that satisfy the step's filter conditions.
   *
   * <p>Beyond primitive values, any newly produced correlated tuples whose fields satisfy the
   * step's event conditions are also propagated as full tuples into the local correlated pool,
   * enabling downstream correlated-first input resolution.
   *
   * @param stepTemplate the target step template
   * @param rootConditions the filter conditions linked to this step
   * @param ingestion the ingestion result carrying primitive values and new correlated tuples
   * @param workflowRun the running workflow execution
   */
  private void propagateValuesToStep(
      Step stepTemplate,
      List<Condition> rootConditions,
      IngestionResult ingestion,
      Workflow workflowRun) {

    Map<String, List<String>> valuesToPropagate =
        filterValuesMatchingConditions(rootConditions, ingestion.parsedByType());
    List<WorkflowStateEntries.Correlated> correlatedToPropagate =
        filterCorrelatedMatchingConditions(rootConditions, ingestion.newCorrelated());

    if (valuesToPropagate.isEmpty() && correlatedToPropagate.isEmpty()) {
      return;
    }

    // Load or build the local state for this step template and add the values
    WorkflowState localState = loadOrBuildLocalState(stepTemplate, workflowRun);
    if (localState == null) {
      log.error(
          "[Chaining] Failed to load or build local state for step template {} in workflow run {}",
          stepTemplate.getId(),
          workflowRun.getId());
      return;
    }
    WorkflowStateEntries localEntries =
        gson.fromJson(localState.getEntries(), WorkflowStateEntries.class);

    for (Map.Entry<String, List<String>> valueEntry : valuesToPropagate.entrySet()) {
      String keyTypeName = valueEntry.getKey();
      List<String> values = valueEntry.getValue();
      WorkflowStateEntries.Input input = localEntries.getInputByKey(keyTypeName);
      input.getValues().addAll(values);
    }

    // Add correlated tuples that are not already present in local state (dedup by pair-set)
    for (WorkflowStateEntries.Correlated tuple : correlatedToPropagate) {
      if (localEntries.getCorrelated().stream()
          .noneMatch(existing -> existing.getValues().equals(tuple.getValues()))) {
        localEntries.getCorrelated().add(tuple);
      }
    }

    localState.setEntries(gson.toJson(localEntries));
    save(localState);
  }

  /**
   * Filters output values to keep only those matching the interested key types from the root
   * conditions' children and matching at least one leaf condition in the event tree.
   *
   * @param rootConditions the root filter conditions to check against
   * @param parsedByType map of output type names to their extracted values
   * @return map of key type names to values that match at least one leaf condition
   */
  private Map<String, List<String>> filterValuesMatchingConditions(
      List<Condition> rootConditions, Map<String, List<String>> parsedByType) {

    // Collect the key types from child conditions of the roots
    Set<String> interestedKeyTypes =
        rootConditions.stream()
            .flatMap(
                root ->
                    (root.getConditionChildren() != null
                            ? root.getConditionChildren().stream()
                            : java.util.stream.Stream.<Condition>empty())
                        .flatMap(child -> getConditionKeyTypeNames(child).stream()))
            .collect(Collectors.toSet());

    // Filter output values: keep only those matching interested key types and relevant to the event
    Map<String, List<String>> valuesToPropagate = new HashMap<>();
    for (String keyTypeName : interestedKeyTypes) {
      List<String> values = parsedByType.get(keyTypeName);
      if (values == null || values.isEmpty()) {
        continue;
      }

      // Propagate values that match any leaf condition in any root condition tree.
      // We ignore AND/OR grouping here: a value is relevant if it satisfies any individual
      // leaf condition, because it may contribute to the overall event satisfaction together
      // with other values. The full AND/OR evaluation happens at step execution time.
      List<String> matchingValues =
          values.stream().filter(val -> matchesAnyRootCondition(val, rootConditions)).toList();
      if (!matchingValues.isEmpty()) {
        valuesToPropagate.put(keyTypeName, matchingValues);
      }
    }
    return valuesToPropagate;
  }

  private boolean rootHasMatchingLeafKeyTypes(Condition root, Set<PrimitiveType> outputKeyTypes) {
    if (root.getConditionChildren() == null || root.getConditionChildren().isEmpty()) {
      return false;
    }
    Set<String> outputKeyTypeNames =
        outputKeyTypes.stream().map(PrimitiveType::name).collect(Collectors.toSet());
    return root.getConditionChildren().stream()
        .flatMap(child -> getConditionKeyTypeNames(child).stream())
        .anyMatch(outputKeyTypeNames::contains);
  }

  private Set<String> getConditionKeyTypeNames(Condition condition) {
    if (condition.getKeyTypes() != null && !condition.getKeyTypes().isEmpty()) {
      return condition.getKeyTypes().stream().map(PrimitiveType::name).collect(Collectors.toSet());
    }
    return Set.of();
  }

  /**
   * Filters correlated tuples to keep only those whose field values satisfy at least one leaf
   * condition in any of the step's root event conditions.
   *
   * <p>A tuple is selected when ANY of its primitive field values individually matches ANY leaf
   * condition. The full AND/OR event evaluation happens at step execution time; here we only check
   * eligibility so the tuple is available in the local pool.
   *
   * @param rootConditions the root filter conditions for the target step
   * @param newCorrelated correlated tuples produced during the current sync call
   * @return list of tuples that match at least one leaf condition
   */
  private List<WorkflowStateEntries.Correlated> filterCorrelatedMatchingConditions(
      List<Condition> rootConditions, List<WorkflowStateEntries.Correlated> newCorrelated) {

    if (newCorrelated.isEmpty()) {
      return List.of();
    }

    return newCorrelated.stream()
        .filter(
            tuple ->
                tuple.getValues().stream()
                    .anyMatch(pair -> matchesAnyRootCondition(pair.value(), rootConditions)))
        .toList();
  }

  /**
   * Returns {@code true} if the given value satisfies at least one leaf condition within any of the
   * provided root conditions.
   *
   * <p>This is the shared eligibility check for both primitive-value and correlated-tuple
   * propagation. AND/OR group semantics are intentionally ignored here. Full evaluation happens at
   * step execution time.
   */
  private boolean matchesAnyRootCondition(String value, List<Condition> rootConditions) {
    return rootConditions.stream()
        .anyMatch(root -> conditionUtils.matchesAnyLeafCondition(value, root));
  }

  /**
   * Output of a single {@link #saveToEntries} call: the primitive values indexed by type name
   * (produced this call only), and the correlated tuples newly created from complex objects.
   */
  private record IngestionResult(
      Map<String, List<String>> parsedByType, List<WorkflowStateEntries.Correlated> newCorrelated) {

    boolean isEmpty() {
      return parsedByType.isEmpty() && newCorrelated.isEmpty();
    }
  }

  /**
   * Parses structured output fields and adds their values to the global state entries.
   *
   * @param entries global state entries to populate
   * @param structuredOutput JSON object with field arrays produced by the step
   * @param typeMappings mapping from output field name to its resolved chaining type
   * @param workflowRun the running workflow execution
   * @return {@link IngestionResult} containing extracted primitive values and newly created
   *     correlated tuples
   */
  private IngestionResult saveToEntries(
      WorkflowStateEntries entries,
      JsonObject structuredOutput,
      Map<String, ChainingMappedType> typeMappings,
      Workflow workflowRun) {

    Map<String, List<String>> parsedByType = new HashMap<>();
    List<WorkflowStateEntries.Correlated> newCorrelated = new ArrayList<>();
    PrimitiveValidationContext validationContext =
        primitiveValidationContextBuilder.build(typeMappings, workflowRun);

    for (Map.Entry<String, JsonElement> entry : structuredOutput.entrySet()) {
      String fieldName = entry.getKey();
      JsonElement jsonValue = entry.getValue();

      // Each output field must be a non-null array to be processed
      if (jsonValue.isJsonNull() || !jsonValue.isJsonArray()) {
        continue;
      }

      ChainingMappedType mappedType = typeMappings.get(fieldName);
      if (mappedType == null) {
        log.warn(
            "[Chaining] Skipping output field '{}' because no primitive type mapping exists.",
            fieldName);
        continue;
      }
      if (mappedType.kind() == ChainingTypeKind.NOT_CHAINABLE) {
        continue;
      }

      // Resolve primitive types once, avoids re-evaluating inside the element loop
      List<PrimitiveType> primitiveTypes = mappedType.primitiveTypes();
      boolean isComplex = mappedType.kind() == ChainingTypeKind.COMPLEX;

      for (JsonElement element : jsonValue.getAsJsonArray()) {
        if (element.isJsonPrimitive() && !isComplex && !primitiveTypes.isEmpty()) {
          // Validate scalar value against scope rules and record under each matching primitive type
          String val = element.getAsString();
          for (PrimitiveType primitiveType : primitiveTypes) {
            if (PrimitiveValueValidator.isAcceptedForPrimitiveType(
                primitiveType, val, validationContext)) {
              recordValue(primitiveType.name(), val, entries, parsedByType);
            }
          }
        } else if (element.isJsonObject() && isComplex) {
          // Complex output (e.g. {port: 22, host: "1.1.1.1"}): store as correlated pairs
          String type = mappedType.origin() != null ? mappedType.origin().name() : null;
          saveCorrelatedObject(
              entries,
              element.getAsJsonObject(),
              type,
              parsedByType,
              newCorrelated,
              validationContext);
        }
      }
    }
    return new IngestionResult(parsedByType, newCorrelated);
  }

  /**
   * Records a validated primitive value into both the global state entries and the by-type
   * accumulator used for local-state propagation.
   */
  private void recordValue(
      String key,
      String value,
      WorkflowStateEntries entries,
      Map<String, List<String>> parsedByType) {
    entries.getInputByKey(key).getValues().add(value);
    parsedByType.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
  }

  /**
   * Saves a correlated object (multi-field entry like {username, password}) into state entries,
   * validating each field against the scope rules before accepting it.
   *
   * <p>JSON field names are normalized to {@link PrimitiveType#name()} (for example, "username" to
   * "Username") so that the object path produces the same key convention as the scalar path.
   *
   * <p><b>All-or-nothing semantics</b>: if any field fails scope validation, the entire tuple is
   * rejected and nothing is written to state. A partial correlated tuple would break the
   * correlation semantics. Downstream code expects every pair in a tuple to be valid together.
   *
   * <p>Once all fields pass, each accepted primitive field is ALSO decomposed flat into {@code
   * entries.inputs} so that downstream consumers can query individual primitives regardless of
   * their complex origin.
   *
   * @param entries state entries to update
   * @param obj JSON object whose fields form a correlated pair set
   * @param type business type name (ContractOutputType.name())
   * @param parsedByType accumulator for local-state propagation (mirroring the scalar path)
   * @param newCorrelated accumulator that collects the correlated tuple created by this call
   * @param validationContext scope rules applied per primitive type (same as the scalar path)
   */
  private void saveCorrelatedObject(
      WorkflowStateEntries entries,
      JsonObject obj,
      String type,
      Map<String, List<String>> parsedByType,
      List<WorkflowStateEntries.Correlated> newCorrelated,
      PrimitiveValidationContext validationContext) {

    Set<WorkflowStateEntries.Pair> pairSet = new HashSet<>();
    // Collect updates for entries.inputs and parsedByType; applied only if all fields are valid.
    List<Runnable> pendingUpdates = new ArrayList<>();

    for (Map.Entry<String, JsonElement> fieldEntry : obj.entrySet()) {
      String jsonKey = fieldEntry.getKey();
      JsonElement value = fieldEntry.getValue();

      if (value == null || value.isJsonNull()) {
        continue;
      }

      Optional<PrimitiveType> accepted = resolveOutputFieldPrimitiveType(type, jsonKey);
      if (accepted.isEmpty()) {
        continue;
      }

      PrimitiveType primitiveType = accepted.get();
      String valStr = value.isJsonPrimitive() ? value.getAsString() : value.toString();

      // Reject the whole tuple if any field fails validation. Partial tuples break correlation.
      if (!PrimitiveValueValidator.isAcceptedForPrimitiveType(
          primitiveType, valStr, validationContext)) {
        log.debug(
            "[Chaining] Rejecting correlated tuple: field '{}' value '{}' failed scope validation",
            jsonKey,
            valStr);
        return;
      }

      pairSet.add(new WorkflowStateEntries.Pair(primitiveType.name(), valStr));

      String primKey = primitiveType.name();
      pendingUpdates.add(
          () -> {
            entries.getInputByKey(primKey).getValues().add(valStr);
            parsedByType.computeIfAbsent(primKey, k -> new ArrayList<>()).add(valStr);
          });
    }

    if (pairSet.size() > 1) {
      pendingUpdates.forEach(Runnable::run);
      WorkflowStateEntries.Correlated tuple = new WorkflowStateEntries.Correlated(pairSet, type);
      entries.getCorrelated().add(tuple);
      newCorrelated.add(tuple);
    }
  }

  /**
   * Resolves the primitive type for an output field.
   *
   * <p>It first applies contextual complex-type mapping, then falls back to direct primitive-label
   * matching. Unknown fields are ignored.
   */
  private Optional<PrimitiveType> resolveOutputFieldPrimitiveType(
      String outputTypeName, String jsonFieldName) {
    Optional<PrimitiveType> contextual =
        ChainingTypeRegistry.resolveComplexFieldPrimitive(outputTypeName, jsonFieldName);
    if (contextual.isPresent()) {
      return contextual;
    }

    Optional<PrimitiveType> primitiveOpt = PrimitiveType.fromLabelOptional(jsonFieldName);
    if (primitiveOpt.isEmpty()) {
      log.debug(
          "[Chaining] Skipping unknown field '{}' in correlated object: no PrimitiveType match",
          jsonFieldName);
      return Optional.empty();
    }
    return primitiveOpt;
  }

  /** Persists a workflow state entity. */
  public WorkflowState save(WorkflowState state) {
    return workflowStateRepository.save(state);
  }

  /**
   * Deletes all workflow states associated with workflows of the given simulation.
   *
   * @param simulationId the ID of the simulation whose workflow states should be cleared
   */
  public void deleteAllBySimulationId(String simulationId) {
    workflowStateRepository.deleteAllByWorkflowExecution_Simulation_Id(simulationId);
  }

  /**
   * Returns or creates the global state for a workflow execution.
   *
   * @param workflow the running workflow
   * @return existing global state, or a new (unsaved) one with empty entries
   */
  private WorkflowState loadOrBuildGlobalState(Workflow workflow) {
    WorkflowState state = getGlobalStateByWorkflowId(workflow.getId());
    if (state == null) {
      state =
          WorkflowState.builder()
              .workflowExecution(workflow)
              .entries(gson.toJson(createInitialEntries()))
              .build();
    }
    return state;
  }

  private static WorkflowStateEntries createInitialEntries() {
    return new WorkflowStateEntries(
        new ArrayList<>(), new ArrayList<>(), new HashSet<>(), new HashSet<>());
  }

  /**
   * Returns the global {@link WorkflowState} for a workflow execution (null step-template).
   *
   * @param workflowId the workflow execution ID
   * @return the global state, or {@code null} if not yet created
   */
  public WorkflowState getGlobalStateByWorkflowId(String workflowId) {
    return workflowStateRepository.findByStepTemplateIsNullAndWorkflowExecutionId(workflowId);
  }

  /**
   * Returns the local {@link WorkflowState} for a step template within a workflow execution.
   *
   * @param targetTemplate the step template ID
   * @param workflowExecution the workflow execution ID
   * @return the local state, or {@code null} if not yet initialized
   */
  public WorkflowState loadOrBuildLocalState(Step targetTemplate, Workflow workflowExecution) {
    WorkflowState localState =
        workflowStateRepository.findByStepTemplate_IdAndWorkflowExecution_Id(
            targetTemplate.getId(), workflowExecution.getId());

    if (localState == null) {
      localState =
          initializeLocalState(
              targetTemplate, workflowExecution, gson.toJson(createInitialEntries()));
    }

    return localState;
  }

  /**
   * Creates a new local state entity for a step template (not yet persisted).
   *
   * @param target the step template
   * @param workflowExecution the workflow execution
   * @param entriesJson initial entries as JSON
   * @return a new WorkflowState bound to the step and workflow
   */
  private WorkflowState initializeLocalState(
      Step target, Workflow workflowExecution, String entriesJson) {
    return WorkflowState.builder()
        .stepTemplate(target)
        .workflowExecution(workflowExecution)
        .entries(entriesJson)
        .build();
  }

  /**
   * path - key are use during the config of mapped value (see Condition Mapper) path is different
   * depending on step parent but key is the same for the next step
   *
   * @param workflowStateEntries "state entries of current step"
   * @param output "new output"
   * @param path "outputs.message.stdout" or "outputs.message.port+outputs.message.ip"
   * @param key "stout" or "ip+port"
   */
  public void newOutput(
      WorkflowStateEntries workflowStateEntries, String output, String path, String key) {
    if (workflowStateEntries.isPathCorrelated(path)) {
      // Get Mapped condition liked to this step(child) and with idStepFrom(parent)
      // todo It give you the key <-> path
      Map<String, String> pathToKey = new HashMap<>();
      // todo remove
      pathToKey.put("outputs.message.ip", "ip");
      pathToKey.put("outputs.message.port", "port");
      List<String> paths = workflowStateEntries.pathCorrelated(path);

      Set<WorkflowStateEntries.Pair> values = new HashSet<>();
      for (String pathUnit : paths) {
        // TODO check if we can have other than Primitive
        String value = getValues(output, pathUnit).stream().findFirst().orElse("");
        values.add(new WorkflowStateEntries.Pair(pathToKey.get(pathUnit), value));
      }

      Map<Set<WorkflowStateEntries.Pair>, WorkflowStateEntries.Correlated> index =
          workflowStateEntries.getIndexCorrelatedInput();
      if (!index.containsKey(values)) {
        WorkflowStateEntries.Correlated newCorrelated =
            new WorkflowStateEntries.Correlated(values, null);
        workflowStateEntries.getCorrelated().add(newCorrelated);
        // todo test all combination  and launch the ones not executed
        // Todo save this StepInputBuffer
      }
    } else {
      Set<String> values = getValues(output, path);

      List<String> newValues = new ArrayList<>();

      WorkflowStateEntries.Input input = workflowStateEntries.getInputByKey(key);
      for (String value : values) {
        if (!input.getValues().contains(value)) {
          newValues.add(value);
          input.getValues().add(value);
          // todo test all combination and launch the ones not executed
          // Todo save this StepInputBuffer
        }
      }
    }
  }

  /**
   * Extracts values from an output string based on the given path.
   *
   * @param output the output string to extract values from
   * @param path the path specifying which fields to extract
   * @return a set of extracted string values
   */
  private Set<String> getValues(String output, String path) {
    Map<String, Object> fields = StepService.getFields(output, path);

    return fields.values().stream()
        .map(
            value -> {
              if (value instanceof JsonNull) {
                return null;
              } else if (value instanceof JsonPrimitive jsonPrimitive) {
                return jsonPrimitive.getAsString();
              } else {
                return value.toString();
              }
            })
        .collect(Collectors.toSet());
  }
}
