package io.openaev.api.chaining;

import io.openaev.api.chaining.dto.ConditionCreateInput;
import io.openaev.api.chaining.dto.ConditionOutput;
import io.openaev.api.chaining.dto.EventOutput;
import io.openaev.database.model.Condition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Static mapper between {@link Condition} and event DTOs. */
public class ConditionMapper {

  private ConditionMapper() {}

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
        deduplicatedById.values().stream().map(ConditionMapper::toConditionOutput).toList();

    return EventOutput.builder()
        .id(root.getId())
        .name(root.getName())
        .description(root.getDescription())
        .workflowId(root.getWorkflowId())
        .conditions(conditionOutputs)
        .createdAt(root.getCreationDate())
        .updatedAt(root.getUpdateDate())
        .build();
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

    return ConditionOutput.builder()
        .id(c.getId())
        .keyType(c.getKeyType())
        .keySubtype(c.getKeySubtype())
        .type(c.getType() != null ? c.getType().name() : null)
        .value(c.getValue())
        .conditionParentId(parentId)
        .build();
  }

  public static Condition toCondition(ConditionCreateInput input) {
    return toCondition(input, null);
  }

  public static Condition toCondition(ConditionCreateInput input, Condition conditionParent) {
    Objects.requireNonNull(input, "condition create input must not be null");

    return Condition.builder()
        .keyType(input.getKeyType())
        .keySubtype(input.getKeySubtype())
        .type(input.getType())
        .value(input.getValue())
        .conditionParent(conditionParent)
        .build();
  }
}
