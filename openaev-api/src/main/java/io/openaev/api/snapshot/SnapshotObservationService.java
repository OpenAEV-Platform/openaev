package io.openaev.api.snapshot;

import static io.openaev.config.SessionHelper.currentUser;

import io.openaev.annotation.Indexable;
import io.openaev.api.snapshot.SnapshotCursorCodec.SnapshotCursor;
import io.openaev.api.snapshot.form.AttackObservationOutput;
import io.openaev.api.snapshot.form.SnapshotSearchInput;
import io.openaev.api.snapshot.form.SnapshotSearchOutput;
import io.openaev.api.snapshot.form.VulnerabilityObservationOutput;
import io.openaev.config.EngineConfig;
import io.openaev.database.model.Capability;
import io.openaev.database.model.IndexingStatus;
import io.openaev.database.model.User;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.database.raw.RawUserAuthFlat;
import io.openaev.database.repository.AttackObservationRepository;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.repository.VulnerabilityObservationRepository;
import io.openaev.engine.api.CursorPageQuery;
import io.openaev.engine.facade.EngineService;
import io.openaev.engine.model.EsBase;
import io.openaev.engine.model.snapshotobservation.EsAttackObservation;
import io.openaev.engine.model.snapshotobservation.EsVulnerabilityObservation;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.EsIndexingUtils;
import io.openaev.service.UserService;
import io.openaev.utils.mapper.RawUserAuthMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Readiness, cursor and window computation, and paging delegation for the bulk snapshot export
 * endpoints (Story 1.7/1.8). One generic algorithm shared by both observation streams; only the
 * model class, indexing-status type, existence probe and output mapping differ.
 */
@Service
@RequiredArgsConstructor
public class SnapshotObservationService {

  /** Default {@code safety_lag_seconds} when the client does not supply one (FR27). */
  private static final long DEFAULT_SAFETY_LAG_SECONDS = 120;

  /** Default {@code page_size} when the client does not supply one. */
  static final int DEFAULT_PAGE_SIZE = 500;

  /** Absolute floor of {@code safety_lag_seconds}, regardless of the grace window. */
  private static final long MIN_SAFETY_LAG_SECONDS = 60;

  /** Absolute ceiling of {@code safety_lag_seconds}. */
  private static final long MAX_SAFETY_LAG_SECONDS = 3600;

  private static final String CONSISTENCY_MODE = "eventual";

  private final EngineConfig engineConfig;
  private final EngineService engineService;
  private final IndexingStatusRepository indexingStatusRepository;
  private final AttackObservationRepository attackObservationRepository;
  private final VulnerabilityObservationRepository vulnerabilityObservationRepository;
  private final UserRepository userRepository;
  private final UserService userService;
  private final RawUserAuthMapper rawUserAuthMapper;
  private final SnapshotCursorCodec cursorCodec;
  private final SnapshotObservationMapper mapper;

  public SnapshotSearchOutput<AttackObservationOutput> searchAttackObservations(
      String tenantId, SnapshotSearchInput input) {
    return search(
        tenantId,
        input,
        EsAttackObservation.class,
        indexingType(EsAttackObservation.class),
        attackObservationRepository::existsPendingIndexing,
        mapper::toOutput);
  }

  public SnapshotSearchOutput<VulnerabilityObservationOutput> searchVulnerabilityObservations(
      String tenantId, SnapshotSearchInput input) {
    return search(
        tenantId,
        input,
        EsVulnerabilityObservation.class,
        indexingType(EsVulnerabilityObservation.class),
        vulnerabilityObservationRepository::existsPendingIndexing,
        mapper::toOutput);
  }

  private <T extends EsBase, O> SnapshotSearchOutput<O> search(
      String tenantId,
      SnapshotSearchInput input,
      Class<T> modelClass,
      String indexingType,
      PendingProbe probe,
      Function<T, O> toOutput) {

    if (input.since() != null && input.cursor() != null) {
      throw new BadRequestException("since and cursor are mutually exclusive");
    }

    // FR27: captured once, threaded through every helper below. No helper may call Instant.now().
    Instant now = Instant.now();

    long grace = engineConfig.getIndexingGraceWindowSeconds();
    int size =
        clamp(
            input.pageSize() != null ? input.pageSize() : DEFAULT_PAGE_SIZE,
            1,
            EngineService.CURSOR_PAGE_MAX_SIZE);
    long lag =
        clamp(
            input.safetyLagSeconds() != null
                ? input.safetyLagSeconds()
                : DEFAULT_SAFETY_LAG_SECONDS,
            Math.max(MIN_SAFETY_LAG_SECONDS, grace),
            MAX_SAFETY_LAG_SECONDS);

    SnapshotCursor after =
        input.cursor() == null ? null : cursorCodec.decode(input.cursor(), tenantId);

    Instant indexedThrough = horizon(now, grace, lag, indexingType, probe);
    // Engine documents carry base_updated_at at millisecond resolution while the database and the
    // indexing cursor carry microseconds, so the window may only end on a millisecond the indexer
    // has entirely passed. Serving a millisecond it is still inside would let a row of that same
    // millisecond, indexed later with a smaller id, land behind a cursor already handed out.
    Instant snapshotWindowEnd =
        min(
            now.minusSeconds(lag).truncatedTo(ChronoUnit.MILLIS),
            lastCompleteMillisecond(indexedThrough));
    boolean snapshotReady = !indexedThrough.isBefore(now.minusSeconds(lag));

    CursorPageQuery.Keyset keyset =
        after == null ? null : new CursorPageQuery.Keyset(after.ts(), after.id());
    CursorPageQuery query = new CursorPageQuery(input.since(), keyset, snapshotWindowEnd, size);

    List<T> docs = engineService.searchCursorPaged(grantFilter(), modelClass, query);

    boolean hasMore = docs.size() == size;
    // An empty page echoes the cursor it was given, so a client can always assign next_cursor
    // back onto its own state; null would send its next poll back to the start of the stream.
    String nextCursor =
        docs.isEmpty()
            ? input.cursor()
            : cursorCodec.encode(
                new SnapshotCursor(
                    1, tenantId, docs.getLast().getBase_updated_at(), docs.getLast().getBase_id()));

    List<O> observations = docs.stream().map(toOutput).toList();

    return new SnapshotSearchOutput<>(
        observations,
        nextCursor,
        hasMore,
        snapshotWindowEnd,
        indexedThrough,
        now,
        CONSISTENCY_MODE,
        snapshotReady);
  }

