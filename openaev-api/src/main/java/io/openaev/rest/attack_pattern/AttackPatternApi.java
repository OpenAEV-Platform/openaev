package io.openaev.rest.attack_pattern;

import static io.openaev.database.specification.AttackPatternSpecification.byName;
import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.RBAC;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawAttackPattern;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.database.repository.InjectorContractRepository;
import io.openaev.database.repository.KillChainPhaseRepository;
import io.openaev.database.specification.InjectorContractSpecification;
import io.openaev.rest.attack_pattern.form.AttackPatternCreateInput;
import io.openaev.rest.attack_pattern.form.AttackPatternUpdateInput;
import io.openaev.rest.attack_pattern.form.AttackPatternUpsertInput;
import io.openaev.rest.attack_pattern.service.AttackPatternService;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AttackPatternApi extends RestBehavior {

  public static final String ATTACK_PATTERN_URI = "/api/attack_patterns";

  private final AttackPatternService attackPatternService;

  private final AttackPatternRepository attackPatternRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final KillChainPhaseRepository killChainPhaseRepository;

  @GetMapping("/api/attack_patterns")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.ATTACK_PATTERN)
  public List<RawAttackPattern> attackPatterns() {
    return attackPatternRepository.rawAll();
  }

  @PostMapping("/api/attack_patterns/search")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATTACK_PATTERN)
  public Page<AttackPattern> attackPatterns(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<AttackPattern> specification, Pageable pageable) ->
            this.attackPatternRepository.findAll(specification, pageable),
        searchPaginationInput,
        AttackPattern.class);
  }

  @PostMapping(ATTACK_PATTERN_URI + "/search-with-ai")
  @RBAC(skipRBAC = true)
  @Operation(
      summary = "Extract Attack Paterns from text or files using AI",
      description = "Get attack patterns ids extracted from a text or files using AI")
  public List<String> searchAttackPatternWithTTPAIWebservice(
      @RequestPart("files") @Nullable List<MultipartFile> files,
      @RequestPart("text") @Nullable final String text) {
    return attackPatternService.searchAttackPatternWithTTPAIWebservice(
        files == null ? new ArrayList<>() : files, text == null ? "" : text);
  }

  @GetMapping("/api/attack_patterns/{attackPatternId}")
  @RBAC(
      resourceId = "#attackPatternId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ATTACK_PATTERN)
  public AttackPattern attackPattern(@PathVariable String attackPatternId) {
    return attackPatternRepository
        .findById(attackPatternId)
        .orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping("/api/attack_patterns")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.ATTACK_PATTERN)
  @Transactional(rollbackOn = Exception.class)
  public AttackPattern createAttackPattern(@Valid @RequestBody AttackPatternCreateInput input) {
    AttackPattern attackPattern = new AttackPattern();
    attackPattern.setUpdateAttributes(input);
    attackPattern.setKillChainPhases(
        fromIterable(killChainPhaseRepository.findAllById(input.getKillChainPhasesIds())));
    attackPattern.setParent(
        updateRelation(input.getParentId(), attackPattern.getParent(), attackPatternRepository));
    return attackPatternRepository.save(attackPattern);
  }

  @GetMapping("/api/attack_patterns/{attackPatternId}/injector_contracts")
  @RBAC(
      resourceId = "#attackPatternId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ATTACK_PATTERN)
  public Iterable<InjectorContract> injectorContracts(@PathVariable String attackPatternId) {
    attackPatternRepository.findById(attackPatternId).orElseThrow(ElementNotFoundException::new);
    return injectorContractRepository.findAll(
        InjectorContractSpecification.fromAttackPattern(attackPatternId));
  }

  @PutMapping("/api/attack_patterns/{attackPatternId}")
  @RBAC(
      resourceId = "#attackPatternId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.ATTACK_PATTERN)
  @Transactional(rollbackOn = Exception.class)
  public AttackPattern updateAttackPattern(
      @NotBlank @PathVariable final String attackPatternId,
      @Valid @RequestBody AttackPatternUpdateInput input) {
    AttackPattern attackPattern =
        this.attackPatternRepository
            .findById(attackPatternId)
            .orElseThrow(ElementNotFoundException::new);
    attackPattern.setUpdateAttributes(input);
    attackPattern.setKillChainPhases(
        fromIterable(this.killChainPhaseRepository.findAllById(input.getKillChainPhasesIds())));
    attackPattern.setUpdatedAt(Instant.now());
    return attackPatternRepository.save(attackPattern);
  }

  private List<AttackPattern> internalUpsertAttackPatterns(
      List<AttackPatternCreateInput> attackPatterns, Boolean ignoreDependencies) {
    List<AttackPattern> upserted = new ArrayList<>();
    attackPatterns.forEach(
        attackPatternInput -> {
          String attackPatternExternalId = attackPatternInput.getExternalId();
          Optional<AttackPattern> optionalAttackPattern =
              attackPatternRepository.findByExternalId(attackPatternExternalId);
          List<KillChainPhase> killChainPhases =
              attackPatternInput.getKillChainPhasesIds() != null
                      && !attackPatternInput.getKillChainPhasesIds().isEmpty()
                  ? fromIterable(
                      killChainPhaseRepository.findAllById(
                          attackPatternInput.getKillChainPhasesIds()))
                  : List.of();
          AttackPattern attackPatternParent =
              attackPatternInput.getParentId() != null
                  ? attackPatternRepository
                      .findByStixId(attackPatternInput.getParentId())
                      .orElseThrow(ElementNotFoundException::new)
                  : null;
          if (optionalAttackPattern.isEmpty()) {
            AttackPattern newAttackPattern = new AttackPattern();
            newAttackPattern.setStixId(attackPatternInput.getStixId());
            newAttackPattern.setExternalId(attackPatternExternalId);
            newAttackPattern.setKillChainPhases(killChainPhases);
            newAttackPattern.setName(attackPatternInput.getName());
            newAttackPattern.setDescription(attackPatternInput.getDescription());
            newAttackPattern.setPlatforms(attackPatternInput.getPlatforms());
            newAttackPattern.setPermissionsRequired(attackPatternInput.getPermissionsRequired());
            newAttackPattern.setParent(attackPatternParent);
            upserted.add(newAttackPattern);
          } else {
            AttackPattern attackPattern = optionalAttackPattern.get();
            // In this case, the input may not contain kill chain phases or parent, we keep the
            // original
            if (ignoreDependencies) {
              if (killChainPhases.isEmpty() && !attackPattern.getKillChainPhases().isEmpty()) {
                killChainPhases = attackPattern.getKillChainPhases();
              }
              if (attackPatternParent == null && attackPattern.getParent() != null) {
                attackPatternParent = attackPattern.getParent();
              }
            }
            attackPattern.setStixId(attackPatternInput.getStixId());
            attackPattern.setKillChainPhases(killChainPhases);
            attackPattern.setName(attackPatternInput.getName());
            attackPattern.setDescription(attackPatternInput.getDescription());
            attackPattern.setPlatforms(attackPatternInput.getPlatforms());
            attackPattern.setPermissionsRequired(attackPatternInput.getPermissionsRequired());
            attackPattern.setParent(attackPatternParent);
            upserted.add(attackPattern);
          }
        });
    return fromIterable(this.attackPatternRepository.saveAll(upserted));
  }

  @PostMapping("/api/attack_patterns/upsert")
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.ATTACK_PATTERN)
  @Transactional(rollbackOn = Exception.class)
  public Iterable<AttackPattern> upsertAttackPatterns(
      @Valid @RequestBody AttackPatternUpsertInput input) {
    List<AttackPattern> upserted = new ArrayList<>();
    List<AttackPatternCreateInput> attackPatterns = input.getAttackPatterns();
    List<AttackPatternCreateInput> patternsWithoutParent =
        attackPatterns.stream().filter(a -> a.getParentId() == null).toList();
    List<AttackPatternCreateInput> patternsWithParent =
        attackPatterns.stream().filter(a -> a.getParentId() != null).toList();
    upserted.addAll(
        internalUpsertAttackPatterns(patternsWithoutParent, input.getIgnoreDependencies()));
    upserted.addAll(
        internalUpsertAttackPatterns(patternsWithParent, input.getIgnoreDependencies()));
    return upserted;
  }

  @DeleteMapping("/api/attack_patterns/{attackPatternId}")
  @RBAC(
      resourceId = "#attackPatternId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.ATTACK_PATTERN)
  @Transactional(rollbackOn = Exception.class)
  public void deleteAttackPattern(@PathVariable String attackPatternId) {
    attackPatternRepository.deleteById(attackPatternId);
  }

  // -- OPTION --

  @GetMapping(ATTACK_PATTERN_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATTACK_PATTERN)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return fromIterable(
            this.attackPatternRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping(ATTACK_PATTERN_URI + "/options")
  @RBAC(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATTACK_PATTERN)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.attackPatternRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
