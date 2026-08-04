package io.openaev.service.chaining;

import static io.openaev.database.audit.ModelBaseListener.DATA_DELETE;

import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Base;
import io.openaev.database.model.ScopeRuleValueType;
import io.openaev.database.model.Team;
import io.openaev.database.repository.WorkflowScopeRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps workflow scope rules consistent when a scoped entity is deleted.
 *
 * <p>Scope rules reference assets, asset groups and teams by their raw id ({@code
 * workflow_scope_rule_value}) with no foreign key, because the column is polymorphic (it can also
 * hold IPs, subnets and domains). Nothing at the database level therefore removes a rule when the
 * asset / asset group / team it points at is deleted, which leaves an unresolvable "ghost" entry:
 * the scope editor cannot resolve its label and renders a permanent "Loading..." chip.
 *
 * <p>This listener consumes the same entity lifecycle bus that feeds SSE and the notification
 * engine ({@link io.openaev.database.audit.ModelBaseListener}), so it catches every deletion path
 * in a single place: single delete, bulk chunked delete, and cascade deletes triggered by removing
 * a parent (scenario, exercise, tenant, ...). Cleanup runs {@code BEFORE_COMMIT}, i.e. inside the
 * same transaction as the deletion, so the scope rules disappear atomically with their entity.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WorkflowScopeRuleCascadeListener {

  private final WorkflowScopeRuleRepository workflowScopeRuleRepository;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void onEntityDeleted(BaseEvent event) {
    if (!DATA_DELETE.equals(event.getType())) {
      return;
    }
    Base instance = event.getInstance();
    if (instance == null || instance.getId() == null) {
      return;
    }

    ScopeRuleValueType valueType = valueTypeFor(instance);
    if (valueType == null) {
      return;
    }

    try {
      int removed =
          workflowScopeRuleRepository.deleteByRuleValueAndValueType(instance.getId(), valueType);
      if (removed > 0) {
        log.debug(
            "Removed {} workflow scope rule(s) referencing deleted {} {}",
            removed,
            valueType,
            instance.getId());
      }
    } catch (Exception e) {
      // Best-effort cascade: never block the entity deletion because of scope-rule cleanup.
      log.error(
          "Failed to clean up workflow scope rules for deleted {} {}: {}",
          valueType,
          instance.getId(),
          e.getMessage());
    }
  }

  /**
   * Maps a deleted entity to the scope-rule value type that references it, or {@code null} when the
   * entity is not a scoped target kind. {@link io.openaev.database.model.Endpoint} and other
   * concrete assets are matched through their {@link Asset} supertype.
   */
  private ScopeRuleValueType valueTypeFor(Base instance) {
    if (instance instanceof AssetGroup) {
      return ScopeRuleValueType.ASSET_GROUP_ID;
    }
    if (instance instanceof Asset) {
      return ScopeRuleValueType.ASSET_ID;
    }
    if (instance instanceof Team) {
      return ScopeRuleValueType.TEAM_ID;
    }
    return null;
  }
}
