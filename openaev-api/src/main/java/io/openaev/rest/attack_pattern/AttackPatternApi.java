package io.openaev.rest.attack_pattern;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.database.specification.AttackPatternSpecification.byName;
import static io.openaev.helper.DatabaseHelper.updateRelation;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.*;
import io.openaev.database.raw.RawAttackPatternIndexing;
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
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping({AttackPatternApi.ATTACK_PATTERN_URI, TENANT_PREFIX + "/attack_patterns"})
public class AttackPatternApi extends RestBehavior {

  public static final String ATTACK_PATTERN_URI = "/api/attack_patterns";

  private final AttackPatternService attackPatternService;

  private final AttackPatternRepository attackPatternRepository;
  private final InjectorContractRepository injectorContractRepository;
  private final KillChainPhaseRepository killChainPhaseRepository;

  @GetMapping
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ATTACK_PATTERN)
  public List<RawAttackPatternIndexing> attackPatterns() {
    return attackPatternRepository.rawAll();
  }

  @PostMapping("/search")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATTACK_PATTERN)
  public Page<AttackPattern> attackPatterns(
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<AttackPattern> specification, Pageable pageable) ->
            this.attackPatternRepository.findAll(specification, pageable),
        searchPaginationInput,
        AttackPattern.class);
  }

  @PostMapping("/search-with-ai")
  @AccessControl(skipRBAC = true, isEnterpriseEdition = true)
  @Operation(
      summary = "Extract Attack Patterns from text or files using AI",
      description = "Get attack patterns ids extracted from a text or files using AI")
  public List<String> searchAttackPatternWithTTPAIWebservice(
      @RequestPart(value = "files", required = false) @Nullable List<MultipartFile> files,
      @RequestPart(value = "text", required = false) @Nullable final String text,
      @RequestPart(value = "agent_slug", required = false) @Nullable final String agentSlug) {
    return attackPatternService.searchAttackPatternWithTTPAIWebservice(
        files == null ? new ArrayList<>() : files, text == null ? "" : text, agentSlug);
  }

  @GetMapping("/{attackPatternId}")
  @AccessControl(
      resourceId = "#attackPatternId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ATTACK_PATTERN)
  public AttackPattern attackPattern(@PathVariable String attackPatternId) {
    return attackPatternService.findById(attackPatternId);
  }

  @PostMapping
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.ATTACK_PATTERN)
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

  @GetMapping("/{attackPatternId}/injector_contracts")
  @AccessControl(
      resourceId = "#attackPatternId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.ATTACK_PATTERN)
  public Iterable<InjectorContract> injectorContracts(@PathVariable String attackPatternId) {
    attackPatternRepository.findById(attackPatternId).orElseThrow(ElementNotFoundException::new);
    return injectorContractRepository.findAll(
        InjectorContractSpecification.fromAttackPattern(attackPatternId));
  }

  @PutMapping("/{attackPatternId}")
  @AccessControl(
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

  @PostMapping("/upsert")
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.ATTACK_PATTERN)
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
        attackPatternService.internalUpsertAttackPatterns(
            patternsWithoutParent, input.getIgnoreDependencies()));
    upserted.addAll(
        attackPatternService.internalUpsertAttackPatterns(
            patternsWithParent, input.getIgnoreDependencies()));
    return upserted;
  }

  @DeleteMapping("/{attackPatternId}")
  @AccessControl(
      resourceId = "#attackPatternId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.ATTACK_PATTERN)
  @Transactional(rollbackOn = Exception.class)
  public void deleteAttackPattern(@PathVariable String attackPatternId) {
    attackPatternRepository.deleteById(attackPatternId);
  }

  // -- OPTION --

  @GetMapping("/options")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATTACK_PATTERN)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return fromIterable(
            this.attackPatternRepository.findAll(
                byName(searchText), Sort.by(Sort.Direction.ASC, "name")))
        .stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping("/options")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ATTACK_PATTERN)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.attackPatternRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
