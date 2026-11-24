package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import io.openaev.rest.asset.endpoint.form.EndpointSimple;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.finding.form.FindingOutput;
import io.openaev.rest.finding.form.FindingSummaryOutput;
import io.openaev.rest.finding.form.RelatedFindingOutput;
import io.openaev.service.AssetGroupService;
import io.openaev.rest.finding.form.FindingSimple;
import io.openaev.utils.TargetType;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Slf4j
public class FindingMapper {

  private final EndpointMapper endpointMapper;
  private final AssetGroupMapper assetGroupMapper;
  private final ExerciseMapper exerciseMapper;
  private final ScenarioMapper scenarioMapper;
  private final InjectMapper injectMapper;
  private final AssetGroupService assetGroupService;

  /**
   * The asset groups of a finding: the groups that contain at least one of its assets, statically
   * or through their dynamic filter. Never the groups its inject targeted. The search index reads
   * the same resolver, so the detail page and the "by asset group" widgets agree.
   */
  private Set<AssetGroupSimple> assetGroupsOf(Finding finding) {
    List<String> assetIds = finding.getAssets().stream().map(Asset::getId).toList();
    return assetGroupService.assetGroupsOfAssets(assetIds).stream()
        .map(assetGroupMapper::toAssetGroupSimple)
        .collect(Collectors.toSet());
  }

  /**
   * Group-wide impact counts of a finding, resolved by the service and handed over as one value so
   * four positional {@code long} arguments cannot be silently swapped.
   */
  public record FindingImpactCounts(long assets, long teams, long users, long assetGroups) {}

  /**
   * Group-wide summary of a finding, deduplicated by (type, value) across every occurrence in the
   * tenant. The finding overview hero relies on this instead of the picked representative row, so
   * the first/last seen and impact counts reflect the whole group rather than one arbitrary
   * occurrence.
   *
   * @param finding the representative finding row of the group
   * @param seen the group-wide first/last seen and occurrence count, null when the aggregate could
   *     not be resolved - the representative row's own dates are then used
   * @param counts the group-wide impact counts
   */
  public FindingSummaryOutput toFindingSummaryOutput(
      Finding finding, FindingRepository.FindingSeenAggregate seen, FindingImpactCounts counts) {
    return FindingSummaryOutput.builder()
        .id(finding.getId())
        .type(finding.getType())
        .value(finding.getValueDisplay())
        .firstSeen(seen != null ? seen.getFirstSeen() : finding.getCreationDate())
        .lastSeen(seen != null ? seen.getLastSeen() : finding.getUpdateDate())
        .occurrences(seen != null ? seen.getOccurrences() : 1)
        .assetsCount(counts.assets())
        .teamsCount(counts.teams())
        .usersCount(counts.users())
        .assetGroupsCount(counts.assetGroups())
        .build();
  }

  /**
   * Single finding output for the CRUD endpoints. This is the only representation of a finding the
   * API returns. Its value is the displayed value ({@link Finding#getValueDisplay()}), never the
   * cleartext, so a secret value never leaves the platform.
   */
  public FindingOutput toFindingOutput(Finding finding, long occurrencesCount) {
    return FindingOutput.builder()
        .id(finding.getId())
        .type(finding.getType())
        .value(finding.getValueDisplay())
        .tags(finding.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .firstSeen(finding.getFirstSeen())
        .lastSeen(finding.getLastSeen())
        .occurrencesCount(occurrencesCount)
        .location(toLocation(finding.getLocation()))
        .build();
  }

  public FindingSimple toFindingSimple(Finding finding) {
    return FindingSimple.builder()
        .id(finding.getId())
        .type(finding.getType())
        .value(finding.getValueDisplay())
        .asset(
            Optional.ofNullable(finding.getLocation())
                .map(FindingLocation::getAsset)
                .map(endpointMapper::toEndpointSimple)
                .orElse(null))
        .firstSeen(finding.getFirstSeen())
        .lastSeen(finding.getLastSeen())
        .build();
  }

  private TargetSimple toLocation(FindingLocation location) {
    if (location == null) {
      return null;
    }
    if (location.getAsset() != null) {
      EndpointSimple asset = endpointMapper.toEndpointSimple(location.getAsset());
      return TargetSimple.builder()
          .id(asset.getId())
          .name(asset.getName())
          .type(TargetType.ASSETS)
          .category(asset.getCategory())
          .subtype(asset.getPlatform())
          .build();
    }
    if (location.getUser() != null) {
      User user = location.getUser();
      return TargetSimple.builder()
          .id(user.getId())
          .name(user.getNameOrEmail())
          .type(TargetType.PLAYERS)
          .build();
    }
    return null;
  }
}
