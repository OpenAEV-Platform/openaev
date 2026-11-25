package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.threat_arsenal.dto.ThreatArsenalActionUpdateInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.payload.PayloadVersionChangeService;
import io.openaev.service.threat_arsenal.ThreatArsenalService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Payload versions of a Threat Arsenal action, and moving injects between them (F30). */
@RestController
@RequiredArgsConstructor
public class ThreatArsenalVersionApi {

  private final PayloadVersionChangeService payloadVersionChangeService;
  private final ThreatArsenalService threatArsenalService;

  // -- READ --

  @LogExecutionTime
  @Operation(summary = "Versions of the action's payload, newest first")
  @GetMapping({
    THREAT_ARSENAL_URL + "/{actionId}/versions",
    TENANT_THREAT_ARSENAL_URL + "/{actionId}/versions"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public List<PayloadVersionOutput> versions(TxCtx ctx, @PathVariable String actionId) {
    return payloadVersionChangeService.versions(actionId);
  }

  @LogExecutionTime
  @Operation(
      summary = "Scenario injects, atomic tests and chained steps using the action's payload")
  @GetMapping({
    THREAT_ARSENAL_URL + "/{actionId}/usages",
    TENANT_THREAT_ARSENAL_URL + "/{actionId}/usages"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public List<PayloadUsageOutput> usages(TxCtx ctx, @PathVariable String actionId) {
    return payloadVersionChangeService.usages(actionId);
  }

  @LogExecutionTime
  @Operation(summary = "History of the action's payload across its versions, newest first")
  @GetMapping({
    THREAT_ARSENAL_URL + "/{actionId}/history",
    TENANT_THREAT_ARSENAL_URL + "/{actionId}/history"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public List<ContentHistoryOutput> history(TxCtx ctx, @PathVariable String actionId) {
    return payloadVersionChangeService.history(actionId);
  }

  @LogExecutionTime
  @Operation(
      summary = "What the action's payload version changes, field by field",
      description =
          "Compared with compared_to, another version of the same payload; without it, every field"
              + " of the version is added (a first version). Every field that makes a new version is"
              + " compared.")
  @GetMapping({
    THREAT_ARSENAL_URL + "/{actionId}/changes",
    TENANT_THREAT_ARSENAL_URL + "/{actionId}/changes"
  })
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public List<PayloadFieldChangeOutput> changes(
      TxCtx ctx,
      @PathVariable String actionId,
      @RequestParam(name = "compared_to", required = false) String comparedTo) {
    return threatArsenalService.versionChanges(actionId, comparedTo);
  }

  @LogExecutionTime
  @Operation(
      summary = "What a content would change in the action's payload version, field by field",
      description =
          "Payload drafts: compares the editor's content with the version, without saving"
              + " anything.")
  @PostMapping({
    THREAT_ARSENAL_URL + "/{actionId}/changes",
    TENANT_THREAT_ARSENAL_URL + "/{actionId}/changes"
  })
  // Read-only: the candidate version is built in memory and never flushed.
  @Transactional(readOnly = true)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.THREAT_ARSENAL)
  public List<PayloadFieldChangeOutput> contentChanges(
      TxCtx ctx,
      @PathVariable String actionId,
      // Not validated: a draft may still be incomplete.
      @RequestBody ThreatArsenalActionUpdateInput input) {
    return threatArsenalService.contentChanges(actionId, input);
  }

  // -- UPDATE --

  @LogExecutionTime
  @Operation(
      summary = "Move injects and steps to another version of the payload",
      description =
          "Each inject or step needs the right to update its scenario or atomic test, and arguments"
              + " the target version accepts; the others are returned as refused.")
  @PostMapping({
    THREAT_ARSENAL_URL + "/{actionId}/change-version",
    TENANT_THREAT_ARSENAL_URL + "/{actionId}/change-version"
  })
  @Transactional(rollbackFor = Exception.class)
  @AccessControl(
      resourceId = "#actionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.THREAT_ARSENAL)
  public PayloadVersionChangeOutput changeVersion(
      TxCtx ctx,
      @PathVariable String actionId,
      @Valid @RequestBody PayloadVersionChangeInput input) {
    return payloadVersionChangeService.changeVersion(actionId, input);
  }
}
