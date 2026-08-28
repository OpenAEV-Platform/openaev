package io.openaev.service.targets.search;

import io.openaev.database.model.AiTargetTarget;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetCategory;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectTarget;
import io.openaev.database.model.Tag;
import io.openaev.database.repository.AiTargetRepository;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.InjectContentUtils;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Resolves the AI target ({@link Asset} with {@code category = AI_TARGET}) referenced from an
 * inject content ({@code ai_target} field) as an {@link InjectTarget}, so it appears in the atomic
 * testing "Targets" panel alongside endpoint / agent targets. The AI target is not an asset
 * relation on the inject; it is a content reference, hence a dedicated adaptor rather than a
 * JPA-relation search.
 */
@Component
@RequiredArgsConstructor
public class AiTargetSearchAdaptor extends SearchAdaptorBase {

  private final AiTargetRepository aiTargetRepository;
  private final AssetGroupService assetGroupService;
  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  private Optional<Asset> contentAiTarget(Inject scopedInject) {
    // Key parsing shared with InjectService.resolveContentAiTarget via InjectContentUtils.
    return InjectContentUtils.contentAiTargetId(scopedInject.getContent())
        .flatMap(aiTargetRepository::findAiTargetById);
  }

  /**
   * Resolve every AI target attached to the inject: the one referenced from the content (AI target
   * / Manual mode) and every AI target asset that belongs to the inject's asset groups (Asset group
   * mode). Deduplicated by id, insertion order preserved.
   */
  private List<Asset> resolveAiTargets(Inject scopedInject) {
    Map<String, Asset> byId = new LinkedHashMap<>();
    contentAiTarget(scopedInject).ifPresent(aiTarget -> byId.put(aiTarget.getId(), aiTarget));

    // Resolve every asset group member (static AND dynamic) and keep the AI targets. Going through
    // AssetGroupService (rather than the static-only findAllByAssetGroupIds join) is what makes
    // filter-based groups such as "Category = AI_TARGET" expand to their members here, mirroring
    // how the endpoint target tab resolves dynamic groups.
    for (AssetGroup assetGroup : scopedInject.getAssetGroups()) {
      for (Asset asset : assetGroupService.assetsFromAssetGroup(assetGroup.getId())) {
        if (AssetCategory.AI_TARGET.equals(asset.getCategory())) {
          byId.putIfAbsent(asset.getId(), asset);
        }
      }
    }
    return List.copyOf(byId.values());
  }

  @Override
  public Page<InjectTarget> search(SearchPaginationInput input, Inject scopedInject) {
    String textSearch = StringUtils.trimToEmpty(input.getTextSearch());
    List<InjectTarget> targets =
        resolveAiTargets(scopedInject).stream()
            .filter(
                aiTarget ->
                    textSearch.isEmpty()
                        || StringUtils.containsIgnoreCase(aiTarget.getName(), textSearch))
            .map(aiTarget -> convertFromAiTarget(aiTarget, scopedInject))
            .toList();

    // A real (paged) Pageable is required: a PageImpl backed by Pageable.unpaged() throws
    // UnsupportedOperationException when Jackson serialises the pageable in the response.
    int size = Math.max(1, input.getSize());
    Pageable pageable = PageRequest.of(input.getPage(), size);
    int from = Math.min((int) pageable.getOffset(), targets.size());
    int to = Math.min(from + size, targets.size());
    return new PageImpl<>(targets.subList(from, to), pageable, targets.size());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForInject(Inject scopedInject, String textSearch) {
    String search = StringUtils.trimToEmpty(textSearch);
    return resolveAiTargets(scopedInject).stream()
        .filter(
            aiTarget ->
                search.isEmpty() || StringUtils.containsIgnoreCase(aiTarget.getName(), search))
        .map(aiTarget -> new FilterUtilsJpa.Option(aiTarget.getId(), aiTarget.getName()))
        .toList();
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    return aiTargetRepository.findAiTargetsByIds(ids).stream()
        .map(aiTarget -> new FilterUtilsJpa.Option(aiTarget.getId(), aiTarget.getName()))
        .toList();
  }

  private InjectTarget convertFromAiTarget(Asset aiTarget, Inject inject) {
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        inject,
        () ->
            new AiTargetTarget(
                aiTarget.getId(),
                aiTarget.getName(),
                aiTarget.getTags().stream().map(Tag::getId).collect(Collectors.toSet()),
                aiTarget.getAiTargetProvider() != null
                    ? aiTarget.getAiTargetProvider().name()
                    : null),
        true);
  }
}
