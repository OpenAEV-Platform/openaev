package io.openaev.utils.mapper;

import io.openaev.database.model.*;
import io.openaev.database.repository.FindingRepository;
import io.openaev.rest.asset.endpoint.form.EndpointSimple;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.finding.form.FindingOutput;
import io.openaev.rest.finding.form.FindingSimple;
import io.openaev.rest.finding.form.FindingSummaryOutput;
import io.openaev.rest.scenario.form.ScenarioSimple;
import io.openaev.service.AssetGroupService;
import io.openaev.utils.TargetType;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
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
   * The assets of a finding: the ones linked to it, else the asset of its location. A finding has
   * one location, but the assets it is linked to are what the clients of the former output read.
   */
  private static List<Asset> assetsOf(Finding finding) {
    if (!finding.getAssets().isEmpty()) {
      return finding.getAssets();
    }
    return Optional.ofNullable(finding.getLocation())
        .map(FindingLocation::getAsset)
        .map(List::of)
        .orElse(List.of());
  }

  /**
   * The asset groups of each asset of the findings, resolved once for all of them: the groups that
   * contain the asset, statically or through their dynamic filter. Never the groups an inject
   * targeted.
   */
  private Map<String, List<AssetGroup>> assetGroupsByAssetId(Collection<Finding> findings) {
    List<String> assetIds =
        findings.stream()
            .flatMap(finding -> assetsOf(finding).stream())
            .map(Asset::getId)
            .distinct()
            .toList();
    return assetGroupService.assetGroupsByAssetId(assetIds);
  }

  private Set<AssetGroupSimple> assetGroupsOf(
      Finding finding, Map<String, List<AssetGroup>> assetGroupsByAssetId) {
    return assetsOf(finding).stream()
        .flatMap(asset -> assetGroupsByAssetId.getOrDefault(asset.getId(), List.of()).stream())
        .collect(
            Collectors.toMap(
                AssetGroup::getId,
                Function.identity(),
                (first, second) -> first,
                LinkedHashMap::new))
        .values()
        .stream()
        .map(assetGroupMapper::toAssetGroupSimple)
        .collect(Collectors.toSet());
  }

  /**
   * Group-wide impact counts of a finding, resolved by the service and handed over as one value so
   * three positional {@code long} arguments cannot be silently swapped.
   */
  public record FindingImpactCounts(long assets, long teams, long users) {}

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
        .firstSeen(seen != null ? seen.getFirstSeen() : firstSeenOf(finding))
        .lastSeen(seen != null ? seen.getLastSeen() : lastSeenOf(finding))
        .occurrences(seen != null ? seen.getOccurrences() : 1)
        .assetsCount(counts.assets())
        .teamsCount(counts.teams())
        .usersCount(counts.users())
        .build();
  }

  /**
   * Single finding output for the CRUD endpoints. This is the only representation of a finding the
   * API returns. Its value is the displayed value ({@link Finding#getValueDisplay()}), never the
   * cleartext, so a secret value never leaves the platform.
   */
  public FindingOutput toFindingOutput(Finding finding, long occurrencesCount) {
    Optional<FindingOccurrence> occurrence = latestOccurrence(finding);
    Optional<Inject> inject = occurrence.map(FindingOccurrence::getInject);
    Optional<Exercise> simulation = inject.map(Inject::getExercise);
    return FindingOutput.builder()
        .id(finding.getId())
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
        .assets(assetsOf(finding).stream().map(Asset::getId).toList())
        .teams(finding.getTeams().stream().map(Team::getId).toList())
        .users(finding.getUsers().stream().map(User::getId).toList())
        .occurrencesCount(occurrencesCount)
        .location(toLocation(finding.getLocation()))
        .field(occurrence.map(FindingOccurrence::getField).orElse(null))
        .simulation(simulation.map(exerciseMapper::toExerciseSimple).orElse(null))
        .scenario(scenarioOf(simulation))
        .assetGroups(assetGroupsOf(finding, assetGroupsByAssetId(List.of(finding))))
        .build();
  }

  public FindingSimple toFindingSimple(Finding finding) {
    return toFindingSimple(finding, assetGroupsByAssetId(List.of(finding)));
  }

  /** A page of search rows, with the asset groups of all its findings resolved in one go. */
  public Page<FindingSimple> toFindingSimplePage(Page<Finding> findings) {
    Map<String, List<AssetGroup>> assetGroupsByAssetId =
        assetGroupsByAssetId(findings.getContent());
    return findings.map(finding -> toFindingSimple(finding, assetGroupsByAssetId));
  }

  private FindingSimple toFindingSimple(
      Finding finding, Map<String, List<AssetGroup>> assetGroupsByAssetId) {
    Optional<Inject> inject = latestOccurrence(finding).map(FindingOccurrence::getInject);
    Optional<Exercise> simulation = inject.map(Inject::getExercise);
    return FindingSimple.builder()
        .id(finding.getId())
        .type(finding.getType())
        .value(finding.getValueDisplay())
        .asset(
            Optional.ofNullable(finding.getLocation())
                .map(FindingLocation::getAsset)
                .map(endpointMapper::toEndpointSimple)
                .orElse(null))
        .firstSeen(firstSeenOf(finding))
        .lastSeen(lastSeenOf(finding))
        .creationDate(finding.getCreationDate())
        .updateDate(finding.getUpdateDate())
        .assets(
            assetsOf(finding).stream()
                .map(endpointMapper::toEndpointSimple)
                .collect(Collectors.toSet()))
        .assetGroups(assetGroupsOf(finding, assetGroupsByAssetId))
        .inject(inject.map(injectMapper::toInjectSimple).orElse(null))
        .simulation(simulation.map(exerciseMapper::toExerciseSimple).orElse(null))
        .scenario(scenarioOf(simulation))
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
        .build();
  }

  private ScenarioSimple scenarioOf(Optional<Exercise> simulation) {
    return simulation.map(Exercise::getScenario).map(scenarioMapper::toScenarioSimple).orElse(null);
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
