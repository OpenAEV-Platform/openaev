package io.openaev.api.threat_arsenal;

import static io.openaev.api.threat_arsenal.ThreatArsenalApi.TENANT_THREAT_ARSENAL_URL;
import static io.openaev.api.threat_arsenal.ThreatArsenalApi.THREAT_ARSENAL_URL;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.payload.PayloadVersionChangeService;
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
