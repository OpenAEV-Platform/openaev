package io.openaev.api.attackpath;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.config.SessionHelper;
import io.openaev.context.TxCtx;
import io.openaev.database.model.attackpath.projection.AttackPathSimSummaryRow;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.openaev.service.attackpath.AttackPathAccessControl;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.AttackPathSeedService;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathEndpointRelationsDTO;
import io.openaev.service.attackpath.dto.AttackPathExecutionDetailDTO;
import io.openaev.service.attackpath.dto.AttackPathExpandDTO;
import io.openaev.service.attackpath.dto.AttackPathFindingPageDTO;
import io.openaev.service.attackpath.dto.AttackPathSeedInput;
import io.openaev.service.attackpath.dto.AttackPathSeedResultDTO;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Attack-path endpoints (issue 6647). Gated behind the {@code ATTACK_PATH} preview feature: every
 * endpoint checks {@link PreviewFeatureService#isFeatureEnabled} and returns 404 unless the feature
 * is turned on ({@code openaev.enabled-dev-features}), the same way the platform's other preview
 * features gate their code.
 *
 * <p>Tenant isolation is enforced by the statement inspector, not by hand: each read declares a
 * {@link TxCtx} parameter, so the transaction aspect writes the request's tenant scope (from the
 * {@code {tenantId}} path or the {@code X-Tenant-Ids} header) into {@code app.current_tenants}, and
 * the inspector filters every read of the activated {@code attackpath_*} tables against it. Without
 * that parameter the scope would stay empty and the reads would fail closed.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping({AttackPathApi.ATTACK_PATH_URI, TENANT_PREFIX + "/attack-path"})
public class AttackPathApi extends RestBehavior {

  public static final String ATTACK_PATH_URI = "/api/attack-path";

  /** Upper bound on the findings page size, so a client cannot request an unbounded read. */
  private static final int MAX_FINDINGS_PAGE_SIZE = 200;

  private final AttackPathGraphService graphService;
  private final AttackPathSeedService seedService;
  private final PreviewFeatureService previewFeatureService;
  private final AttackPathAccessControl attackPathAccessControl;

  /** Runtime feature gate: return 404 unless the {@code ATTACK_PATH} preview feature is enabled. */
  private void requireAttackPathFeature() {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.ATTACK_PATH)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
  }

  /**
   * Full graph of a simulation, built from two flat reads plus one in-memory pass.
   *
   * <p>RBAC: the annotation stays {@code skipRBAC = true} because it cannot express the seed
   * exception; resource-level {@code SIMULATION READ} is enforced in-method by {@link
   * AttackPathAccessControl#assertCanReadSimulation}, which lets synthetic seed ids through (they
   * are not real {@code exercises}). Every per-simulation read below does the same. Tenant
   * isolation is still enforced by the statement inspector.
   */
  @GetMapping("/simulations/{simulationId}/graph")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public AttackPathDTO graph(
      TxCtx ctx, @PathVariable String simulationId, @RequestParam(required = false) String mode) {
    requireAttackPathFeature();
    attackPathAccessControl.assertCanReadSimulation(simulationId);
    return graphService.buildGraph(simulationId, mode);
  }

  /**
   * Simulations that have attack-path data (id, endpoint count, execution count), for the front's
   * picker. Tenant-scoped through the {@link TxCtx}. When {@code scenarioId} is given (scenario
   * context, #6647 B0), the query is restricted to that scenario's simulations server-side. The
   * rows are then grant-filtered to the ones the caller can READ (seed rows kept), so the picker
   * never leaks a simulation the user is not granted on.
   */
  @GetMapping("/simulations")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public List<AttackPathSimSummaryRow> simulations(
      TxCtx ctx, @RequestParam(required = false) String scenarioId) {
    requireAttackPathFeature();
    return graphService.listSimulations(scenarioId).stream()
        .filter(row -> attackPathAccessControl.canReadSimulation(row.simulationId()))
        .toList();
  }

  /**
   * Expand an endpoint into its finding types and findings, from a single indexed read. {@code ref}
   * is the endpoint key (asset id or the raw value of a discovered endpoint), URL-encoded.
   */
  @GetMapping("/simulations/{simulationId}/endpoint/findings")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public AttackPathExpandDTO expandEndpointFindings(
      TxCtx ctx, @PathVariable String simulationId, @RequestParam String ref) {
    requireAttackPathFeature();
    attackPathAccessControl.assertCanReadSimulation(simulationId);
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
    requireAttackPathFeature();
    attackPathAccessControl.assertCanReadSimulation(simulationId);
    return graphService.endpointRelations(simulationId, ref);
  }

  /**
   * A page of a widget category's findings for the drawer (issue 5048). {@code category} is one of
   * {@code credentials|users|files|cves}; an unknown category returns an empty page. The page is
   * size-capped ({@value #MAX_FINDINGS_PAGE_SIZE}) so a client cannot request an unbounded read.
   * Each item carries the endpoint's map node id and its producing execution ids for the front's
   * cross-focus. Tenant-scoped through the {@link TxCtx} like every other read.
   */
  @GetMapping("/simulations/{simulationId}/findings")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public AttackPathFindingPageDTO findings(
      TxCtx ctx,
      @PathVariable String simulationId,
      @RequestParam String category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    requireAttackPathFeature();
    attackPathAccessControl.assertCanReadSimulation(simulationId);
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_FINDINGS_PAGE_SIZE);
    return graphService.listFindings(simulationId, category, PageRequest.of(safePage, safeSize));
  }

  /**
   * One execution's Result &amp; Terminal detail for the drawer (issue 5048), from the frozen
   * snapshot. 404 when the execution is not in the caller's simulation (unknown id or another
   * tenant's, since the read is tenant-scoped through the {@link TxCtx}). Credentials are masked
   * server-side in the command, the output, and the findings.
   *
   * <p>The execution id is passed as a URL-encoded {@code ref} query parameter (like the endpoint
   * reads), not a path variable: an injector-sourced execution id ends with the null-agent marker
   * {@code \0} (a backslash), and an encoded backslash in the path is rejected by the servlet
   * container before it reaches the controller. A query parameter carries it safely.
   */
  @GetMapping("/simulations/{simulationId}/execution")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public AttackPathExecutionDetailDTO executionDetail(
      TxCtx ctx, @PathVariable String simulationId, @RequestParam("ref") String executionId) {
    requireAttackPathFeature();
    attackPathAccessControl.assertCanReadSimulation(simulationId);
    AttackPathExecutionDetailDTO detail = graphService.executionDetail(simulationId, executionId);
    if (detail == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return detail;
  }

  /**
   * Generate a synthetic dataset for the scaling tests. Admin-only, and only reachable when the
   * feature flag is on. The generator sets {@code tenant_id} on every row itself: by default it
   * writes its own synthetic tenants, or, when the body carries a {@code tenantId}, under that
   * existing tenant so the seeded simulations are visible in the front for it.
   *
   * <p>TODO(#6647): remove this seed generator from the API before production. It is a development
   * data generator; production data comes from ingesting real simulation executions.
   */
  @PostMapping("/seed")
  @Transactional
  @AccessControl(skipRBAC = true)
  public AttackPathSeedResultDTO seed(@RequestBody(required = false) AttackPathSeedInput input) {
    requireAttackPathFeature();
    if (!SessionHelper.currentUser().isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attack path seeding is admin-only");
    }
    AttackPathSeedInput params = input != null ? input : new AttackPathSeedInput(null, null, null);
    return seedService.generate(params.toParams(), params.tenantId());
  }
}
