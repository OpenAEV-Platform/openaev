package io.openaev.datapack.packs;

import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Filters;
import io.openaev.datapack.DataPack;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.TagRuleService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class V20260107_Tags_and_tagrules_and_assetgroups extends DataPack {

  private final TagService tagService;
  private final TagRuleService tagRuleService;
  private final AssetGroupService assetGroupService;

  @Override
  public void doProcess() {
    tagService.ensureWellKnownTags();
    tagRuleService.ensurePresetRules();

    Set<Endpoint.PLATFORM_TYPE> platformsToConsider =
        Set.of(
            Endpoint.PLATFORM_TYPE.Linux,
            Endpoint.PLATFORM_TYPE.Windows,
            Endpoint.PLATFORM_TYPE.MacOS);
    Set<Endpoint.PLATFORM_ARCH> architecturesToConsider =
        Set.of(Endpoint.PLATFORM_ARCH.x86_64, Endpoint.PLATFORM_ARCH.arm64);

    for (Endpoint.PLATFORM_ARCH arch : architecturesToConsider) {
      for (Endpoint.PLATFORM_TYPE platform : platformsToConsider) {
        Filters.Filter filterPlatform = new Filters.Filter();
        filterPlatform.setKey("endpoint_platform");
        filterPlatform.setOperator(Filters.FilterOperator.eq);
        filterPlatform.setMode(Filters.FilterMode.or);
        filterPlatform.setValues(new ArrayList<>(List.of(platform.toString())));

        Filters.Filter filterArch = new Filters.Filter();
        filterArch.setKey("endpoint_arch");
        filterArch.setOperator(Filters.FilterOperator.eq);
        filterArch.setMode(Filters.FilterMode.or);
        filterArch.setValues(new ArrayList<>(List.of(arch.toString())));

        Filters.FilterGroup filterGroup = new Filters.FilterGroup();
        filterGroup.setMode(Filters.FilterMode.and);
        filterGroup.setFilters(List.of(filterArch, filterPlatform));

        AssetGroup assetGroup = new AssetGroup();
        assetGroup.setName("All %s %s".formatted(platform.toString(), arch.toString()));
        assetGroup.setDynamicFilter(filterGroup);

        this.assetGroupService.createAssetGroup(assetGroup);
      }
    }
  }
}
