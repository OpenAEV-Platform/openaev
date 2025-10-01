package io.openaev.rest.kill_chain_phase;

import static io.openaev.database.specification.KillChainPhaseSpecification.byName;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.KillChainPhase;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseCreateInput;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseUpdateInput;
import io.openaev.rest.kill_chain_phase.form.KillChainPhaseUpsertInput;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class KillChainPhaseApi extends RestBehavior {

  public static final String KILL_CHAIN_PHASE_URI = "/api/kill_chain_phases";

  private final KillChainPhaseRepository killChainPhaseRepository;

  @GetMapping("/api/kill_chain_phases")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.KILL_CHAIN_PHASE)
  public Iterable<KillChainPhase> killChainPhases() {
    return killChainPhaseRepository.findAll();
  }

  @PostMapping("/api/kill_chain_phases/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.KILL_CHAIN_PHASE)
  public Page<KillChainPhase> killChainPhases(
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<KillChainPhase> specification, Pageable pageable) ->
            this.killChainPhaseRepository.findAll(specification, pageable),
        searchPaginationInput,
        KillChainPhase.class);
  }

  @GetMapping("/api/kill_chain_phases/{killChainPhaseId}")
  @RBAC(
      resourceId = "#killChainPhaseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.KILL_CHAIN_PHASE)
  public KillChainPhase killChainPhase(@PathVariable String killChainPhaseId) {
    return killChainPhaseRepository
        .findById(killChainPhaseId)
        .orElseThrow(ElementNotFoundException::new);
  }

  @PutMapping("/api/kill_chain_phases/{killChainPhaseId}")
  @RBAC(
      resourceId = "#killChainPhaseId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.KILL_CHAIN_PHASE)
  @Transactional(rollbackOn = Exception.class)
  public KillChainPhase updateKillChainPhase(
      @PathVariable String killChainPhaseId, @Valid @RequestBody KillChainPhaseUpdateInput input) {
    KillChainPhase killchainPhase =
        killChainPhaseRepository
            .findById(killChainPhaseId)
            .orElseThrow(ElementNotFoundException::new);
    killchainPhase.setUpdateAttributes(input);
    killchainPhase.setUpdatedAt(Instant.now());
    return killChainPhaseRepository.save(killchainPhase);
  }

  @PostMapping("/api/kill_chain_phases")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.KILL_CHAIN_PHASE)
  @Transactional(rollbackOn = Exception.class)
  public KillChainPhase createKillChainPhase(@Valid @RequestBody KillChainPhaseCreateInput input) {
    KillChainPhase killChainPhase = new KillChainPhase();
    killChainPhase.setUpdateAttributes(input);
    return killChainPhaseRepository.save(killChainPhase);
  }

  @PostMapping("/api/kill_chain_phases/upsert")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.KILL_CHAIN_PHASE)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<KillChainPhase> upsertKillChainPhases(
      @Valid @RequestBody KillChainPhaseUpsertInput input) {
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
            newKillChainPhase.setOrder(
                Optional.ofNullable(KillChainPhaseUtils.orderFromMitreAttack().get(shortName))
                    .orElse(0L));
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

  @DeleteMapping("/api/kill_chain_phases/{killChainPhaseId}")
  @RBAC(
      resourceId = "#killChainPhaseId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.KILL_CHAIN_PHASE)
  public void deleteKillChainPhase(@PathVariable String killChainPhaseId) {
    killChainPhaseRepository.deleteById(killChainPhaseId);
  }

  // -- OPTION --

  @GetMapping(KILL_CHAIN_PHASE_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.KILL_CHAIN_PHASE)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return fromIterable(
            this.killChainPhaseRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "order")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping(KILL_CHAIN_PHASE_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.KILL_CHAIN_PHASE)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.killChainPhaseRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
