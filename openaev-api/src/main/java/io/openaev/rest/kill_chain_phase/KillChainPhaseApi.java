package io.openaev.rest.kill_chain_phase;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.KillChainPhaseSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.AccessControl;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.KillChainPhase;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseCreateInput;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseUpdateInput;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseUpsertInput;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping({KillChainPhaseApi.KILL_CHAIN_PHASE_URI, TENANT_PREFIX + "/kill_chain_phases"})
public class KillChainPhaseApi extends RestBehavior {

  public static final String KILL_CHAIN_PHASE_URI = "/api/kill_chain_phases";

  private final KillChainPhaseRepository killChainPhaseRepository;
  private final TenantWriteScopeResolver writeScopeResolver;

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
        this.killChainPhaseRepository::findAll, searchPaginationInput, KillChainPhase.class);
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
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    KillChainPhase killChainPhase = new KillChainPhase();
    killChainPhase.setUpdateAttributes(input);
    killChainPhase.setTenant(new Tenant(tenantId));
    return killChainPhaseRepository.save(killChainPhase);
  }

  @PostMapping("/upsert")
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.KILL_CHAIN_PHASE)
  @Transactional(rollbackFor = Exception.class)
  public Iterable<KillChainPhase> upsertKillChainPhases(
      TxCtx ctx, @Valid @RequestBody KillChainPhaseUpsertInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    List<KillChainPhase> upserted = new ArrayList<>();
    List<KillChainPhaseCreateInput> inputKillChainPhases = input.getKillChainPhases();
    inputKillChainPhases.forEach(
        killChainPhaseCreateInput -> {
          String killChainName = killChainPhaseCreateInput.getKillChainName();
          String shortName = killChainPhaseCreateInput.getShortName();
          Optional<KillChainPhase> optionalKillChainPhase =
              killChainPhaseRepository.findByKillChainNameAndShortName(killChainName, shortName);
          if (optionalKillChainPhase.isEmpty()) {
            KillChainPhase newKillChainPhase = new KillChainPhase();
            newKillChainPhase.setKillChainName(killChainName);
            newKillChainPhase.setStixId(killChainPhaseCreateInput.getStixId());
            newKillChainPhase.setExternalId(killChainPhaseCreateInput.getExternalId());
            newKillChainPhase.setShortName(shortName);
            newKillChainPhase.setName(killChainPhaseCreateInput.getName());
            newKillChainPhase.setDescription(killChainPhaseCreateInput.getDescription());
            // Honor an explicit, non-zero order from the input (used by importers that know their
            // own
            // matrix ordering, e.g. MITRE ATLAS); otherwise resolve the canonical order from the
            // kill chain name + short name (mitre-attack or mitre-atlas).
            Long inputOrder = killChainPhaseCreateInput.getOrder();
            newKillChainPhase.setOrder(
                inputOrder != null && inputOrder != 0L
                    ? inputOrder
                    : KillChainPhaseUtils.orderFor(killChainName, shortName));
            newKillChainPhase.setTenant(new Tenant(tenantId));
            upserted.add(newKillChainPhase);
          } else {
            KillChainPhase killChainPhase = optionalKillChainPhase.get();
            killChainPhase.setStixId(killChainPhaseCreateInput.getStixId());
            killChainPhase.setShortName(killChainPhaseCreateInput.getShortName());
            killChainPhase.setName(killChainPhaseCreateInput.getName());
            killChainPhase.setExternalId(killChainPhaseCreateInput.getExternalId());
            killChainPhase.setDescription(killChainPhaseCreateInput.getDescription());
            upserted.add(killChainPhase);
          }
        });
    return this.killChainPhaseRepository.saveAll(upserted);
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
                byName(searchText), Sort.by(Sort.Direction.ASC, "order")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping("/options")
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.KILL_CHAIN_PHASE)
  public List<FilterUtilsJpa.Option> optionsById(TxCtx ctx, @RequestBody final List<String> ids) {
    return fromIterable(this.killChainPhaseRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
