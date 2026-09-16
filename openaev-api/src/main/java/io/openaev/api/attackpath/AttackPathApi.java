package io.openaev.api.attackpath;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.config.SessionHelper;
import io.openaev.context.TxCtx;
import io.openaev.database.model.attackpath.projection.AttackPathSimSummaryRow;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.attackpath.AttackPathAccessControl;
import io.openaev.service.attackpath.AttackPathCausalSeedService;
import io.openaev.service.attackpath.AttackPathDeltaService;
import io.openaev.service.attackpath.AttackPathGraphService;
import io.openaev.service.attackpath.AttackPathSeedService;
import io.openaev.service.attackpath.dto.AttackPathCausalSeedResultDTO;
import io.openaev.service.attackpath.dto.AttackPathDTO;
import io.openaev.service.attackpath.dto.AttackPathDeltaDTO;
import io.openaev.service.attackpath.dto.AttackPathEndpointRelationsDTO;
import io.openaev.service.attackpath.dto.AttackPathExecutionDetailDTO;
import io.openaev.service.attackpath.dto.AttackPathExpandDTO;
import io.openaev.service.attackpath.dto.AttackPathFindingPageDTO;
import io.openaev.service.attackpath.dto.AttackPathReplayStepDTO;
import io.openaev.service.attackpath.dto.AttackPathSeedInput;
import io.openaev.service.attackpath.dto.AttackPathSeedResultDTO;
import io.openaev.service.attackpath.ingestion.AttackPathVersionService;
import io.openaev.utils.TxCtxScopeUtils;
import jakarta.validation.constraints.Min;
import java.util.Collection;
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
 * Attack-path endpoints (issue 6647).
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
  private final AttackPathDeltaService deltaService;
  private final AttackPathVersionService versionService;
  private final AttackPathSeedService seedService;
  private final AttackPathCausalSeedService causalSeedService;
  private final AttackPathAccessControl attackPathAccessControl;

  /**
   * The single tenant the seed writes under. Seeding is a one-tenant operation, so a scope that
   * does not resolve to exactly one tenant (none, or several from a multi-tenant header) is
   * rejected rather than silently picking one and seeding the wrong tenant.
   */
  private String requireSingleTenant(TxCtx ctx) {
    Collection<String> tenants = TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx);
    if (tenants.size() != 1) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "seed under exactly one tenant: use the /tenant/{id}/ route");
    }
    return tenants.iterator().next();
  }

  /**
   * Full graph of a simulation, built from two flat reads plus one in-memory pass.
   *
   * <p>RBAC: the annotation stays {@code skipRBAC = true} because it cannot express the seed
   * exception; resource-level {@code SIMULATION READ} is enforced in-method by {@link
   * AttackPathAccessControl#assertCanReadSimulation}, which lets synthetic seed ids through (they
   * are not real {@code exercises}). Every per-simulation read below does the same. Tenant
   * isolation is still enforced by the statement inspector.
   *
   * <p>The snapshot is labelled with the simulation's current attack-path version, which the client
   * then polls {@link #graphDelta} with. The version is read here, before the rows and in the same
   * read-only transaction: reading it after them would let a write commit in the gap and never
   * reach the client, whereas a version that lags the rows only costs one redundant first delta.
   */
  @GetMapping("/simulations/{simulationId}/graph")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public AttackPathDTO graph(
      TxCtx ctx, @PathVariable String simulationId, @RequestParam(required = false) String mode) {
    attackPathAccessControl.assertCanReadSimulation(simulationId);
    long graphVersion =
        versionService.current(simulationId, TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx)).orElse(0L);
    return graphService.buildGraph(simulationId, mode, graphVersion);
  }

  /**
   * What changed in the simulation's attack path since the client's version (#6647, spec 002), so a
   * running run can be followed without re-downloading the whole graph every few seconds.
   *
   * <p>Same guards as {@link #graph}, evaluated on every poll: {@code assertCanReadSimulation}, and
   * the {@link TxCtx} that scopes the reads to the caller's tenant. Because authorization is per
   * request, a permission lost between two polls takes effect on the next one — there is no
   * long-lived channel to carry a stale decision.
   *
   * <p>{@code since} is the version the client already holds ({@code graphVersion} of the snapshot
   * it loaded); 0 means "I have just loaded a snapshot with no version". A negative cursor is
   * rejected as a bad request rather than silently reinterpreted. A cursor the server cannot answer
   * comes back as {@code resyncRequired}, never as a partial graph.
   */
  @GetMapping("/simulations/{simulationId}/graph/delta")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public AttackPathDeltaDTO graphDelta(
      TxCtx ctx, @PathVariable String simulationId, @RequestParam @Min(0) long since) {
    attackPathAccessControl.assertCanReadSimulation(simulationId);
    return deltaService.buildDelta(simulationId, since, TxCtxScopeUtils.tenantIdsFromHTTPCtx(ctx));
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
    return attackPathAccessControl.retainReadable(graphService.listSimulations(scenarioId));
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
    attackPathAccessControl.assertCanReadSimulation(simulationId);
    return graphService.expandEndpoint(simulationId, ref);
  }

  /**
   * An endpoint's relations: one page of the executions targeting it plus the grouped edges into
   * it, whole. {@code ref} is the endpoint key (asset id or raw value), URL-encoded. The page is
   * size-capped ({@value #MAX_FINDINGS_PAGE_SIZE}) and clamped rather than rejected, like the
   * findings read next to it, so an over-sized request from an older client still answers.
   */
  @GetMapping("/simulations/{simulationId}/endpoint/relations")
  @Transactional(readOnly = true)
  @AccessControl(skipRBAC = true)
  public AttackPathEndpointRelationsDTO relations(
      TxCtx ctx,
      @PathVariable String simulationId,
      @RequestParam String ref,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "50") int size) {
    attackPathAccessControl.assertCanReadSimulation(simulationId);
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), MAX_FINDINGS_PAGE_SIZE);
    return graphService.endpointRelations(simulationId, ref, PageRequest.of(safePage, safeSize));
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
  public AttackPathSeedResultDTO seed(
      TxCtx ctx, @RequestBody(required = false) AttackPathSeedInput input) {
    if (!SessionHelper.currentUser().isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attack path seeding is admin-only");
    }
    AttackPathSeedInput params = input != null ? input : new AttackPathSeedInput(null, null, null);
    return seedService.generate(params.toParams(), params.tenantId());
  }

  /**
   * Generate a causal attack-path scenario under the request's tenant, for developing and demoing
   * the causal view at will: {@code scenario=realistic} (default) is one deep recon-to-DC chain,
   * {@code scenario=scaled} is {@code footholds} independent paths converging on one DC chokepoint,
   * {@code scenario=intrusion} is a deep host-to-host targeted intrusion that reads left to right,
   * and {@code scenario=ransomware} is a deep-and-wide kill chain (phishing to DCSync to
   * domain-wide impact) whose workstation tier and impacted fleet are both sized by {@code
   * footholds}. Admin-only, like {@link #seed}. Returns the id of the created simulation, whose
   * Attack path tab renders the seeded graph.
   *
   * <p>With {@code scenario=ransomware&live=step} the simulation is created empty and its kill
   * chain is landed one stage at a time by {@link #replayNextStage}, so an open view shows it build
   * up.
   *
   * <p>TODO(#6647): a development data generator; remove before production.
   */
  @PostMapping("/seed/causal")
  @Transactional
  @AccessControl(skipRBAC = true)
  public AttackPathCausalSeedResultDTO seedCausal(
      TxCtx ctx,
      @RequestParam(required = false, defaultValue = "realistic") String scenario,
      @RequestParam(required = false, defaultValue = "20") int footholds,
      @RequestParam(required = false, defaultValue = "") String live) {
    if (!SessionHelper.currentUser().isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attack path seeding is admin-only");
    }
    String tenantId = requireSingleTenant(ctx);
    String simulationId =
        switch (scenario) {
          case "realistic" -> causalSeedService.seedRealisticChain(tenantId);
          case "scaled" -> causalSeedService.seedScaledChain(tenantId, footholds);
          case "intrusion" -> causalSeedService.seedDeepIntrusion(tenantId, footholds);
          case "ransomware" ->
              "step".equals(live)
                  ? causalSeedService.createRansomwareSimulation(tenantId)
                  : causalSeedService.seedRansomwareChain(tenantId, footholds);
          default ->
              throw new ResponseStatusException(
                  HttpStatus.BAD_REQUEST, "unknown scenario: " + scenario);
        };
    return new AttackPathCausalSeedResultDTO(simulationId);
  }

  /**
   * Play the next stage of a live ransomware replay onto the simulation created with {@code
   * live=step}, and report which stage ran and whether the replay is complete. Admin-only like
   * {@link #seedCausal}. Call it repeatedly, or on a timer, to watch the kill chain build up stage
   * by stage in the Attack path tab.
   *
   * <p>TODO(#6647): a development data generator; remove before production.
   */
  @PostMapping("/simulations/{simulationId}/replay")
  @Transactional
  @AccessControl(skipRBAC = true)
  public AttackPathReplayStepDTO replayNextStage(
      TxCtx ctx,
      @PathVariable String simulationId,
      @RequestParam(required = false, defaultValue = "20") int footholds) {
    if (!SessionHelper.currentUser().isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Attack path seeding is admin-only");
    }
    String tenantId = requireSingleTenant(ctx);
    return causalSeedService.replayRansomwareNextStage(simulationId, tenantId, footholds);
  }
}
