package io.openaev.utils.fixtures;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Filters;
import io.openaev.rest.asset_group.form.AssetGroupInput;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class AssetGroupFixture {

  public static AssetGroup createDefaultAssetGroup(@NotNull final String name) {
    AssetGroup assetGroup = new AssetGroup();
    assetGroup.setName(name);
    assetGroup.setDescription("An asset group");
    return assetGroup;
  }

  public static AssetGroupInput createDefaultAssetGroupInput(@NotNull final String name) {
    AssetGroupInput assetGroupInput = new AssetGroupInput();
    assetGroupInput.setName(name);
    assetGroupInput.setDescription("An asset group");
    return assetGroupInput;
  }

  public static AssetGroupInput createAssetGroupWithTags(
      @NotNull final String name, @NotNull final List<String> tagIds) {
    AssetGroupInput assetGroupInput = createDefaultAssetGroupInput(name);
    assetGroupInput.setTagIds(tagIds);
    return assetGroupInput;
  }

  public static AssetGroupInput createAssetGroupWithDynamicFilters(
      @NotNull final String name, @NotNull final Filters.FilterGroup dynamicFilter) {
    AssetGroupInput assetGroupInput = createDefaultAssetGroupInput(name);
    assetGroupInput.setDynamicFilter(dynamicFilter);
    return assetGroupInput;
  }

  public static AssetGroup createAssetGroupWithAssets(
      @NotNull final String name, List<Asset> assets) {
    AssetGroup assetGroup = createDefaultAssetGroup(name);
    assetGroup.setAssets(assets);
    return assetGroup;
  }

  public static AssetGroup createAssetGroupWithDynamicFilter(
      @NotNull final String name, @NotNull final Filters.FilterGroup dynamicFilter) {
    AssetGroup assetGroup = createDefaultAssetGroup(name);
    assetGroup.setDynamicFilter(dynamicFilter);
    return assetGroup;
  }
}
