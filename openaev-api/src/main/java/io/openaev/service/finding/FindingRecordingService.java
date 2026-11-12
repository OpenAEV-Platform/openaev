package io.openaev.service.finding;

import static io.openaev.helper.StreamHelper.fromIterable;

import io.openaev.context.TxCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Finding;
import io.openaev.database.model.FindingKey;
import io.openaev.database.model.FindingLocation;
import io.openaev.database.model.FindingLocationKeys;
import io.openaev.database.model.Inject;
import io.openaev.database.model.Tag;
import io.openaev.database.model.Tenant;
import io.openaev.database.model.User;
import io.openaev.database.repository.FindingLocationRepository;
import io.openaev.database.repository.FindingRepository;
import io.openaev.finding.identity.DetectedFinding;
import io.openaev.finding.identity.LocationIdentity;
import io.openaev.finding.identity.LocationIdentity.AssetLocation;
import io.openaev.finding.identity.LocationIdentity.UnknownLocation;
import io.openaev.finding.identity.LocationIdentity.UserLocation;
import io.openaev.rest.tag.TagService;
import io.openaev.service.AssetService;
import io.openaev.service.UserService;
import io.openaev.utils.ReferenceResolver;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** The single entry point to write findings: what an inject detected, and when. */
@Service
@RequiredArgsConstructor
public class FindingRecordingService {

  private final FindingRepository findingRepository;
  private final FindingLocationRepository findingLocationRepository;
  private final FindingOccurrenceService findingOccurrenceService;
  private final TagService tagService;
  private final AssetService assetService;
  private final UserService userService;
  private final ReferenceResolver referenceResolver;

