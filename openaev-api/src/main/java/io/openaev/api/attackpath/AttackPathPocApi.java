package io.openaev.api.attackpath;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POC-only attack-path endpoints (issue 6647). The whole controller is gated behind the {@code
 * ATTACK_PATH_POC} preview feature ({@code openaev.enabled-dev-features}), so it is not even
 * registered — and its routes return 404 — unless the feature is turned on. Tenant isolation is
 * enforced by the statement inspector from the request scope, not by hand.
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
  public AttackPathDTO graph(@PathVariable String simulationId) {
    return graphService.buildGraph(simulationId);
  }
}
