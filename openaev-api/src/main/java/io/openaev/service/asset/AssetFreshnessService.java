package io.openaev.service.asset;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.Asset;
import io.openaev.database.repository.AssetRepository;
import io.openaev.service.settings.TenantSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Asset freshness (CTEM: stale or orphaned assets). An asset carries the date it was last observed
 * and the source that observed it ({@link Asset#markSeen}). Staleness is not stored: {@code
 * asset_status} reads {@code STALE} for an asset last seen before the tenant's {@code
 * asset_stale_after_days} (see the formula on {@link Asset}). This service only raises the
 * transition: for every asset that crossed the threshold since the previous pass, it publishes one
 * asset update event, which the notification engine and the stream deliver (a webhook trigger on
 * asset updates sees it once). Leaving STALE needs no event of its own: the observation that ends
 * it is an asset update already.
 *
 * <p>With the setting at 0 (the default) nothing is read and nothing is emitted. Runs inside the
 * caller's tenant-scoped transaction (the freshness job opens one per tenant).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetFreshnessService {

  /** Assets read per batch; the pass keeps going until a batch comes back short. */
  static final int BATCH_SIZE = 500;

  /** Upper bound of batches per tenant and pass, so one tenant cannot hold the job. */
  static final int MAX_BATCHES = 20;

  private final AssetRepository assetRepository;
  private final TenantSettingsService tenantSettingsService;
  private final ApplicationEventPublisher eventPublisher;
  private final ObjectMapper mapper;

  /** The number of days after which an unseen asset turns stale; 0 or less: the check is off. */
  public int staleAfterDays(String tenantId) {
    return tenantSettingsService.findAssetFreshnessSettings(tenantId).staleAfterDays();
  }

  /**
   * Raises the STALE transitions of one tenant's assets between two passes.
   *
   * @param previousPass the time of the previous pass (the window starts there)
   * @param now the time of this pass (the window ends there)
   * @return the assets that turned STALE in the window, oldest observation first
   */
  public List<Asset> raiseStaleTransitions(String tenantId, Instant previousPass, Instant now) {
    int days = staleAfterDays(tenantId);
    if (days <= 0 || previousPass == null || !previousPass.isBefore(now)) {
      return List.of();
    }
    Duration threshold = Duration.ofDays(days);
    Instant from = previousPass.minus(threshold);
    Instant to = now.minus(threshold);
    List<Asset> turnedStale = new ArrayList<>();
    for (int batch = 0; batch < MAX_BATCHES; batch++) {
      List<Asset> crossed =
          assetRepository.findAssetsLastSeenBetween(
              tenantId, from, to, PageRequest.of(batch, BATCH_SIZE));
      crossed.forEach(
          asset ->
              eventPublisher.publishEvent(
                  new BaseEvent(ModelBaseListener.DATA_UPDATE, asset, mapper)));
      turnedStale.addAll(crossed);
      if (crossed.size() < BATCH_SIZE) {
        break;
      }
    }
    if (!turnedStale.isEmpty()) {
      log.info(
          "Asset freshness, tenant {}: {} asset(s) turned stale (unseen for {} days)",
          tenantId,
          turnedStale.size(),
          days);
    }
    return turnedStale;
  }
}
