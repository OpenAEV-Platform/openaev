package io.openaev.rest.kill_chain_phase;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.KillChainPhaseSpecification.byNameOrKillChainName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.KillChainPhase;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseCreateInput;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseUpdateInput;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseUpsertInput;
import io.openaev.rest.kill_chain_phase.service.KillChainPhaseService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping({KillChainPhaseApi.KILL_CHAIN_PHASE_URI, TENANT_PREFIX + "/kill_chain_phases"})
public class KillChainPhaseApi extends RestBehavior {

  public static final String KILL_CHAIN_PHASE_URI = "/api/kill_chain_phases";

  private final KillChainPhaseRepository killChainPhaseRepository;
  private final KillChainPhaseService killChainPhaseService;

  @GetMapping
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.KILL_CHAIN_PHASE)
  public Iterable<KillChainPhase> killChainPhases(TxCtx ctx) {
    return killChainPhaseRepository.findAll();
  }

  @PostMapping("/search")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.KILL_CHAIN_PHASE)
  public Page<KillChainPhase> killChainPhases(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<KillChainPhase> specification, Pageable pageable) ->
            this.killChainPhaseRepository.findAll(specification, pageable),
        searchPaginationInput,
        KillChainPhase.class);
  }

  @GetMapping("/{killChainPhaseId}")
  @Transactional
  @AccessControl(
      resourceId = "#killChainPhaseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.KILL_CHAIN_PHASE)
  public KillChainPhase killChainPhase(TxCtx ctx, @PathVariable String killChainPhaseId) {
    return killChainPhaseRepository
        .findById(killChainPhaseId)
        .orElseThrow(ElementNotFoundException::new);
  }

  @PutMapping("/{killChainPhaseId}")
  @AccessControl(
      resourceId = "#killChainPhaseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.KILL_CHAIN_PHASE)
  @Transactional(rollbackFor = Exception.class)
  public KillChainPhase updateKillChainPhase(
      TxCtx ctx,
      @PathVariable String killChainPhaseId,
      @Valid @RequestBody KillChainPhaseUpdateInput input) {
    KillChainPhase killchainPhase =
        killChainPhaseRepository
            .findById(killChainPhaseId)
            .orElseThrow(ElementNotFoundException::new);
    killchainPhase.setUpdateAttributes(input);
    killchainPhase.setUpdatedAt(Instant.now());
    return killChainPhaseRepository.save(killchainPhase);
  }

  @PostMapping
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.KILL_CHAIN_PHASE)
  @Transactional(rollbackFor = Exception.class)
  public KillChainPhase createKillChainPhase(
      TxCtx ctx, @Valid @RequestBody KillChainPhaseCreateInput input) {
    KillChainPhase killChainPhase = new KillChainPhase();
    killChainPhase.setUpdateAttributes(input);
    return killChainPhaseRepository.save(killChainPhase);
  }

  /**
   * Race-safe upsert. Several collectors (MITRE Enterprise / Mobile / ICS, Atlas...) call this
   * endpoint concurrently right after platform startup; two threads can both miss the lookup for a
   * brand new phase and both insert it, so the loser fails on the {@code
   * kill_chain_phases_stix_id_tenant_unique} constraint. The losing transaction is fully rolled
   * back, therefore a single retry in a fresh transaction sees the winner's committed row and
   * updates it instead of inserting. {@code NOT_SUPPORTED} keeps this endpoint outside any
   * transaction: each service call must run in its own transaction for the retry to work.
   */
  @PostMapping("/upsert")
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.KILL_CHAIN_PHASE)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Iterable<KillChainPhase> upsertKillChainPhases(
      TxCtx ctx, @Valid @RequestBody KillChainPhaseUpsertInput input) {
    try {
      return killChainPhaseService.upsertKillChainPhases(input.getKillChainPhases());
    } catch (DataIntegrityViolationException e) {
      if (!isKillChainPhaseUniqueViolation(e)) {
        throw e;
      }
      log.warn(
          "Kill chain phase upsert lost a concurrent-insert race, retrying once: {}",
          e.getMessage());
      return killChainPhaseService.upsertKillChainPhases(input.getKillChainPhases());
    }
  }

  /**
   * Only a duplicate-key violation on one of the kill_chain_phases unique constraints is a
   * concurrent-insert race worth retrying; any other integrity failure (not-null, foreign key...)
   * would fail again identically and must propagate immediately.
   */
  private static boolean isKillChainPhaseUniqueViolation(DataIntegrityViolationException e) {
    return e.getCause() instanceof ConstraintViolationException constraintViolation
        && constraintViolation.getConstraintName() != null
        && constraintViolation.getConstraintName().startsWith("kill_chain_phases_");
  }

  @DeleteMapping("/{killChainPhaseId}")
  @Transactional
  @AccessControl(
      resourceId = "#killChainPhaseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.KILL_CHAIN_PHASE)
  public void deleteKillChainPhase(TxCtx ctx, @PathVariable String killChainPhaseId) {
    killChainPhaseRepository.deleteById(killChainPhaseId);
  }

  // -- OPTION --

  @GetMapping("/options")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.KILL_CHAIN_PHASE)
  public List<FilterUtilsJpa.Option> optionsByName(
      TxCtx ctx, @RequestParam(required = false) final String searchText) {
    return fromIterable(
            this.killChainPhaseRepository.findAll(
                byNameOrKillChainName(searchText),
                Sort.by(Sort.Order.asc("killChainName"), Sort.Order.asc("order"))))
        .stream()
        .map(KillChainPhaseApi::toOption)
        .toList();
  }

  @PostMapping("/options")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.KILL_CHAIN_PHASE)
  public List<FilterUtilsJpa.Option> optionsById(TxCtx ctx, @RequestBody final List<String> ids) {
    return fromIterable(this.killChainPhaseRepository.findAllById(ids)).stream()
        .map(KillChainPhaseApi::toOption)
        .toList();
  }

  /**
   * The platform is multi kill chain: phase names are only unique within their kill chain, so
   * options are always labelled "[kill chain] phase" to disambiguate.
   */
  private static FilterUtilsJpa.Option toOption(KillChainPhase phase) {
    return new FilterUtilsJpa.Option(
        phase.getId(), "[" + phase.getKillChainName() + "] " + phase.getName());
  }
}
