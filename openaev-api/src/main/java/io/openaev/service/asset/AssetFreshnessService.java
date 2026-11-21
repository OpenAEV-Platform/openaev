package io.openaev.service.asset;

import io.openaev.database.model.Asset;
import io.openaev.database.repository.AssetRepository;
import io.openaev.service.settings.TenantSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Asset freshness (CTEM: stale or orphaned assets). An asset carries the date it was last observed
 * and the source that observed it ({@link Asset#markSeen}); this service turns the assets of one
 * tenant stale once they are unseen for longer than the tenant's {@code asset_stale_after_days},
 * and fresh again when a later observation landed without going through {@code markSeen}.
 *
 * <p>Each transition is an entity save, so it emits the asset update event the notification engine
 * and the stream deliver (a webhook trigger on asset updates sees it). An asset never observed has
 * no verdict: it is neither fresh nor stale, and is never turned stale.
 *
 * <p>Runs inside the caller's tenant-scoped transaction (the freshness job opens one per tenant).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetFreshnessService {

  /** Assets moved per batch; the job keeps going until a batch comes back short. */
  static final int BATCH_SIZE = 500;

  /** Upper bound of batches per tenant and run, so one tenant cannot hold the job. */
  static final int MAX_BATCHES = 20;

  private final AssetRepository assetRepository;
  private final TenantSettingsService tenantSettingsService;

  /** The number of days after which an unseen asset turns stale; 0 or less: the check is off. */
  public int staleAfterDays(String tenantId) {
    return tenantSettingsService.findAssetFreshnessSettings(tenantId).staleAfterDays();
  }

  /**
   * Applies the freshness verdict to one tenant's assets at {@code now}.
   *
   * @return the assets whose state changed (stale ones first, then the ones fresh again)
   */
  public FreshnessTransitions refreshTenant(String tenantId, Instant now) {
    int days = staleAfterDays(tenantId);
    if (days <= 0) {
      return FreshnessTransitions.NONE;
    }
    Instant threshold = now.minus(Duration.ofDays(days));
    List<Asset> turnedStale = new ArrayList<>();
    for (int batch = 0; batch < MAX_BATCHES; batch++) {
      List<Asset> unseen =
          assetRepository.findFreshAssetsUnseenSince(
              tenantId, threshold, PageRequest.of(0, BATCH_SIZE));
      if (unseen.isEmpty()) {
        break;
      }
      unseen.forEach(asset -> markStale(asset, now));
      assetRepository.saveAll(unseen);
      turnedStale.addAll(unseen);
      if (unseen.size() < BATCH_SIZE) {
        break;
      }
    }
    List<Asset> freshAgain = new ArrayList<>();
    for (int batch = 0; batch < MAX_BATCHES; batch++) {
      List<Asset> seenAgain =
          assetRepository.findStaleAssetsSeenSince(
              tenantId, threshold, PageRequest.of(0, BATCH_SIZE));
      if (seenAgain.isEmpty()) {
        break;
      }
      seenAgain.forEach(AssetFreshnessService::markFresh);
      assetRepository.saveAll(seenAgain);
      freshAgain.addAll(seenAgain);
      if (seenAgain.size() < BATCH_SIZE) {
        break;
      }
    }
    if (!turnedStale.isEmpty() || !freshAgain.isEmpty()) {
      log.info(
          "Asset freshness, tenant {}: {} asset(s) turned stale, {} fresh again (after {} days)",
          tenantId,
          turnedStale.size(),
          freshAgain.size(),
          days);
    }
    return new FreshnessTransitions(turnedStale, freshAgain);
  }

  static void markStale(Asset asset, Instant now) {
    asset.setStale(true);
    asset.setStaleSince(now);
  }

  static void markFresh(Asset asset) {
    asset.setStale(false);
    asset.setStaleSince(null);
  }

  /** The assets one freshness pass moved. */
  public record FreshnessTransitions(List<Asset> turnedStale, List<Asset> freshAgain) {
    public static final FreshnessTransitions NONE = new FreshnessTransitions(List.of(), List.of());
  }
}
