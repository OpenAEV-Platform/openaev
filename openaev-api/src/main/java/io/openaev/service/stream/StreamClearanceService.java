package io.openaev.service.stream;

import io.openaev.config.MarkedTables;
import io.openaev.config.MarkingBypass;
import io.openaev.config.cache.MarkingClearanceCacheManager;
import io.openaev.context.MarkingCtx;
import io.openaev.database.model.User;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Resolves the marking clearance a stream reader's events are checked against.
 *
 * <p>The HTTP path filters rows in SQL with the clearance of the request; the stream cannot, since
 * it sends the entity loaded by the publisher. So each event is checked, per reader, against the
 * same clearance ({@link MarkingClearanceCacheManager}, already cached: no query per event). The
 * markings it is checked against travel with the event (see {@link
 * io.openaev.database.audit.BaseEvent#isVisibleUnder}).
 */
@Service
@RequiredArgsConstructor
public class StreamClearanceService {

  private static final String ASSETS_TABLE = "assets";

  private final MarkingClearanceCacheManager clearanceCache;
  private final MarkedTables markedTables;
  private final PreviewFeatureService previewFeatureService;

  /**
   * Returns the clearance of the user in the tenant: {@link MarkingCtx#all()} when nothing is
   * filtered for them.
   *
   * @param tenantId the tenant whose clearance applies: a marking belongs to one tenant. With none,
   *     the reader holds no marking and only receives unmarked entities (fail-closed).
   */
  public MarkingCtx clearanceFor(User user, String tenantId) {
    // Same switches as the SQL filtering: with the MARKING preview feature off, or assets not
    // activated for marking, nothing is filtered.
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.MARKING)
        || markedTables.get(ASSETS_TABLE) == null
        || MarkingBypass.bypassesMarkings(user)) {
      return MarkingCtx.all();
    }
    if (tenantId == null) {
      return MarkingCtx.none();
    }
    return clearanceCache.findClearance(user.getId(), tenantId, false);
  }
}
