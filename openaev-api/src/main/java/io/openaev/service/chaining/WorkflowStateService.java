package io.openaev.service.chaining;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.openaev.database.model.*;
import io.openaev.database.repository.ConditionRepository;
import io.openaev.utils.ConditionUtils;
import io.openaev.validator.IpAddressUtils;
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
  private final WorkflowStateStore workflowStateStore;
  private final ConditionRepository conditionRepository;

  /**
   * Syncs structured output data into the global workflow state entries and propagates matching
   * values to the local states of steps whose filter conditions are satisfied by the output.
   *
   * <p>Append-only: the accepted values and tuples are inserted as new entries of the global state
   * (and of the matching local states); the existing state is never read nor rewritten, so the cost
   * of a sync does not depend on how much the run has accumulated (ADR-011).
   *
   * @param dataToSync JSON element containing output data to merge
   * @param typeMappings mapping from field name to resolved chaining mapped type
   * @param workflowRun the running workflow whose global state is updated
   */
  public void syncState(
      JsonElement dataToSync, Map<String, ChainingMappedType> typeMappings, Workflow workflowRun) {
    if (!dataToSync.isJsonObject()) {
      return;
    }
    Map<String, ChainingMappedType> safeTypeMappings =
        typeMappings != null ? typeMappings : Collections.emptyMap();

    WorkflowStateEntries globalDelta = WorkflowStateEntries.empty();
    IngestionResult ingestion =
        saveToEntries(globalDelta, dataToSync.getAsJsonObject(), safeTypeMappings, workflowRun);
    if (ingestion.isEmpty()) {
      return;
    }

    workflowStateStore.append(
        workflowStateStore.getOrCreateGlobalStateId(workflowRun), globalDelta);
    propagateToLocalStates(ingestion, workflowRun);
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

    // Deterministic order: concurrent syncs touching the same local states must lock them in the
    // same order, or they could deadlock when running inside a single transaction.
    stepToConditions.entrySet().stream()
        .sorted(Map.Entry.comparingByKey(Comparator.comparing(Step::getId)))
        .forEach(
            stepEntry ->
                propagateValuesToStep(
                    stepEntry.getKey(), stepEntry.getValue(), ingestion, workflowRun));
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

    WorkflowStateEntries localDelta = WorkflowStateEntries.empty();
    for (Map.Entry<String, List<String>> valueEntry : valuesToPropagate.entrySet()) {
      localDelta.getInputByKey(valueEntry.getKey()).getValues().addAll(valueEntry.getValue());
    }
    // Tuples already present in the local state are ignored by the store (same content hash).
    localDelta.getCorrelated().addAll(correlatedToPropagate);

    workflowStateStore.append(
        workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun), localDelta);
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
          values.stream()
              .filter(val -> matchesAnyRootCondition(val, keyTypeName, rootConditions))
              .toList();
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
                    .anyMatch(
                        pair -> matchesAnyRootCondition(pair.value(), pair.key(), rootConditions)))
        .toList();
  }

  /**
   * Returns {@code true} if the given value satisfies at least one same-key-type leaf condition
   * within any of the provided root conditions.
   *
   * <p>This is the shared eligibility check for both primitive-value and correlated-tuple
   * propagation. AND/OR group semantics are intentionally ignored here. Full evaluation happens at
   * step execution time.
   */
  private boolean matchesAnyRootCondition(
      String value, String keyTypeName, List<Condition> rootConditions) {
    return rootConditions.stream()
        .anyMatch(root -> conditionUtils.matchesAnyLeafCondition(value, root, keyTypeName));
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
   * Parses structured output fields and adds their values to the global state delta.
   *
   * @param entries global state delta to populate
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
              recordAcceptedPrimitiveValue(
                  primitiveType, val, entries, parsedByType, validationContext);
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

  private void recordAcceptedPrimitiveValue(
      PrimitiveType primitiveType,
      String value,
      WorkflowStateEntries entries,
      Map<String, List<String>> parsedByType,
      PrimitiveValidationContext validationContext) {
    recordValue(primitiveType.name(), value, entries, parsedByType);
    if (primitiveType == PrimitiveType.IpSubnet) {
      recordExpandedSubnetHosts(value, entries, parsedByType, validationContext);
    }
  }

  private void recordExpandedSubnetHosts(
      String subnet,
      WorkflowStateEntries entries,
      Map<String, List<String>> parsedByType,
      PrimitiveValidationContext validationContext) {
    IpAddressUtils.ExpandedSubnetHosts expanded =
        IpAddressUtils.expandSubnetToHostsByFamily(subnet);
    for (String expandedIp : expanded.ipv4Hosts()) {
      PrimitiveType primitiveType = PrimitiveType.IPv4;
      if (PrimitiveValueValidator.isAcceptedForPrimitiveType(
          primitiveType, expandedIp, validationContext)) {
        recordValue(primitiveType.name(), expandedIp, entries, parsedByType);
      }
    }
    for (String expandedIp : expanded.ipv6Hosts()) {
      PrimitiveType primitiveType = PrimitiveType.IPv6;
      if (PrimitiveValueValidator.isAcceptedForPrimitiveType(
          primitiveType, expandedIp, validationContext)) {
        recordValue(primitiveType.name(), expandedIp, entries, parsedByType);
      }
    }
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

      pendingUpdates.add(
          () -> {
            recordAcceptedPrimitiveValue(
                primitiveType, valStr, entries, parsedByType, validationContext);
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

  // -- Read side ---------------------------------------------------------------------------------

  /**
   * View of the global state of a run, restricted to {@code keys}: input values of these keys and,
   * when {@code withCorrelated} is set, the correlated tuples holding at least one of them. Empty
   * when the run has no global state yet.
   */
  public WorkflowStateEntries loadGlobalEntries(
      Workflow workflowRun, Collection<String> keys, boolean withCorrelated) {
    return workflowStateStore
        .findGlobalStateId(workflowRun.getId())
        .map(stateId -> workflowStateStore.load(stateId, keys, withCorrelated, false))
        .orElseGet(WorkflowStateEntries::empty);
  }

  /**
   * View of the local state of a step template in a run, restricted to {@code keys} (with the
   * correlated tuples holding one of them when {@code withCorrelated} is set), and with its
   * committed execution hashes when {@code withHashes} is set. Empty when the step has no local
   * state yet.
   */
  public WorkflowStateEntries loadLocalEntries(
      Step stepTemplate,
      Workflow workflowRun,
      Collection<String> keys,
      boolean withCorrelated,
      boolean withHashes) {
    return workflowStateStore
        .findLocalStateId(stepTemplate.getId(), workflowRun.getId())
        .map(stateId -> workflowStateStore.load(stateId, keys, withCorrelated, withHashes))
        .orElseGet(WorkflowStateEntries::empty);
  }

  /** Input values of the global state of a run for the given keys. */
  public Set<String> getGlobalInputValues(String workflowRunId, Collection<String> keys) {
    return workflowStateStore
        .findGlobalStateId(workflowRunId)
        .map(stateId -> workflowStateStore.loadInputValues(stateId, keys))
        .orElseGet(Set::of);
  }

  // -- Execution hashes (anti-replay) -------------------------------------------------------------

  /** Execution hashes already committed for a step template in a run. */
  public Set<String> getCommittedHashes(Step stepTemplate, Workflow workflowRun) {
    return workflowStateStore
        .findLocalStateId(stepTemplate.getId(), workflowRun.getId())
        .map(workflowStateStore::loadExecutionHashes)
        .orElseGet(Set::of);
  }

  /**
   * Commits execution hashes for a step template in a run and returns the ones actually committed
   * by this call. A hash that was already committed — including by a concurrent evaluation of the
   * same step — is not returned: its combination has already been executed and must not be executed
   * again.
   */
  public Set<String> commitHashes(Step stepTemplate, Workflow workflowRun, Set<String> hashes) {
    if (hashes == null || hashes.isEmpty()) {
      return Set.of();
    }
    return workflowStateStore.commitExecutionHashes(
        workflowStateStore.getOrCreateLocalStateId(stepTemplate, workflowRun), hashes);
  }

  /**
   * Clears the committed execution hashes of a step template on a given RUN workflow, re-arming the
   * step so the next {@link StepService#createReadySteps} readies and re-executes it.
   *
   * <p>Editing an already-executed step in place (the autonomous {@code
   * update_openaev_attack_path_step}) swaps only the step's baked inject data; its committed
   * execution hashes stay behind, so the engine would keep treating the step as already-fired and
   * never run the corrected version. Dropping those hashes is what makes the "fix a step, then
   * re-run it" loop actually re-execute. Re-firing then replays the step against everything its
   * trigger has matched so far (a seed re-fires once; a finding-driven step re-fires across the
   * findings it already consumed), now with the corrected definition.
   *
   * <p>Load-only (never builds a state): a step that never executed against this run has no local
   * state and nothing committed, so this is a no-op and a cosmetic pre-execution edit costs
   * nothing.
   *
   * @param stepTemplate the step template whose re-fire guard should be reset
   * @param workflowExecution the RUN workflow the step executes under
   */
  public void clearExecutionHashes(Step stepTemplate, Workflow workflowExecution) {
    workflowStateStore
        .findLocalStateId(stepTemplate.getId(), workflowExecution.getId())
        .ifPresent(workflowStateStore::clearExecutionHashes);
  }
}
