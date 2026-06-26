package io.openaev.rest.asset.ai_targets;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;
import static io.openaev.helper.StreamHelper.fromIterable;
import static io.openaev.helper.StreamHelper.iterableToSet;
import static io.openaev.utils.pagination.PaginationUtils.buildPaginationJPA;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.AiTarget;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** CRUD for {@link AiTarget} assets (LLM endpoints / AI agents under adversarial test). */
@RequiredArgsConstructor
@RestController
public class AiTargetApi {

  public static final String AI_TARGET_URI = "/api/ai_targets";
  private static final String TENANT_AI_TARGET_URI = TENANT_PREFIX + "/ai_targets";

  private final AiTargetRepository aiTargetRepository;
  private final TagRepository tagRepository;

  @GetMapping({AI_TARGET_URI, TENANT_AI_TARGET_URI})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  public Iterable<AiTarget> aiTargets() {
    return aiTargetRepository.findAll();
  }

  @PostMapping({AI_TARGET_URI, TENANT_AI_TARGET_URI})
  @AccessControl(actionPerformed = Action.CREATE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public AiTarget createAiTarget(@Valid @RequestBody final AiTargetInput input) {
    AiTarget aiTarget = new AiTarget();
    aiTarget.setUpdateAttributes(input);
    aiTarget.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return this.aiTargetRepository.save(aiTarget);
  }

  @GetMapping({AI_TARGET_URI + "/{aiTargetId}", TENANT_AI_TARGET_URI + "/{aiTargetId}"})
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.ASSET)
  public AiTarget aiTarget(@PathVariable @NotBlank final String aiTargetId) {
    return this.aiTargetRepository
        .findById(aiTargetId)
        .orElseThrow(ElementNotFoundException::new);
  }

  @PostMapping({AI_TARGET_URI + "/search", TENANT_AI_TARGET_URI + "/search"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public Page<AiTarget> aiTargets(@RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return buildPaginationJPA(
        this.aiTargetRepository::findAll, searchPaginationInput, AiTarget.class);
  }

  @PutMapping({AI_TARGET_URI + "/{aiTargetId}", TENANT_AI_TARGET_URI + "/{aiTargetId}"})
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public AiTarget updateAiTarget(
      @PathVariable @NotBlank final String aiTargetId,
      @Valid @RequestBody final AiTargetInput input) {
    AiTarget aiTarget =
        this.aiTargetRepository.findById(aiTargetId).orElseThrow(ElementNotFoundException::new);
    aiTarget.setUpdateAttributes(input);
    aiTarget.setTags(iterableToSet(this.tagRepository.findAllById(input.getTagIds())));
    return this.aiTargetRepository.save(aiTarget);
  }

  @DeleteMapping({AI_TARGET_URI + "/{aiTargetId}", TENANT_AI_TARGET_URI + "/{aiTargetId}"})
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.ASSET)
  @Transactional(rollbackFor = Exception.class)
  public void deleteAiTarget(@PathVariable @NotBlank final String aiTargetId) {
    this.aiTargetRepository.deleteById(aiTargetId);
  }

  @GetMapping({AI_TARGET_URI + "/options", TENANT_AI_TARGET_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<FilterUtilsJpa.Option> optionsByName(
      @RequestParam(required = false) final String searchText) {
    return aiTargetRepository.findAllByName(StringUtils.trimToNull(searchText)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }

  @PostMapping({AI_TARGET_URI + "/options", TENANT_AI_TARGET_URI + "/options"})
  @Transactional
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.ASSET)
  public List<FilterUtilsJpa.Option> optionsById(@RequestBody final List<String> ids) {
    return fromIterable(this.aiTargetRepository.findAllById(ids)).stream()
        .map(i -> new FilterUtilsJpa.Option(i.getId(), i.getName()))
        .toList();
  }
}