  /**
   * Computes {@code indexed_through}: how far this model's indexing has actually progressed,
   * read-side, from {@code indexing_status} plus an existence probe (FR30).
   *
   * <p>The probe is skipped once {@code cursor >= now - lag + 1ms}: the indexer has then already
   * passed the end of the window this request will serve ({@code min(now - lag, cursor)} and {@code
   * min(now - lag, now - grace)} are both {@code now - lag}, since {@code lag >= grace}), so no
   * pending row below that bound can exist and the probe result could not change what is served.
   * The skip returns {@code fallback} (never the raw cursor): skipping means "the probe would have
   * found nothing pending", the {@code otherwise} branch of FR30.
   *
   * <p>In that branch the returned value is therefore an approximation of the real cursor, off by
   * at most {@code lag - grace} in either direction. It is a readiness signal, not a measurement:
   * what is served stays capped at {@code now - lag} regardless.
   */
  private Instant horizon(
      Instant now, long grace, long lag, String indexingType, PendingProbe probe) {
    Instant fallback = now.minusSeconds(grace);
    Optional<IndexingStatus> status = indexingStatusRepository.findByType(indexingType);
    // A pending reset (no row, or the REINDEX_REQUESTED_CURSOR sentinel a migration wrote) means
    // the
    // index is about to be wiped and re-fed from epoch: the far-future sentinel must not read as a
    // cursor past the window, which would skip the probe and report a ready snapshot.
    boolean resetPending = EsIndexingUtils.isReindexRequested(indexingType, status);
    Instant cursor = resetPending ? Instant.EPOCH : status.get().getLastIndexing();
    String cursorId = resetPending ? null : status.get().getLastId();
    // One millisecond of margin: the window ends on the millisecond of now - lag, and that whole
    // millisecond is only behind the indexer once the cursor has passed its end.
    if (!cursor.isBefore(now.minusSeconds(lag).plusMillis(1))) {
      return fallback;
    }
    return probe.test(cursor, cursorId, fallback) ? cursor : fallback;
  }

  /**
   * The last millisecond every row of which is indexed, given a horizon below which every row is.
   * Rows exactly at the horizon may still be pending (the rest of a keyset tie group), so the
   * millisecond containing it only counts once the horizon has moved past its end.
   */
  private static Instant lastCompleteMillisecond(Instant horizon) {
    return horizon.minusMillis(1).truncatedTo(ChronoUnit.MILLIS);
  }

  /**
   * Whether a row that the indexer has not reached yet exists after the keyset cursor {@code
   * (cursorTs, cursorId)} and at or before {@code upperTs}: the resume predicate of the model's
   * {@code findForIndexing}, bounded above.
   */
  @FunctionalInterface
  private interface PendingProbe {
    boolean test(Instant cursorTs, String cursorId, Instant upperTs);
  }

  /**
   * The grants the page is filtered on, or null when the caller sees every observation. Same rule
   * as the scenario and simulation listings ({@code ScenarioService}, {@code ExerciseService}):
   * admin, BYPASS or {@code ACCESS_ASSESSMENT} see every scenario and simulation, so every
   * observation restricted to one of them; anyone else only sees those its grants cover, plus the
   * unrestricted ones.
   */
  private RawUserAuth grantFilter() {
    User user = userService.currentUser();
    if (user.isAdminOrBypass() || user.getCapabilities().contains(Capability.ACCESS_ASSESSMENT)) {
      return null;
    }
    return currentUserAuth();
  }

  private RawUserAuth currentUserAuth() {
    List<RawUserAuthFlat> usersWithAuthFlat = userRepository.getUserWithAuth(currentUser().getId());
    return rawUserAuthMapper.toRawUserAuth(usersWithAuthFlat);
  }

  private static String indexingType(Class<? extends EsBase> modelClass) {
    return modelClass.getAnnotation(Indexable.class).index();
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  private static long clamp(long value, long min, long max) {
    return Math.max(min, Math.min(max, value));
  }

  private static Instant min(Instant a, Instant b) {
    return a.isBefore(b) ? a : b;
  }
}