  /**
   * Records the findings an inject detected and their occurrence on it, in a transaction of its
   * own: a failure leaves neither a finding without occurrence nor the caller's work rolled back.
   * Detections sharing a finding key make one finding: the most recent gives its value and name,
   * the first the field of its occurrence, and their tags add up.
   *
   * @return the findings, found or created
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
  public List<Finding> recordFindingsByInject(
      @NotNull final TxCtx ctx,
      @NotNull final Inject inject,
      @NotNull final List<DetectedFinding> detections) {
    if (detections.isEmpty()) {
      return List.of();
    }
    String tenantId = singleTenantOf(ctx, inject.getId());
    Instant date = FindingOccurrenceService.occurrenceDate(inject);

    // 1 - keep only the assets and users that exist in the tenant
    List<DetectedFinding> detectedFindings = withKnownLocations(detections);

    // 2 - find or create the locations (based on their key)
    List<FindingLocation> findingLocations =
        upsertLocations(detectedFindings.stream().map(DetectedFinding::location).toList());

    // 3 - find or create the findings, with their tags
    List<Finding> findings = upsertFindings(tenantId, date, detectedFindings, findingLocations);

    // 4 - record their occurrence on the inject, with the field of their first detection
    findingOccurrenceService.recordOccurrences(
        inject,
        date,
        findings,
        firstFieldByFindingId(detectedFindings, findings, findingLocations));
    return findings;
  }

  /**
   * An asset or a user that does not exist, or that belongs to another tenant, becomes the unknown
   * location: the finding is kept, without a target. The assets are read in the transaction's
   * scope, so another tenant's are not found.
   */
  private List<DetectedFinding> withKnownLocations(List<DetectedFinding> detections) {
    Set<String> knownAssetIds =
        assetService
            .assets(
                detections.stream()
                    .map(detection -> assetIdOf(detection.location()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList())
            .stream()
            .map(Asset::getId)
            .collect(Collectors.toSet());
    Set<String> knownUserIds =
        userService
            .find(
                detections.stream()
                    .map(detection -> userIdOf(detection.location()))
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList())
            .stream()
            .map(User::getId)
            .collect(Collectors.toSet());
    return detections.stream()
        .map(
            detection ->
                switch (detection.location()) {
                  case AssetLocation(String assetId) when !knownAssetIds.contains(assetId) ->
                      withUnknownLocation(detection);
                  case UserLocation(String userId) when !knownUserIds.contains(userId) ->
                      withUnknownLocation(detection);
                  default -> detection;
                })
        .toList();
  }

  private static DetectedFinding withUnknownLocation(DetectedFinding detection) {
    return new DetectedFinding(
        detection.type(),
        detection.value(),
        detection.field(),
        detection.name(),
        UnknownLocation.INSTANCE,
        detection.tagIds());
  }

  /**
   * The tenant the findings are written in: the transaction's scope and the row's tenant are one
   * value, and {@code findings.tenant_id} is NOT NULL.
   *
   * @throws IllegalStateException when the scope names anything but exactly one tenant
   */
  private static String singleTenantOf(TxCtx ctx, String injectId) {
    return switch (ctx) {
      case TxCtx.Restricted restricted when restricted.tenantIds().size() == 1 ->
          restricted.tenantIds().get(0);
      default ->
          throw new IllegalStateException(
              "Cannot write a finding for inject "
                  + injectId
                  + " without a single-tenant scope: findings.tenant_id is NOT NULL and the row"
                  + " would be unattributed. The caller must resolve the tenant from the"
                  + " inject.");
    };
  }

  // -- LOCATIONS --

  private List<FindingLocation> upsertLocations(List<LocationIdentity> locations) {
    Map<String, LocationIdentity> locationByKey =
        locations.stream()
            .collect(
                Collectors.toMap(
                    FindingRecordingService::findingLocationKeyOf,
                    location -> location,
                    (a, b) -> a));
    List<FindingLocation> existing =
        findingLocationRepository.findByLocationKeyIn(locationByKey.keySet());
    Set<String> existingKeys =
        existing.stream().map(FindingLocation::getLocationKey).collect(Collectors.toSet());

    List<FindingLocation> missing =
        locationByKey.entrySet().stream()
            .filter(entry -> !existingKeys.contains(entry.getKey()))
            .map(entry -> newFindingLocation(entry.getValue()))
            .toList();
    // Flushed: the database computes the key the findings are matched on
    List<FindingLocation> added = findingLocationRepository.saveAllAndFlush(missing);
    return Stream.concat(existing.stream(), added.stream()).toList();
  }

  private FindingLocation newFindingLocation(LocationIdentity location) {
    FindingLocation findingLocation = new FindingLocation();
    findingLocation.setAsset(referenceResolver.resolve(assetIdOf(location), Asset.class));
    findingLocation.setUser(referenceResolver.resolve(userIdOf(location), User.class));
    return findingLocation;
  }

  // -- FINDINGS --

  private List<Finding> upsertFindings(
      String tenantId,
      Instant occurrenceDate,
      List<DetectedFinding> detectedFindings,
      List<FindingLocation> findingLocations) {
    Map<String, FindingLocation> findingLocationByKey = findingLocationByKey(findingLocations);
    Map<FindingKey, List<DetectedFinding>> detectionsByKey =
        detectedFindings.stream()
            .collect(
                Collectors.groupingBy(
                    detection -> findingKeyOf(detection, findingLocationByKey),
                    LinkedHashMap::new,
                    Collectors.toList()));
    Map<String, Tag> tagById =
        tagService
            .tagSet(
                detectedFindings.stream()
                    .flatMap(detection -> detection.tagIds().stream())
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(Tag::getId, tag -> tag));

    List<Finding> existing = findFindingsByKeys(tenantId, detectionsByKey.keySet());
    Set<FindingKey> existingKeys =
        existing.stream().map(FindingKey::of).collect(Collectors.toSet());
    List<List<DetectedFinding>> missing =
        detectionsByKey.entrySet().stream()
            .filter(entry -> !existingKeys.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .toList();

    List<Finding> updated =
        refreshExistingFindings(existing, detectionsByKey, tagById, occurrenceDate);
    List<Finding> added =
        addFindings(tenantId, occurrenceDate, missing, findingLocationByKey, tagById);
    return Stream.concat(updated.stream(), added.stream()).toList();
  }

  private List<Finding> refreshExistingFindings(
      List<Finding> existing,
      Map<FindingKey, List<DetectedFinding>> detectionsByKey,
      Map<String, Tag> tagById,
      Instant occurrenceDate) {
    for (Finding finding : existing) {
      List<DetectedFinding> detections = detectionsByKey.get(FindingKey.of(finding));
      refreshFinding(finding, detections.getLast(), occurrenceDate);
      boolean tagsAdded = addTags(finding, detections, tagById);
      boolean locationLinked = linkLegacyLocation(finding, detections.getLast().location());
      // A change of links alone leaves the row untouched: the search index follows updated_at
      if (tagsAdded || locationLinked) {
        finding.setUpdateDate(Instant.now());
      }
    }
    return fromIterable(findingRepository.saveAll(existing));
  }

  private List<Finding> addFindings(
      String tenantId,
      Instant occurrenceDate,
      List<List<DetectedFinding>> missing,
      Map<String, FindingLocation> findingLocationByKey,
      Map<String, Tag> tagById) {
    List<Finding> newFindings = new ArrayList<>();
    for (List<DetectedFinding> detections : missing) {
      DetectedFinding last = detections.getLast();
      Finding finding = new Finding();
      finding.setTenant(new Tenant(tenantId));
      finding.setType(last.type());
      finding.setValue(last.value());
      finding.setName(last.name());
      finding.setValueHash(last.valueHash());
      finding.setLocation(findingLocationByKey.get(findingLocationKeyOf(last.location())));
      finding.setFirstSeen(occurrenceDate);
      finding.setLastSeen(occurrenceDate);
      addTags(finding, detections, tagById);
      linkLegacyLocation(finding, last.location());
      newFindings.add(finding);
    }
    return fromIterable(findingRepository.saveAll(newFindings));
  }

  private List<Finding> findFindingsByKeys(String tenantId, Set<FindingKey> keys) {
    return findingRepository
        .findAllByTenantTypeValueHashAndLocation(
            tenantId,
            keys.stream().map(FindingKey::type).collect(Collectors.toSet()),
            keys.stream().map(FindingKey::valueHash).collect(Collectors.toSet()),
            keys.stream().map(FindingKey::locationId).collect(Collectors.toSet()))
        .stream()
        .filter(finding -> keys.contains(FindingKey.of(finding)))
        .toList();
  }

  // The latest detection gives the value and the name; the seen dates only widen
  private static void refreshFinding(
      Finding finding, DetectedFinding detection, Instant occurrenceDate) {
    // An older inject recorded late keeps the value and the name of the more recent one
    if (finding.getLastSeen() == null || !occurrenceDate.isBefore(finding.getLastSeen())) {
      finding.setValue(detection.value());
      finding.setName(detection.name());
    }
    if (finding.getFirstSeen() == null || occurrenceDate.isBefore(finding.getFirstSeen())) {
      finding.setFirstSeen(occurrenceDate);
    }
    if (finding.getLastSeen() == null || occurrenceDate.isAfter(finding.getLastSeen())) {
      finding.setLastSeen(occurrenceDate);
    }
  }

  // The tags of every detection, so the tags of one finding add up; an unknown tag is skipped
  private static boolean addTags(
      Finding finding, List<DetectedFinding> detections, Map<String, Tag> tagById) {
    return finding
        .getTags()
        .addAll(
            detections.stream()
                .flatMap(detection -> detection.tagIds().stream())
                .map(tagById::get)
                .filter(Objects::nonNull)
                .toList());
  }

  // Temporary: removed in chunk 7, once the readers go through finding_locations
  private boolean linkLegacyLocation(Finding finding, LocationIdentity location) {
    return switch (location) {
      case AssetLocation(String assetId) ->
          finding.getAssets().stream().noneMatch(asset -> asset.getId().equals(assetId))
              && finding.getAssets().add(referenceResolver.resolve(assetId, Asset.class));
      case UserLocation(String userId) ->
          finding.getUsers().stream().noneMatch(user -> user.getId().equals(userId))
              && finding.getUsers().add(referenceResolver.resolve(userId, User.class));
      case UnknownLocation unknown -> false;
    };
  }

  // -- OCCURRENCES --

  // The occurrence keeps the field of the first detection of its finding
  private static Map<String, String> firstFieldByFindingId(
      List<DetectedFinding> detectedFindings,
      List<Finding> findings,
      List<FindingLocation> findingLocations) {
    Map<String, FindingLocation> findingLocationByKey = findingLocationByKey(findingLocations);
    Map<FindingKey, String> findingIdByKey =
        findings.stream().collect(Collectors.toMap(FindingKey::of, Finding::getId));
    Map<String, String> firstFieldByFindingId = new HashMap<>();
    for (DetectedFinding detection : detectedFindings) {
      firstFieldByFindingId.putIfAbsent(
          findingIdByKey.get(findingKeyOf(detection, findingLocationByKey)), detection.field());
    }
    return firstFieldByFindingId;
  }

  // -- KEYS --

  private static String assetIdOf(LocationIdentity location) {
    return location instanceof AssetLocation(String assetId) ? assetId : null;
  }

  private static String userIdOf(LocationIdentity location) {
    return location instanceof UserLocation(String userId) ? userId : null;
  }

  private static String findingLocationKeyOf(LocationIdentity location) {
    return FindingLocationKeys.keyOf(assetIdOf(location), userIdOf(location));
  }

  private static Map<String, FindingLocation> findingLocationByKey(
      List<FindingLocation> findingLocations) {
    return findingLocations.stream()
        .collect(Collectors.toMap(FindingLocation::getLocationKey, location -> location));
  }

  private static FindingKey findingKeyOf(
      DetectedFinding detection, Map<String, FindingLocation> findingLocationByKey) {
    return new FindingKey(
        detection.type(),
        detection.valueHash(),
        findingLocationByKey.get(findingLocationKeyOf(detection.location())).getId());
  }
}
