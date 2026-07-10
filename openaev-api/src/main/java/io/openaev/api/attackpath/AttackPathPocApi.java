package io.openaev.api.attackpath;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathEndpointRelationsDTO;
import io.openaev.service.attackpath.dto.AttackPathExpandDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * POC-only attack-path endpoints (issue 6647). The whole controller is gated behind the {@code
 * ATTACK_PATH_POC} preview feature ({@code openaev.enabled-dev-features}), so it is not even
 * registered — and its routes return 404 — unless the feature is turned on.
 *
 * <p>Tenant isolation is enforced by the statement inspector, not by hand: each read declares a
 * {@link TxCtx} parameter, so the transaction aspect writes the request's tenant scope (from the
 * {@code {tenantId}} path or the {@code X-Tenant-Ids} header) into {@code app.current_tenants}, and
 * the inspector filters every read of the activated {@code attackpath_*} tables against it. Without
 * that parameter the scope would stay empty and the reads would fail closed.
 */
@RestController
@RequiredArgsConstructor
@Conditional(AttackPathPocCondition.class)
@RequestMapping({AttackPathPocApi.ATTACK_PATH_POC_URI, TENANT_PREFIX + "/poc/attack-path"})
public class AttackPathPocApi extends RestBehavior {

  public static final String ATTACK_PATH_POC_URI = "/api/poc/attack-path";

  private final AttackPathGraphService graphService;

  /**
   * Full graph of a simulation, built from two flat reads plus one in-memory pass. {@code
   * skipRBAC}: synthetic seeded simulations are not real {@code exercises}, so resource-level RBAC
   * cannot resolve them; tenant isolation is still enforced by the statement inspector.
   */
  @GetMapping("/simulations/{simulationId}/graph")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public AttackPathDTO graph(TxCtx ctx, @PathVariable String simulationId) {
    return graphService.buildGraph(simulationId);
  }

  /**
   * Expand an endpoint into its finding types and findings, from a single indexed read. {@code ref}
   * is the endpoint key (asset id or the raw value of a discovered endpoint), URL-encoded.
   */
  @GetMapping("/simulations/{simulationId}/endpoint/findings")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public AttackPathExpandDTO expand(
      TxCtx ctx, @PathVariable String simulationId, @RequestParam String ref) {
    return graphService.expandEndpoint(simulationId, ref);
  }

  /**
   * An endpoint's relations: its executions and the grouped edges into it, from a single indexed
   * read. {@code ref} is the endpoint key (asset id or raw value), URL-encoded.
   */
  @GetMapping("/simulations/{simulationId}/endpoint/relations")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public AttackPathEndpointRelationsDTO relations(
      TxCtx ctx, @PathVariable String simulationId, @RequestParam String ref) {
    return graphService.endpointRelations(simulationId, ref);
  }
}
