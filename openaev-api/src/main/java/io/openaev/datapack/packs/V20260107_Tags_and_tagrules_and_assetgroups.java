package io.openaev.datapack.packs;

import io.openaev.database.model.*;
import io.openaev.datapack.DataPack;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.DataPackService;
import io.openaev.service.TagRuleService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class V20260107_Tags_and_tagrules_and_assetgroups extends DataPack {

  private final TagService tagService;
  private final TagRuleService tagRuleService;
  private final AssetGroupService assetGroupService;

  public V20260107_Tags_and_tagrules_and_assetgroups(
      DataPackService dataPackService,
      TagService tagService,
      TagRuleService tagRuleService,
      AssetGroupService assetGroupService) {
    super(dataPackService);
    this.tagService = tagService;
    this.tagRuleService = tagRuleService;
    this.assetGroupService = assetGroupService;
  }

  private Optional<TagRule> findTagRuleForPlatform(
      Set<TagRule> tagRules, Endpoint.PLATFORM_TYPE platform) {
    String relevantTagName =
        switch (platform) {
          case Windows -> Tag.SECURITY_COVERAGE_WINDOWS_TAG_NAME;
          case Linux -> Tag.SECURITY_COVERAGE_LINUX_TAG_NAME;
          case MacOS -> Tag.SECURITY_COVERAGE_MACOS_TAG_NAME;
          default ->
              throw new IllegalArgumentException(
                  "Unexpected platform type: %s".formatted(platform));
        };
    return tagRules.stream()
        .filter(tr -> relevantTagName.equals(tr.getTag().getName()))
        .findFirst();
  }

  @Override
  public void doProcess() {
    tagService.ensureWellKnownTags();
    Set<TagRule> presetRules = tagRuleService.ensurePresetRules();

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

        AssetGroup saved = this.assetGroupService.createAssetGroup(assetGroup);

        findTagRuleForPlatform(presetRules, platform)
            .ifPresent(
                tagRule -> {
                  tagRuleService.addAssetGroup(tagRule, saved);
                });
      }
    }
  }
}
