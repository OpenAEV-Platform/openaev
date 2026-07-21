package io.openaev.service.attackpath;

import io.openaev.database.model.Condition;
import io.openaev.service.attackpath.dto.ConsumedFindingKeyDTO;
import io.openaev.utils.ConditionUtils;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves a step template's kill-chain metadata from its conditions (issue 5048): the prerequisite
 * step templates it depends on, and the finding keys it consumes.
 *
 * <p>{@code dependsOn} comes from the step's {@code DEPEND_ON} conditions (each value is a
 * prerequisite step template id). {@code consumedFindingKeys} comes from the step's filter
 * conditions, flattened across AND/OR trees down to the leaves that carry a key type.
 */
@Component
@RequiredArgsConstructor
public class AttackPathKillChainResolver {

  private final ConditionUtils conditionUtils;

  /** A step template's kill-chain view: its prerequisites and the finding keys it consumes. */
  public record KillChainMeta(
      List<String> dependsOn, List<ConsumedFindingKeyDTO> consumedFindingKeys) {
    public static KillChainMeta empty() {
      return new KillChainMeta(List.of(), List.of());
    }
  }

  /**
   * @param stepConditions the conditions linked to one step template (the tree roots)
   */
  public KillChainMeta resolve(List<Condition> stepConditions) {
    if (stepConditions == null || stepConditions.isEmpty()) {
      return KillChainMeta.empty();
    }
    List<String> dependsOn = new ArrayList<>();
    List<ConsumedFindingKeyDTO> consumedFindingKeys = new ArrayList<>();
    for (Condition condition : stepConditions) {
      if (conditionUtils.isDependOnCondition(condition)) {
        String stepTemplateId = condition.getValue();
        if (stepTemplateId != null && !stepTemplateId.isBlank()) {
          dependsOn.add(stepTemplateId);
        }
      } else if (conditionUtils.isFilterCondition(condition)) {
        // A filter root may be an AND/OR tree with no key of its own; the consumed keys live at the
        // leaves, so walk the whole tree and collect every leaf that carries a key type.
        collectConsumedKeys(condition, consumedFindingKeys);
      }
    }
    return new KillChainMeta(dependsOn, consumedFindingKeys);
  }

  private void collectConsumedKeys(Condition condition, List<ConsumedFindingKeyDTO> out) {
    if (condition == null) {
      return;
    }
    if (condition.getKeyType() != null) {
      out.add(
          new ConsumedFindingKeyDTO(
              condition.getKeyType().label,
              condition.getType() != null ? condition.getType().name() : null,
              condition.getValue()));
    }
    if (condition.getConditionChildren() != null) {
      for (Condition child : condition.getConditionChildren()) {
        collectConsumedKeys(child, out);
      }
    }
  }
}
