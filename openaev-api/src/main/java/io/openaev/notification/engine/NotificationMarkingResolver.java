package io.openaev.notification.engine;

import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.database.model.Asset;
import io.openaev.database.model.NotificationTriggerEventType;
import io.openaev.database.model.ResourceType;
import io.openaev.database.repository.AssetRepository;
import io.openaev.rest.finding.FindingService;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Resolves whether a notification recipient's marking clearance covers a given resource, shared by
 * the LIVE ({@link NotificationEngineService}) and DIGEST ({@link NotificationDigestService})
 * stages so the rule for each resource type is implemented once instead of reinvented per stage.
 *
 * <p>Callers must already hold the resource's tenant scope ({@code TenantContext} + {@code
 * TxCtx.forTenant(...)}) before calling {@link #resolveMarkingSets} or {@link #isVisibleTo}:
 * neither opens one itself.
 */
@Service
@RequiredArgsConstructor
public class NotificationMarkingResolver {

  private final AssetRepository assetRepository;
  private final FindingService findingService;
  private final InjectService injectService;
  private final MarkingClearanceCacheManager markingClearanceCacheManager;
  private final PreviewFeatureService previewFeatureService;

  /**
   * Marking ids relevant to one resource, one array per asset it carries or reaches - the unit
   * {@link #isVisibleTo} and {@code NotificationEngineService.recipientsUnderClearance} check a
   * recipient's clearance against. ASSET reads its own {@code marking_ids} by primary key (hence
   * the explicit tenant id - a Hibernate {@code @Filter} never applies to {@code find()} by key);
   * FINDING and INJECT carry no markings of their own, theirs come from their linked asset(s). A
   * DELETE always resolves to no markings: by the time this runs - post-commit on the async engine
   * thread, or a later digest minute - the resource and its join-table rows are already gone, the
   * same gap {@link NotificationMatchingService} has for filtered triggers on DELETE.
   */
  public List<String[]> resolveMarkingSets(
      ResourceType resourceType,
      String entityId,
      String tenantId,
      NotificationTriggerEventType eventType) {
    if (eventType == NotificationTriggerEventType.DELETE) {
      return List.of();
    }
    return switch (resourceType) {
      case ASSET ->
          assetRepository
              .findByIdAndTenantId(entityId, tenantId)
              .map(Asset::getMarkingIds)
              .filter(ids -> ids != null && ids.length > 0)
              .map(ids -> List.<String[]>of(ids))
              .orElse(List.of());
      case FINDING -> findingService.findAssetMarkingIds(entityId);
      case INJECT -> injectService.findAssetMarkingIds(entityId);
      default -> List.of();
    };
  }

  /**
   * True when {@code userId}'s marking clearance covers at least one of the resource's marking sets
   * (OR across them - a finding/inject stays notifiable through its least-restricted linked asset,
   * the same permissive reading {@link io.openaev.config.MarkingDimension}'s link-rows predicate
   * uses for reads), when the resource carries none, or when the MARKING preview feature is off.
   */
  public boolean isVisibleTo(
      String userId,
      String tenantId,
      ResourceType resourceType,
      String entityId,
      NotificationTriggerEventType eventType) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)) {
      return true;
    }
    List<String[]> markingSets = resolveMarkingSets(resourceType, entityId, tenantId, eventType);
    if (markingSets.isEmpty()) {
      return true;
    }
    MarkingCtx clearance = markingClearanceCacheManager.findClearance(userId, tenantId);
    return markingSets.stream().anyMatch(clearance::coversMarkings);
  }
}
