package io.openaev.api.chaining.dto;

import io.openaev.database.model.Condition;
import io.openaev.database.model.Step;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Static mapper between {@link Condition} and event DTOs. */
public class EventMapper {

  private EventMapper() {}

  /**
   * Maps a condition tree root to output. If children are initialized on the root, the full tree is
   * returned; otherwise only loaded nodes are returned.
   */
  public static EventOutput toOutput(Condition root) {
    Objects.requireNonNull(root, "root condition must not be null");

    List<Condition> discovered = new ArrayList<>();
    collectTree(root, discovered);
    return toOutput(root, discovered);
  }

  /**
   * Maps a root with an explicit flat list of all tree conditions.
   *
   * <p>Use this overload when you already queried all conditions and want to guarantee complete
   * output even if lazy children are not initialized on the root instance.
   */
  public static EventOutput toOutput(Condition root, List<Condition> allConditions) {
    Objects.requireNonNull(root, "root condition must not be null");

    List<Condition> source =
        (allConditions == null || allConditions.isEmpty()) ? List.of(root) : allConditions;

    // Preserve order and avoid duplicates by condition_id.
    Map<String, Condition> deduplicatedById = new LinkedHashMap<>();
    for (Condition condition : source) {
      if (condition != null && condition.getId() != null) {
        deduplicatedById.putIfAbsent(condition.getId(), condition);
      }
    }
    deduplicatedById.putIfAbsent(root.getId(), root);

    List<ConditionOutput> conditionOutputs =
        deduplicatedById.values().stream().map(EventMapper::toConditionOutput).toList();

    String stepFromId = root.getStepFrom() != null ? root.getStepFrom().getId() : null;

    return new EventOutput(
        root.getId(),
        root.getName(),
        root.getDescription(),
        root.getWorkflowId(),
        conditionOutputs,
        stepFromId,
        root.getCreationDate(),
        root.getUpdateDate());
  }

  private static void collectTree(Condition node, List<Condition> result) {
    if (node == null) return;

    result.add(node);

    if (node.getConditionChildren() != null) {
      for (Condition child : node.getConditionChildren()) {
        collectTree(child, result);
      }
    }
  }

  private static ConditionOutput toConditionOutput(Condition c) {
    String parentId = c.getConditionParent() != null ? c.getConditionParent().getId() : null;

    ConditionOutput output = new ConditionOutput();
    output.setId(c.getId());
    output.setKeyType(c.getKeyType());
    output.setType(c.getType() != null ? c.getType().name() : null);
    output.setValue(c.getValue());
    output.setConditionParentId(parentId);
    return output;
  }

  public static Condition toCondition(ConditionCreateInput input, Step stepFrom) {
    return toCondition(input, stepFrom, null);
  }

  public static Condition toCondition(
      ConditionCreateInput input, Step stepFrom, Condition conditionParent) {
    Objects.requireNonNull(input, "condition create input must not be null");

    Condition condition = new Condition();
    condition.setKeyType(input.getKeyType());
    condition.setType(input.getType());
    condition.setValue(input.getValue());
    condition.setStepFrom(stepFrom);
    condition.setConditionParent(conditionParent);
    return condition;
  }
}
