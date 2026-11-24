package io.openaev.xtm.counterpart;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RequirePreviewFeature;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.User;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PermissionService;
import io.openaev.service.UserService;
import io.openaev.xtm.counterpart.form.CounterpartLookupInput;
import io.openaev.xtm.counterpart.form.CounterpartOutput;
import io.openaev.xtm.counterpart.form.LocalObjectOutput;
import io.openaev.xtm.counterpart.form.XtmConnectionOutput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Cross-platform awareness: which connected XTM products this tenant reaches, and which of our
 * objects they also hold. Each lookup is guarded by the right to search the object kind it reads.
 * Behind the dedicated preview feature {@code XTM_COUNTERPARTS} (off by default; Sam, 23 Nov 2026),
 * apart from the exploration graph ({@code EXPLORATION_GRAPH}): while it is off every route answers
 * a 404 before any lookup runs, so no call goes out to a connected product.
 *
 * <p>The counterpart lookups call the connected products over the network and read no table, so
 * they run outside any database transaction: a slow remote product never holds a connection of the
 * pool.
 */
@RestController
@RequiredArgsConstructor
@RequirePreviewFeature(PreviewFeature.XTM_COUNTERPARTS)
@RequestMapping(CounterpartApi.XTM_API)
@Tag(name = "XTM counterparts", description = "Objects shared with the other XTM products")
public class CounterpartApi extends RestBehavior {

  public static final String XTM_API = TENANT_PREFIX + "/xtm";

  private final CounterpartService counterpartService;
  private final LocalObjectService localObjectService;
  private final UserService userService;
  private final PermissionService permissionService;

  @LogExecutionTime
  @GetMapping("/connections")
  @Transactional(readOnly = true)
  // Open to every user of the tenant: it answers product names, connection flags and base urls,
  // never a credential, and the front needs it on every page that shows a pivot.
  @AccessControl(skipRBAC = true)
  @Operation(summary = "List the XTM products this tenant is connected to")
  public List<XtmConnectionOutput> connections(TxCtx ctx, @PathVariable String tenantId) {
    return counterpartService.connections(tenantId).stream()
        .map(XtmConnectionOutput::from)
        .toList();
  }

  @LogExecutionTime
  @PostMapping("/counterparts/assets")
  // A remote call to the connected products: no database transaction is held while it waits.
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  @Operation(summary = "Find assets in the connected XTM products")
  public List<CounterpartOutput> assets(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return lookup(tenantId, CounterpartKind.ASSET, input);
  }

  @LogExecutionTime
  @PostMapping("/counterparts/attack-patterns")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATTACK_PATTERN)
  @Operation(summary = "Find attack patterns in the connected XTM products")
  public List<CounterpartOutput> attackPatterns(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return lookup(tenantId, CounterpartKind.ATTACK_PATTERN, input);
  }

  @LogExecutionTime
  @PostMapping("/counterparts/vulnerabilities")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.VULNERABILITY)
  @Operation(summary = "Find vulnerabilities in the connected XTM products")
  public List<CounterpartOutput> vulnerabilities(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return lookup(tenantId, CounterpartKind.VULNERABILITY, input);
  }

  @LogExecutionTime
  @PostMapping("/counterparts/threats")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  @Operation(summary = "Find scenario threat sources in the connected XTM products")
  public List<CounterpartOutput> threats(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return lookup(tenantId, CounterpartKind.THREAT, input);
  }

  @LogExecutionTime
  @PostMapping("/counterparts/alerts")
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION)
  @Operation(summary = "Find collected alerts in the connected XTM products, by alert link")
  public List<CounterpartOutput> alerts(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return lookup(tenantId, CounterpartKind.ALERT, input);
  }

  // -- REVERSE LOOKUP: what another XTM product asks of this OpenAEV --
  // OpenCTI calls these with the keys of its own objects to show "Open in OpenAEV". Each read is
  // guarded by the right to search the kind, and runs in the caller's tenant scope. Scenario and
  // inject results are grant-managed: the search right only opens the endpoint, each object is then
  // kept only when the caller can read it (access(ctx)), and finding counts need the findings
  // right.

  @LogExecutionTime
  @PostMapping("/objects/assets")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  @Operation(summary = "Find our assets by hostname, for another XTM product")
  public List<LocalObjectOutput> localAssets(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return local(ctx, CounterpartKind.ASSET, input);
  }

  @LogExecutionTime
  @PostMapping("/objects/attack-patterns")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATTACK_PATTERN)
  @Operation(summary = "Find our attack patterns by MITRE id, for another XTM product")
  public List<LocalObjectOutput> localAttackPatterns(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return local(ctx, CounterpartKind.ATTACK_PATTERN, input);
  }

  @LogExecutionTime
  @PostMapping("/objects/vulnerabilities")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.VULNERABILITY)
  @Operation(summary = "Find our vulnerabilities by CVE id, for another XTM product")
  public List<LocalObjectOutput> localVulnerabilities(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return local(ctx, CounterpartKind.VULNERABILITY, input);
  }

  @LogExecutionTime
  @PostMapping("/objects/threats")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  @Operation(summary = "Find our scenarios generated from the given STIX ids")
  public List<LocalObjectOutput> localThreats(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return local(ctx, CounterpartKind.THREAT, input);
  }

  @LogExecutionTime
  @PostMapping("/objects/alerts")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.INJECT)
  @Operation(summary = "Find the injects our collected alerts answered, by alert link")
  public List<LocalObjectOutput> localAlerts(
      TxCtx ctx, @PathVariable String tenantId, @Valid @RequestBody CounterpartLookupInput input) {
    return local(ctx, CounterpartKind.ALERT, input);
  }

  private List<LocalObjectOutput> local(
      TxCtx ctx, CounterpartKind kind, CounterpartLookupInput input) {
    return localObjectService.find(kind, input.keys(), access(ctx)).stream()
        .map(LocalObjectOutput::from)
        .toList();
  }

  /** The caller's read rights on each returned object, in every tenant of the request scope. */
  private LocalObjectService.Access access(TxCtx ctx) {
    User user = userService.currentUser();
    return new LocalObjectService.Access() {
      @Override
      public boolean canReadScenario(String scenarioId) {
        return can(scenarioId, ResourceType.SCENARIO);
      }

      @Override
      public boolean canReadSimulation(String simulationId) {
        return can(simulationId, ResourceType.SIMULATION);
      }

      @Override
      public boolean canReadAtomicTesting(String injectId) {
        return can(injectId, ResourceType.ATOMIC_TESTING);
      }

      @Override
      public boolean canReadFindings() {
        return can(null, ResourceType.FINDING);
      }

      private boolean can(String id, ResourceType type) {
        return permissionService.holdsInScope(
            ctx,
            user,
            caller ->
                permissionService.hasPermission(caller, Optional.empty(), id, type, Action.READ));
      }
    };
  }

  private List<CounterpartOutput> lookup(
      String tenantId, CounterpartKind kind, CounterpartLookupInput input) {
    String userId = userService.currentUser().getId();
    return counterpartService.resolve(tenantId, userId, kind, input.keys()).stream()
        .map(CounterpartOutput::from)
        .toList();
  }
}
