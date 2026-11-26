package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.finding.form.AggregatedFindingOutput;
import io.openaev.rest.finding.form.FindingOutput;
import io.openaev.rest.finding.form.FindingSummaryOutput;
import io.openaev.rest.finding.form.RelatedFindingOutput;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.TargetType;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
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
        .firstSeen(
            seen != null
                ? seen.getFirstSeen()
                : Optional.ofNullable(finding.getFirstSeen()).orElse(finding.getCreationDate()))
        .lastSeen(
            seen != null
                ? seen.getLastSeen()
                : Optional.ofNullable(finding.getLastSeen()).orElse(finding.getUpdateDate()))
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
  public FindingOutput toFindingOutput(Finding finding) {
    Optional<FindingOccurrence> occurrence = latestOccurrence(finding);
    Optional<Inject> inject = occurrence.map(FindingOccurrence::getInject);
    Optional<Exercise> simulation = inject.map(Inject::getExercise);
    return FindingOutput.builder()
        .id(finding.getId())
        .field(occurrence.map(FindingOccurrence::getField).orElse(null))
        .type(finding.getType())
        .value(finding.getValueDisplay())
        .name(finding.getName())
        .tags(finding.getTags().stream().map(Tag::getId).collect(Collectors.toSet()))
        .injectId(inject.map(Inject::getId).orElse(null))
        .creationDate(finding.getCreationDate())
        .updateDate(finding.getUpdateDate())
        .firstSeen(firstSeenOf(finding))
        .lastSeen(lastSeenOf(finding))
        .status(finding.getStatus())
        .lastTested(finding.getLastTested())
        .statusInjectId(finding.getStatusInjectId())
        .retestInjectId(finding.getRetestInjectId())
        .assets(finding.getAssets().stream().map(Asset::getId).toList())
        .teams(finding.getTeams().stream().map(Team::getId).toList())
        .users(finding.getUsers().stream().map(User::getId).toList())
        .simulation(simulation.map(exerciseMapper::toExerciseSimple).orElse(null))
        .scenario(
            simulation
                .map(Exercise::getScenario)
                .map(scenarioMapper::toScenarioSimple)
                .orElse(null))
        .assetGroups(assetGroupsOf(finding))
        .build();
  }

  public AggregatedFindingOutput toAggregatedFindingOutput(
      Finding finding, List<Asset> relatedAssets) {
    return toAggregatedFindingOutput(
        finding,
        relatedAssets,
        finding.getCreationDate(),
        finding.getUpdateDate(),
        firstSeenOf(finding),
        lastSeenOf(finding));
  }

  /**
   * Aggregated (deduplicated by type + value) output. The representative {@code finding} row is the
   * most recent occurrence in the group (greatest {@code updateDate}, tie-broken by smallest id -
   * see {@code FindingSpecification.distinctTypeValueWithFilter}); its own dates are that single
   * occurrence's, so callers pass the group-wide row times (earliest creation, latest write) and
   * the group-wide detection times (first and last seen) explicitly.
   */
  public AggregatedFindingOutput toAggregatedFindingOutput(
      Finding finding,
      List<Asset> relatedAssets,
      Instant createdAt,
      Instant updatedAt,
      Instant firstSeen,
      Instant lastSeen) {
    return AggregatedFindingOutput.builder()
        .id(finding.getId())
        .value(finding.getValueDisplay())
        .type(finding.getType())
        .creationDate(createdAt)
        .updateDate(updatedAt)
        .firstSeen(firstSeen)
        .lastSeen(lastSeen)
        // Findings can attach to ANY asset type (agentless websites, AI targets, cloud/network
        // assets), so no instanceof Endpoint filtering here.
        .assets(
            relatedAssets.stream()
                .map(endpointMapper::toEndpointSimple)
                .collect(Collectors.toSet()))
        .build();
  }

  public RelatedFindingOutput toRelatedFindingOutput(Finding finding) {
    Optional<Inject> inject = latestOccurrence(finding).map(FindingOccurrence::getInject);
    Optional<Exercise> simulation = inject.map(Inject::getExercise);
    return RelatedFindingOutput.builder()
        .id(finding.getId())
        .value(finding.getValueDisplay())
        .type(finding.getType())
        .updateDate(finding.getUpdateDate())
        .firstSeen(firstSeenOf(finding))
        .lastSeen(lastSeenOf(finding))
        .assets(
            finding.getAssets().stream()
                .map(asset -> endpointMapper.toEndpointSimple(asset))
                .collect(Collectors.toSet()))
        .assetGroups(assetGroupsOf(finding))
        .inject(inject.map(injectMapper::toInjectSimple).orElse(null))
        .simulation(simulation.map(exerciseMapper::toExerciseSimple).orElse(null))
        .scenario(
            simulation
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

  /**
   * First detection of a finding row: its first seen, or its creation time when the detection time
   * is unknown (rows written before first/last seen existed). Same rule as the summary aggregate.
   */
  public static Instant firstSeenOf(Finding finding) {
    return Optional.ofNullable(finding.getFirstSeen()).orElse(finding.getCreationDate());
  }

  /**
   * Last detection of a finding row: its last seen, or its last write time when the detection time
   * is unknown. Never the write time when a detection time exists: any write moves it.
   */
  public static Instant lastSeenOf(Finding finding) {
    return Optional.ofNullable(finding.getLastSeen()).orElse(finding.getUpdateDate());
  }

  private static Optional<FindingOccurrence> latestOccurrence(Finding finding) {
    return finding.getOccurrences().stream()
        .filter(occurrence -> occurrence.getInject() != null)
        .max(Comparator.comparing(FindingOccurrence::getDate));
  }
}
