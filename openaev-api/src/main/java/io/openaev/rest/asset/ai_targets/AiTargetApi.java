package io.openaev.rest.asset.ai_targets;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.AiTargetRepository;
import io.openaev.database.repository.TagRepository;
import io.openaev.rest.asset.ai_targets.form.AiTargetInput;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * CRUD facade for AI targets. AI targets are {@link Asset} rows with {@code category = AI_TARGET} -
 * this controller keeps the dedicated {@code /api/ai_targets} surface (menu / marketing) while
 * persisting through the unified asset model.
 */
@RequiredArgsConstructor
@RestController
public class AiTargetApi {

  public static final String AI_TARGET_URI = "/api/ai_targets";
  private static final String TENANT_AI_TARGET_URI = TENANT_PREFIX + "/ai_targets";

  private final AiTargetRepository aiTargetRepository;
  private final TagRepository tagRepository;

  /** Restricts any search to AI target assets, on top of the caller-provided specification. */
  private Specification<Asset> aiTargetCategory() {
    return (root, query, cb) -> cb.equal(root.get("category"), AssetCategory.AI_TARGET);
  }

  private Asset prepareAiTarget(Asset aiTarget, AiTargetInput input) {
    aiTarget.setUpdateAttributes(input);
    aiTarget.setCategory(AssetCategory.AI_TARGET);
    aiTarget.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return aiTarget;
  }

  @GetMapping({AI_TARGET_URI, TENANT_AI_TARGET_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  public Iterable<Asset> aiTargets(TxCtx ctx) {
    return aiTargetRepository.findAllAiTargets();
  }

  @PostMapping({AI_TARGET_URI, TENANT_AI_TARGET_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public Asset createAiTarget(TxCtx ctx, @Valid @RequestBody final AiTargetInput input) {
    return this.aiTargetRepository.save(prepareAiTarget(new Asset(), input));
  }

  @GetMapping({AI_TARGET_URI + "/{aiTargetId}", TENANT_AI_TARGET_URI + "/{aiTargetId}"})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  public Asset aiTarget(TxCtx ctx, @PathVariable @NotBlank final String aiTargetId) {
    return this.aiTargetRepository
        .findAiTargetById(aiTargetId)
        .orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping({AI_TARGET_URI + "/search", TENANT_AI_TARGET_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public Page<Asset> aiTargets(
      TxCtx ctx, @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        (Specification<Asset> spec, org.springframework.data.domain.Pageable pageable) ->
            this.aiTargetRepository.findAll(aiTargetCategory().and(spec), pageable),
        searchPaginationInput,
        Asset.class);
  }

  @PutMapping({AI_TARGET_URI + "/{aiTargetId}", TENANT_AI_TARGET_URI + "/{aiTargetId}"})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public Asset updateAiTarget(
      TxCtx ctx,
      @PathVariable @NotBlank final String aiTargetId,
      @Valid @RequestBody final AiTargetInput input) {
    Asset aiTarget =
        this.aiTargetRepository
            .findAiTargetById(aiTargetId)
            .orElseThrow(ElementNotFoundException::new);
    return this.aiTargetRepository.save(prepareAiTarget(aiTarget, input));
  }

  @DeleteMapping({AI_TARGET_URI + "/{aiTargetId}", TENANT_AI_TARGET_URI + "/{aiTargetId}"})
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public void deleteAiTarget(TxCtx ctx, @PathVariable @NotBlank final String aiTargetId) {
    // Resolve through the tenant-filtered, category-scoped lookup first: a raw deleteById would
    // bypass the Hibernate tenant filter (em.find) and could delete any asset type by id.
    Asset aiTarget =
        this.aiTargetRepository
            .findAiTargetById(aiTargetId)
            .orElseThrow(ElementNotFoundException::new);
    this.aiTargetRepository.delete(aiTarget);
  }

  @GetMapping({AI_TARGET_URI + "/options", TENANT_AI_TARGET_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<FilterUtilsJpa.Option> optionsByName(
      TxCtx ctx, @RequestParam(required = false) final String searchText) {
    return aiTargetRepository.findAllByName(StringUtils.trimToNull(searchText)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping({AI_TARGET_URI + "/options", TENANT_AI_TARGET_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<FilterUtilsJpa.Option> optionsById(TxCtx ctx, @RequestBody final List<String> ids) {
    return this.aiTargetRepository.findAiTargetsByIds(ids).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
