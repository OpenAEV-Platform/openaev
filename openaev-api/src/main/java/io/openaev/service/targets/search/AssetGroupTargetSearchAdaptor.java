package io.openaev.service.targets.search;

import io.openaev.database.model.AssetGroupTarget;
import io.openaev.database.model.Inject;
import io.openaev.database.model.InjectTarget;
import io.openaev.rest.asset_group.AssetGroupCriteriaBuilderService;
import io.openaev.rest.asset_group.form.AssetGroupOutput;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.FilterUtilsJpa;
import io.openaev.utils.pagination.SearchPaginationInput;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

@Component
public class AssetGroupTargetSearchAdaptor extends SearchAdaptorBase {

  private final AssetGroupCriteriaBuilderService assetGroupCriteriaBuilderService;
  private final AssetGroupService assetGroupService;
  private final HelperTargetSearchAdaptor helperTargetSearchAdaptor;

  public AssetGroupTargetSearchAdaptor(
      AssetGroupCriteriaBuilderService assetGroupCriteriaBuilderService,
      AssetGroupService assetGroupService,
      HelperTargetSearchAdaptor helperTargetSearchAdaptor) {
    this.assetGroupCriteriaBuilderService = assetGroupCriteriaBuilderService;
    this.helperTargetSearchAdaptor = helperTargetSearchAdaptor;
    this.assetGroupService = assetGroupService;

    // field name translations
    this.fieldTranslations.put("target_name", "asset_group_name");
    this.fieldTranslations.put("target_tags", "asset_group_tags");
    this.fieldTranslations.put("target_injects", "asset_group_injects");
  }

  @Override
  public Page<InjectTarget> search(SearchPaginationInput input, Inject scopedInject) {
    Page<AssetGroupOutput> filteredAssetGroups =
        assetGroupCriteriaBuilderService.assetGroupPagination(this.translate(input, scopedInject));
    return new PageImpl<>(
        filteredAssetGroups.getContent().stream()
            .map(assetGroupOutput -> convertFromAssetGroupOutput(assetGroupOutput, scopedInject))
            .toList(),
        filteredAssetGroups.getPageable(),
        filteredAssetGroups.getTotalElements());
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsForInject(Inject scopedInject, String textSearch) {
    return scopedInject.getAssetGroups().stream()
        .filter(ag -> ag.getName().toLowerCase().contains(textSearch.toLowerCase()))
        .map(ag -> new FilterUtilsJpa.Option(ag.getId(), ag.getName()))
        .toList();
  }

  @Override
  public List<FilterUtilsJpa.Option> getOptionsByIds(List<String> ids) {
    return assetGroupService.assetGroups(ids).stream()
        .map(ag -> new FilterUtilsJpa.Option(ag.getId(), ag.getName()))
        .toList();
  }

  private InjectTarget convertFromAssetGroupOutput(
      AssetGroupOutput assetGroupOutput, Inject inject) {
    return helperTargetSearchAdaptor.buildTargetWithExpectations(
        inject,
        () ->
            new AssetGroupTarget(
                assetGroupOutput.getId(), assetGroupOutput.getName(), assetGroupOutput.getTags()),
        true);
  }
}
