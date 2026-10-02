package io.openaev.utils.fixtures;

import io.openaev.context.TenantContext;
import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Filters;
import io.openaev.database.model.Tenant;
import io.openaev.rest.asset_group.form.AssetGroupInput;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class AssetGroupFixture {

  public static AssetGroup createDefaultAssetGroup(@NotNull final String name) {
    AssetGroup assetGroup = new AssetGroup();
    assetGroup.setName(name);
    assetGroup.setDescription("An asset group");
    // asset_groups is tenant-active and its TenantBaseListener was removed at go-live: stamp the
    // tenant explicitly here, matching what the listener used to do, instead of leaving every call
    // site to remember it. TenantContext.getCurrentTenant() never throws (it defaults to
    // Tenant.DEFAULT_TENANT_UUID), so this is safe even outside a request context. Same shape as
    // SecurityCoverageFixture, the other activated entity that carries no listener.
    assetGroup.setTenant(new Tenant(TenantContext.getCurrentTenant()));
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
