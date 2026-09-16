package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.finding.form.AggregatedFindingOutput;
import io.openaev.rest.finding.form.FindingOutput;
import io.openaev.rest.finding.form.FindingSummaryOutput;
import io.openaev.rest.finding.form.RelatedFindingOutput;
import io.openaev.utils.SensitiveValueMaskingUtils;
import io.openaev.utils.TargetType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        .value(SensitiveValueMaskingUtils.maskIfNeeded(finding.getType(), finding.getValue()))
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
   * API returns, so the masking applied here is what guarantees a secret value never leaves the
   * platform in cleartext.
   */
  public FindingOutput toFindingOutput(Finding finding) {
    return FindingOutput.builder()
        .id(finding.getId())
        .field(finding.getField())
        .type(finding.getType())
        .value(SensitiveValueMaskingUtils.maskIfNeeded(finding.getType(), finding.getValue()))
        .labels(finding.getLabels())
        .name(finding.getName())
        .tags(finding.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .injectId(Optional.ofNullable(finding.getInject()).map(Inject::getId).orElse(null))
        .creationDate(finding.getCreationDate())
        .updateDate(finding.getUpdateDate())
        .assets(finding.getAssets().stream().map(Asset::getId).toList())
        .teams(finding.getTeams().stream().map(Team::getId).toList())
        .users(finding.getUsers().stream().map(User::getId).toList())
        .simulation(
            Optional.ofNullable(finding.getSimulation())
                .map(exerciseMapper::toExerciseSimple)
                .orElse(null))
        .scenario(
            Optional.ofNullable(finding.getScenario())
                .map(scenarioMapper::toScenarioSimple)
                .orElse(null))
        .assetGroups(
            finding.getAssetGroups().stream()
                .map(assetGroupMapper::toAssetGroupSimple)
                .collect(Collectors.toSet()))
        .build();
  }

  public AggregatedFindingOutput toAggregatedFindingOutput(
      Finding finding, List<Asset> relatedAssets) {
    return toAggregatedFindingOutput(
        finding, relatedAssets, finding.getCreationDate(), finding.getUpdateDate());
  }

  /**
   * Aggregated (deduplicated by type + value) output. The representative {@code finding} row is the
   * most recent occurrence in the group (greatest {@code updateDate}, tie-broken by smallest id -
   * see {@code FindingSpecification.distinctTypeValueWithFilter}), so its own {@code updateDate}
   * already matches the group last seen; its {@code creationDate}, however, is that single
   * occurrence's, so callers must still pass the group-wide first/last seen explicitly.
   */
  public AggregatedFindingOutput toAggregatedFindingOutput(
      Finding finding, List<Asset> relatedAssets, Instant firstSeen, Instant lastSeen) {
    return AggregatedFindingOutput.builder()
        .id(finding.getId())
        .value(SensitiveValueMaskingUtils.maskIfNeeded(finding.getType(), finding.getValue()))
        .type(finding.getType())
        .creationDate(firstSeen)
        .updateDate(lastSeen)
        // Findings can attach to ANY asset type (agentless websites, AI targets, cloud/network
        // assets), so no instanceof Endpoint filtering here.
        .assets(
            relatedAssets.stream()
                .map(endpointMapper::toEndpointSimple)
                .collect(Collectors.toSet()))
        .build();
  }

  public RelatedFindingOutput toRelatedFindingOutput(Finding finding) {
    return RelatedFindingOutput.builder()
        .id(finding.getId())
        .value(SensitiveValueMaskingUtils.maskIfNeeded(finding.getType(), finding.getValue()))
        .type(finding.getType())
        .updateDate(finding.getUpdateDate())
        .assets(
            finding.getAssets().stream()
                .map(asset -> endpointMapper.toEndpointSimple(asset))
                .collect(Collectors.toSet()))
        .assetGroups(
            finding.getAssetGroups().stream()
                .map(assetGroup -> assetGroupMapper.toAssetGroupSimple(assetGroup))
                .collect(Collectors.toSet()))
        .inject(injectMapper.toInjectSimple(finding.getInject()))
        .simulation(
            Optional.ofNullable(finding.getInject().getExercise())
                .map(exercise -> exerciseMapper.toExerciseSimple(exercise))
                .orElse(null))
        .scenario(
            Optional.ofNullable(finding.getInject().getExercise())
                .map(Exercise::getScenario)
                .map(scenario -> scenarioMapper.toScenarioSimple(scenario))
                .orElse(null))
        // Teams and persons attached to this occurrence (e.g. phishing credential findings): the
        // occurrence list needs them to show WHO was impacted, not only which machine.
        .teams(
            finding.getTeams().stream()
                .map(
                    team ->
                        TargetSimple.builder()
                            .id(team.getId())
                            .name(team.getName())
                            .type(TargetType.TEAMS)
                            .build())
                .collect(Collectors.toSet()))
        .users(
            finding.getUsers().stream()
                .map(
                    user ->
                        TargetSimple.builder()
                            .id(user.getId())
                            .name(user.getNameOrEmail())
                            .type(TargetType.PLAYERS)
                            .build())
                .collect(Collectors.toSet()))
        .creationDate(finding.getCreationDate())
        .build();
  }
}
