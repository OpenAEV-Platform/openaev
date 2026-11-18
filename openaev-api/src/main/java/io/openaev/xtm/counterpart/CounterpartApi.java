package io.openaev.xtm.counterpart;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.xtm.counterpart.form.CounterpartLookupInput;
import io.openaev.xtm.counterpart.form.CounterpartOutput;
import io.openaev.xtm.counterpart.form.XtmConnectionOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Cross-platform awareness: which connected XTM products this tenant reaches, and which of our
 * objects they also hold. Each lookup is guarded by the right to search the object kind it reads.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping(CounterpartApi.XTM_API)
@Tag(name = "XTM counterparts", description = "Objects shared with the other XTM products")
public class CounterpartApi extends RestBehavior {

  public static final String XTM_API = TENANT_PREFIX + "/xtm";

  private final CounterpartService counterpartService;

  @LogExecutionTime
  @GetMapping("/connections")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  @Operation(summary = "List the XTM products this tenant is connected to")
  public List<XtmConnectionOutput> connections(TxCtx ctx, @PathVariable String tenantId) {
    return counterpartService.connections(tenantId).stream()
        .map(XtmConnectionOutput::from)
        .toList();
  }

  @LogExecutionTime
  @PostMapping("/counterparts/assets")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  @Operation(summary = "Find assets in the connected XTM products")
  public List<CounterpartOutput> assets(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return lookup(tenantId, CounterpartKind.ASSET, input);
  }

  @LogExecutionTime
  @PostMapping("/counterparts/attack-patterns")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATTACK_PATTERN)
  @Operation(summary = "Find attack patterns in the connected XTM products")
  public List<CounterpartOutput> attackPatterns(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return lookup(tenantId, CounterpartKind.ATTACK_PATTERN, input);
  }

  @LogExecutionTime
  @PostMapping("/counterparts/vulnerabilities")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.VULNERABILITY)
  @Operation(summary = "Find vulnerabilities in the connected XTM products")
  public List<CounterpartOutput> vulnerabilities(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return lookup(tenantId, CounterpartKind.VULNERABILITY, input);
  }

  @LogExecutionTime
  @PostMapping("/counterparts/threats")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  @Operation(summary = "Find scenario threat sources in the connected XTM products")
  public List<CounterpartOutput> threats(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return lookup(tenantId, CounterpartKind.THREAT, input);
  }

  @LogExecutionTime
  @PostMapping("/counterparts/alerts")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Find collected alerts in the connected XTM products, by alert link")
  public List<CounterpartOutput> alerts(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return lookup(tenantId, CounterpartKind.ALERT, input);
  }

  private List<CounterpartOutput> lookup(
      String tenantId, CounterpartKind kind, CounterpartLookupInput input) {
    return counterpartService.resolve(tenantId, kind, input.keys()).stream()
        .map(CounterpartOutput::from)
        .toList();
  }
}
