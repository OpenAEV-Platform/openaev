package io.openaev.processor.datapack;

import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetGroupService;
import io.openaev.service.DataPackService;
import io.openaev.service.TagRuleService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class V20260107_Tags_and_tagrules_and_assetgroups extends DataPack {

  private final TagService tagService;
  private final TagRuleService tagRuleService;
  private final AssetGroupService assetGroupService;
  private final TenantScopedTransaction tenantScopedTransaction;

  public V20260107_Tags_and_tagrules_and_assetgroups(
      DataPackService dataPackService,
      TagService tagService,
      TagRuleService tagRuleService,
      AssetGroupService assetGroupService,
      TenantScopedTransaction tenantScopedTransaction) {
    super(dataPackService);
    this.tagService = tagService;
    this.tagRuleService = tagRuleService;
    this.assetGroupService = assetGroupService;
    this.tenantScopedTransaction = tenantScopedTransaction;
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
  public boolean doProcess(Tenant tenant) {
    try {
      String tenantId = tenant.getId();
      // ensureWellKnownTags must commit in its own transaction before ensurePresetRules runs in
      // its own executeNew: both would otherwise touch the same (name, tenant) tag rows, one on
      // the outer suspended transaction, one on the nested one, and block on each other's
      // uncommitted locks forever (the nested transaction can never see the outer's uncommitted
      // insert, and the outer stays suspended until the nested one returns).
      tenantScopedTransaction.executeNew(
          TxCtx.forTenant(tenantId), tagService::ensureWellKnownTags);
      Set<TagRule> presetRules =
          tenantScopedTransaction.executeNew(
              TxCtx.forTenant(tenantId), () -> tagRuleService.ensurePresetRules(tenantId));

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

          // Must commit in its own transaction before addAssetGroup's executeNew below attaches
          // it to the tag rule on a different connection — otherwise the same lock-wait trap as
          // tags/tag_rules above.
          AssetGroup saved =
              tenantScopedTransaction.executeNew(
                  TxCtx.forTenant(tenantId),
                  () -> this.assetGroupService.createAssetGroup(assetGroup));

          findTagRuleForPlatform(presetRules, platform)
              .map(TagRule::getId)
              .ifPresent(
                  tagRuleId ->
                      tenantScopedTransaction.executeNew(
                          TxCtx.forTenant(tenantId),
                          () -> {
                            // Re-fetch inside this transaction: tagRule came from the previous,
                            // now-closed executeNew and is a detached entity — its LAZY
                            // assetGroups collection would throw LazyInitializationException if
                            // touched without a managed instance from the current session.
                            TagRule managedTagRule =
                                tagRuleService
                                    .findById(tagRuleId)
                                    .orElseThrow(
                                        () ->
                                            new ElementNotFoundException(
                                                "TagRule not found with id: " + tagRuleId));
                            tagRuleService.addAssetGroup(managedTagRule, saved);
                          }));
        }
      }
      return true;
    } catch (Exception e) {
      log.error("Unexpected error during DataPack 20260107 initialization.", e);
      return false;
    }
  }
}
