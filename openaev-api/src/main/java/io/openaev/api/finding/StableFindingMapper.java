package io.openaev.api.finding;

import io.openaev.database.model.Asset;
import io.openaev.database.model.AssetGroup;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.FindingLocationType;
import io.openaev.database.model.FindingOccurrence;
import io.openaev.database.model.FindingSeverityBucket;
import io.openaev.database.model.FindingTriageStatus;
import io.openaev.database.model.Inject;
import io.openaev.database.model.StableFinding;
import io.openaev.database.model.Team;
import io.openaev.database.model.User;
import io.openaev.rest.asset.endpoint.form.EndpointSimple;
import io.openaev.rest.asset_group.form.AssetGroupSimple;
import io.openaev.rest.atomic_testing.form.TargetSimple;
import io.openaev.rest.exercise.form.ExerciseSimple;
import io.openaev.rest.inject.output.InjectSimple;
import io.openaev.rest.injector.output.InjectorSimple;
import io.openaev.rest.scenario.form.ScenarioSimple;
import io.openaev.utils.SensitiveValueMaskingUtils;
import io.openaev.utils.TargetType;
import io.openaev.utils.mapper.AssetGroupMapper;
import io.openaev.utils.mapper.EndpointMapper;
import io.openaev.utils.mapper.ExerciseMapper;
import io.openaev.utils.mapper.InjectMapper;
import io.openaev.utils.mapper.InjectorMapper;
import io.openaev.utils.mapper.ScenarioMapper;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StableFindingMapper {

  private final EndpointMapper endpointMapper;
  private final AssetGroupMapper assetGroupMapper;
  private final ExerciseMapper exerciseMapper;
  private final ScenarioMapper scenarioMapper;
  private final InjectMapper injectMapper;
  private final InjectorMapper injectorMapper;
  private final io.openaev.service.finding.SeverityNormalizationService
      severityNormalizationService;

  /**
   * Maps a stable finding and all of its preloaded observations to the compatibility read shape.
   */
  public StableFindingOutput toOutput(StableFinding finding, List<FindingOccurrence> occurrences) {
    FindingOccurrence latest = occurrences.isEmpty() ? null : occurrences.getFirst();
    Inject latestInject = latest == null ? null : latest.getInject();
    Set<EndpointSimple> assets =
        distinct(occurrences.stream().flatMap(o -> o.getAssets().stream()).toList(), Asset::getId)
            .stream()
            .map(endpointMapper::toEndpointSimple)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<TargetSimple> users =
        distinct(occurrences.stream().flatMap(o -> o.getUsers().stream()).toList(), User::getId)
            .stream()
            .map(this::toUserTarget)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<TargetSimple> teams =
        distinct(occurrences.stream().flatMap(o -> o.getTeams().stream()).toList(), Team::getId)
            .stream()
            .map(this::toTeamTarget)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    Set<AssetGroup> allAssetGroups =
        distinct(
            occurrences.stream()
                .map(FindingOccurrence::getInject)
                .filter(Objects::nonNull)
                .flatMap(inject -> inject.getAssetGroups().stream())
                .toList(),
            AssetGroup::getId);
    List<String> locations =
        occurrences.stream()
            .map(FindingOccurrence::getLocation)
            .filter(Objects::nonNull)
            .filter(location -> !location.isBlank())
            .distinct()
            .toList();
    Set<FindingLocationType> locationTypes =
        occurrences.stream()
            .map(FindingOccurrence::getLocationType)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    List<String> locationKeys =
        occurrences.stream()
            .map(FindingOccurrence::getLocationKey)
            .filter(Objects::nonNull)
            .filter(location -> !location.isBlank())
            .distinct()
            .toList();
    FindingSeverityBucket effectiveSeverity =
        severityNormalizationService.normalize(
            finding.getType(),
            occurrences.stream().map(FindingOccurrence::getObservedSeverity).toList(),
            occurrences.stream()
                .flatMap(occurrence -> occurrence.getAssets().stream())
                .map(Asset::getCriticality)
                .toList());

    return StableFindingOutput.builder()
        .id(finding.getId())
        .legacyId(
            latest == null || latest.getMigratedFrom() == null
                ? null
                : latest.getMigratedFrom().getId())
        .field(finding.getContractOutputKey())
        .type(finding.getType())
        .value(SensitiveValueMaskingUtils.maskIfNeeded(finding.getType(), finding.getValue()))
        .location(locations.isEmpty() ? null : String.join(", ", locations))
        .locationType(locationTypes.size() == 1 ? locationTypes.iterator().next() : null)
        .locationKey(locationKeys.size() == 1 ? locationKeys.getFirst() : null)
        .name(latest == null ? null : latest.getTitle())
        .title(latest == null ? null : latest.getTitle())
        .tags(finding.getTags().stream().map(tag -> tag.getId()).collect(Collectors.toSet()))
        .createdAt(finding.getFirstSeen())
        .updatedAt(finding.getLastSeen())
        .humanUpdatedAt(finding.getHumanUpdatedAt())
        .archivedAt(finding.getArchivedAt())
        .source(toSource(finding, latestInject))
        .inject(toInject(latestInject))
        .injectId(latestInject == null ? null : latestInject.getId())
        .simulation(toSimulation(latestInject))
        .scenario(toScenario(latestInject))
        .assetGroups(toAssetGroups(latestInject))
        .assets(assets)
        .users(users)
        .teams(teams)
        .occurrences(occurrences.size())
        .assetsCount(assets.size())
        .usersCount(users.size())
        .teamsCount(teams.size())
        .assetGroupsCount(allAssetGroups.size())
        .triageStatus(
            latest == null
                    || latest.getMigratedFrom() == null
                    || latest.getMigratedFrom().getTriage() == null
                ? FindingTriageStatus.UNTRIAGED
                : latest.getMigratedFrom().getTriage().getStatus())
        .category(finding.getCategory())
        .aggregationCategory(finding.getAggregationCategory())
        .lifecycle(finding.getLifecycle())
        .outcome(latest == null ? null : persistedOutcome(latest.getOutcome()))
        .severity(effectiveSeverity.name())
        .severityId(latest == null ? null : latest.getObservedSeverityId())
        .sourceUid(latest == null ? null : latest.getSourceFindingUid())
        .resource(latest == null ? null : latest.getResource())
        .resourceUid(latest == null ? null : latest.getResource())
        .resourceName(latest == null ? null : latest.getResourceName())
        .resourceType(latest == null ? null : latest.getResourceTypeSnapshot())
        .resourceService(latest == null ? null : latest.getResourceService())
        .cloudProvider(aggregateProvider(occurrences))
        .cloudAccount(latest == null ? null : latest.getResourceAccount())
        .cloudRegion(latest == null ? null : latest.getResourceRegion())
        .description(latest == null ? null : latest.getDescription())
        .evidenceDetail(latest == null ? null : latest.getEvidenceDetail())
        .statusDetail(latest == null ? null : latest.getStatusDetail())
        .risk(latest == null ? null : latest.getRisk())
        .categories(latest == null ? List.of() : toList(latest.getCategories()))
        .attackPatterns(latest == null ? List.of() : toList(latest.getAttackPatterns()))
        .remediation(latest == null ? null : latest.getRemediation())
        .compliance(latest == null ? null : latest.getCompliance())
        .rawData(latest == null ? null : latest.getRawPayload())
        .build();
  }

  /** Maps one occurrence to the existing finding timeline/list-compatible shape. */
  public FindingOccurrenceOutput toOccurrenceOutput(FindingOccurrence occurrence) {
    Inject inject = occurrence.getInject();
    return FindingOccurrenceOutput.builder()
        .id(occurrence.getId())
        .type(occurrence.getStableFinding().getType())
        .value(
            SensitiveValueMaskingUtils.maskIfNeeded(
                occurrence.getStableFinding().getType(), occurrence.getStableFinding().getValue()))
        .createdAt(occurrence.getObservedAt())
        .updatedAt(occurrence.getObservedAt())
        .inject(toInject(inject))
        .injectId(inject == null ? null : inject.getId())
        .simulation(toSimulation(inject))
        .scenario(toScenario(inject))
        .assetGroups(toAssetGroups(inject))
        .assets(
            occurrence.getAssets().stream()
                .map(endpointMapper::toEndpointSimple)
                .collect(Collectors.toCollection(LinkedHashSet::new)))
        .users(
            occurrence.getUsers().stream()
                .map(this::toUserTarget)
                .collect(Collectors.toCollection(LinkedHashSet::new)))
        .teams(
            occurrence.getTeams().stream()
                .map(this::toTeamTarget)
                .collect(Collectors.toCollection(LinkedHashSet::new)))
        .scanId(occurrence.getScanId())
        .outcome(persistedOutcome(occurrence.getOutcome()))
        .title(occurrence.getTitle())
        .description(occurrence.getDescription())
        .evidenceDetail(occurrence.getEvidenceDetail())
        .statusDetail(occurrence.getStatusDetail())
        .severity(occurrence.getObservedSeverity())
        .severityId(occurrence.getObservedSeverityId())
        .sourceUid(occurrence.getSourceFindingUid())
        .risk(occurrence.getRisk())
        .categories(toList(occurrence.getCategories()))
        .attackPatterns(toList(occurrence.getAttackPatterns()))
        .remediation(occurrence.getRemediation())
        .compliance(occurrence.getCompliance())
        .resource(occurrence.getResource())
        .resourceSnapshot(occurrence.getResourceSnapshot())
        .resourceName(occurrence.getResourceName())
        .resourceType(occurrence.getResourceTypeSnapshot())
        .resourceService(occurrence.getResourceService())
        .cloudProvider(normalizeProvider(occurrence.getResourceProvider()))
        .cloudAccount(occurrence.getResourceAccount())
        .cloudRegion(occurrence.getResourceRegion())
        .location(occurrence.getLocation())
        .locationType(occurrence.getLocationType())
        .locationKey(occurrence.getLocationKey())
        .targetRole(occurrence.getTargetRole())
        .evidenceScope(occurrence.getEvidenceScope())
        .rawData(occurrence.getRawPayload())
        .build();
  }

  /** Maps the aggregate counters used by the finding overview summary. */
  public StableFindingSummaryOutput toSummary(
      StableFinding finding, List<FindingOccurrence> occurrences) {
    Set<String> assetIds =
        occurrences.stream()
            .flatMap(o -> o.getAssets().stream())
            .map(Asset::getId)
            .collect(Collectors.toSet());
    Set<String> userIds =
        occurrences.stream()
            .flatMap(o -> o.getUsers().stream())
            .map(User::getId)
            .collect(Collectors.toSet());
    Set<String> teamIds =
        occurrences.stream()
            .flatMap(o -> o.getTeams().stream())
            .map(Team::getId)
            .collect(Collectors.toSet());
    Set<String> assetGroupIds =
        occurrences.stream()
            .map(FindingOccurrence::getInject)
            .filter(Objects::nonNull)
            .flatMap(inject -> inject.getAssetGroups().stream())
            .map(AssetGroup::getId)
            .collect(Collectors.toSet());
    return StableFindingSummaryOutput.builder()
        .id(finding.getId())
        .type(finding.getType())
        .value(SensitiveValueMaskingUtils.maskIfNeeded(finding.getType(), finding.getValue()))
        .firstSeen(finding.getFirstSeen())
        .lastSeen(finding.getLastSeen())
        .occurrences(occurrences.size())
        .locationsCount(
            occurrences.stream()
                .map(
                    occurrence ->
                        new LocationIdentity(
                            occurrence.getLocationType(),
                            occurrence.getLocationKey(),
                            occurrence.getLocation()))
                .distinct()
                .count())
        .assetsCount(assetIds.size())
        .usersCount(userIds.size())
        .teamsCount(teamIds.size())
        .assetGroupsCount(assetGroupIds.size())
        .build();
  }

  static String aggregateProvider(List<FindingOccurrence> occurrences) {
    Set<String> providers =
        occurrences.stream()
            .map(FindingOccurrence::getResourceProvider)
            .map(StableFindingMapper::normalizeProvider)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    return providers.size() == 1 ? providers.iterator().next() : null;
  }

  private static String normalizeProvider(String provider) {
    if (provider == null || provider.isBlank()) {
      return null;
    }
    return provider.trim().toLowerCase(Locale.ROOT);
  }

  private static String persistedOutcome(String outcome) {
    return outcome != null && outcome.equalsIgnoreCase("PASS") ? null : outcome;
  }

  private static List<String> toList(String[] values) {
    return values == null ? List.of() : Arrays.asList(values);
  }

  private InjectorSimple toSource(StableFinding finding, Inject latestInject) {
    return Optional.ofNullable(latestInject)
        .map(Inject::getInjector)
        .or(() -> Optional.ofNullable(finding.getSourceInjector()))
        .map(injectorMapper::toInjectorSimple)
        .orElse(null);
  }

  private InjectSimple toInject(Inject inject) {
    return inject == null ? null : injectMapper.toInjectSimple(inject);
  }

  private ExerciseSimple toSimulation(Inject inject) {
    return Optional.ofNullable(inject)
        .map(Inject::getExercise)
        .map(exerciseMapper::toExerciseSimple)
        .orElse(null);
  }

  private ScenarioSimple toScenario(Inject inject) {
    return Optional.ofNullable(inject)
        .map(Inject::getExercise)
        .map(Exercise::getScenario)
        .map(scenarioMapper::toScenarioSimple)
        .orElse(null);
  }

  private Set<AssetGroupSimple> toAssetGroups(Inject inject) {
    if (inject == null) {
      return Set.of();
    }
    return inject.getAssetGroups().stream()
        .map(assetGroupMapper::toAssetGroupSimple)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  private TargetSimple toUserTarget(User user) {
    return TargetSimple.builder()
        .id(user.getId())
        .name(user.getNameOrEmail())
        .type(TargetType.PLAYERS)
        .build();
  }

  private TargetSimple toTeamTarget(Team team) {
    return TargetSimple.builder()
        .id(team.getId())
        .name(team.getName())
        .type(TargetType.TEAMS)
        .build();
  }

  private static <T> Set<T> distinct(Collection<T> values, Function<T, String> idExtractor) {
    return new LinkedHashSet<>(
        values.stream()
            .collect(
                Collectors.toMap(
                    idExtractor,
                    Function.identity(),
                    (first, ignored) -> first,
                    LinkedHashMap::new))
            .values());
  }

  private record LocationIdentity(FindingLocationType type, String key, String display) {}
}
