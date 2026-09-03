package io.openaev.api.snapshot;

import static io.openaev.api.snapshot.SnapshotObservationService.DEFAULT_PAGE_SIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.openaev.api.snapshot.form.SnapshotSearchInput;
import io.openaev.api.snapshot.form.SnapshotSearchOutput;
import io.openaev.config.EngineConfig;
import io.openaev.database.model.IndexingStatus;
import io.openaev.database.raw.RawUserAuth;
import io.openaev.database.repository.AttackObservationRepository;
import io.openaev.database.repository.IndexingStatusRepository;
import io.openaev.database.repository.UserRepository;
import io.openaev.database.repository.VulnerabilityObservationRepository;
import io.openaev.engine.EngineService;
import io.openaev.engine.api.CursorPageQuery;
import io.openaev.engine.model.snapshotobservation.EsAttackObservation;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.utils.mapper.RawUserAuthMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for the horizon/window arithmetic of {@link SnapshotObservationService} (story 7505,
 * §3.1, §9.3, §10.4). Assertions never assume a fixed wall-clock value: relationships are checked
 * against the {@code server_time} the service itself returns in the response, so the test never
 * races {@link Instant#now()}.
 */
@ExtendWith(MockitoExtension.class)
class SnapshotObservationServiceTest {

  private static final String TENANT_ID = "tenant-a";

  @Mock private EngineConfig engineConfig;
  @Mock private EngineService engineService;
  @Mock private IndexingStatusRepository indexingStatusRepository;
  @Mock private AttackObservationRepository attackObservationRepository;
  @Mock private VulnerabilityObservationRepository vulnerabilityObservationRepository;
  @Mock private UserRepository userRepository;
  @Mock private RawUserAuthMapper rawUserAuthMapper;

  private SnapshotObservationService service;
  private SnapshotCursorCodec cursorCodec;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    cursorCodec = new SnapshotCursorCodec(objectMapper);
    service =
        new SnapshotObservationService(
            engineConfig,
            engineService,
            indexingStatusRepository,
            attackObservationRepository,
            vulnerabilityObservationRepository,
            userRepository,
            rawUserAuthMapper,
            cursorCodec,
            new SnapshotObservationMapper());
    // Lenient: the validation test throws before this is ever reached.
    lenient().when(rawUserAuthMapper.toRawUserAuth(any())).thenReturn(mock(RawUserAuth.class));
  }

  private SnapshotSearchInput input(
      String cursor, Instant since, Integer pageSize, Integer safetyLagSeconds) {
    return new SnapshotSearchInput(cursor, since, pageSize, safetyLagSeconds);
  }

  private IndexingStatus indexingStatus(Instant lastIndexing) {
    IndexingStatus status = new IndexingStatus();
    status.setType("snapshot-attack-observation");
    status.setLastIndexing(lastIndexing);
    return status;
  }

  private void givenDocuments(List<EsAttackObservation> docs) {
    when(engineService.searchCursorPaged(any(), eq(EsAttackObservation.class), any()))
        .thenReturn(docs);
  }

  private SnapshotSearchOutput<?> search(SnapshotSearchInput input) {
    return service.searchAttackObservations(TENANT_ID, input);
  }

  @Nested
  @DisplayName("Validation")
  class Validation {

    @Test
    @DisplayName("since and cursor together is rejected (FR26)")
    void given_since_and_cursor_should_reject() {
      assertThatThrownBy(() -> search(input("some-cursor", Instant.now(), null, null)))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("Clamping")
  class Clamping {

    private CursorPageQuery capturedQuery() {
      ArgumentCaptor<CursorPageQuery> captor = ArgumentCaptor.forClass(CursorPageQuery.class);
      verify(engineService)
          .searchCursorPaged(any(), eq(EsAttackObservation.class), captor.capture());
      return captor.getValue();
    }

    @Test
    @DisplayName("page_size below 1 is clamped up to 1")
    void given_pageSize_below_minimum_should_clampToOne() {
      // -- ARRANGE --
      givenDocuments(List.of());

      // -- ACT --
      search(input(null, null, 0, null));

      // -- ASSERT --
      assertThat(capturedQuery().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("page_size above CURSOR_PAGE_MAX_SIZE is clamped down to it")
    void given_pageSize_above_maximum_should_clampToMax() {
      // -- ARRANGE --
      givenDocuments(List.of());

      // -- ACT --
      search(input(null, null, 5_000, null));

      // -- ASSERT --
      assertThat(capturedQuery().size()).isEqualTo(EngineService.CURSOR_PAGE_MAX_SIZE);
    }

    @Test
    @DisplayName("page_size left null defaults to DEFAULT_PAGE_SIZE")
    void given_pageSize_null_should_defaultToDefaultPageSize() {
      // -- ARRANGE --
      givenDocuments(List.of());

      // -- ACT --
      search(input(null, null, null, null));

      // -- ASSERT --
      assertThat(capturedQuery().size()).isEqualTo(DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("safety_lag_seconds below max(60, grace) is clamped up")
    void given_safetyLag_below_floor_should_clampUp() {
      // -- ARRANGE --
      when(engineConfig.getIndexingGraceWindowSeconds()).thenReturn(90L);
      givenDocuments(List.of());

      // -- ACT: floor is max(60, 90) = 90, requesting 10 must clamp to 90 --
      SnapshotSearchOutput<?> output = search(input(null, null, null, 10));

      // -- ASSERT --
      assertThat(output.snapshotWindowEnd()).isEqualTo(output.serverTime().minusSeconds(90));
    }

    @Test
    @DisplayName("safety_lag_seconds above 3600 is clamped down")
    void given_safetyLag_above_ceiling_should_clampDown() {
      // -- ARRANGE --
      when(engineConfig.getIndexingGraceWindowSeconds()).thenReturn(60L);
      givenDocuments(List.of());

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(null, null, null, 999_999));

      // -- ASSERT --
      assertThat(output.snapshotWindowEnd()).isEqualTo(output.serverTime().minusSeconds(3600));
    }
  }

  @Nested
  @DisplayName("Horizon computation")
  class HorizonComputation {

    @Test
    @DisplayName("AC6 warming index: probe finds pending rows, horizon stays at the cursor")
    void given_probe_true_should_use_cursor_as_horizon() {
      // -- ARRANGE --
      when(engineConfig.getIndexingGraceWindowSeconds()).thenReturn(60L);
      Instant cursor = Instant.now().minusSeconds(300);
      when(indexingStatusRepository.findByType(any()))
          .thenReturn(Optional.of(indexingStatus(cursor)));
      when(attackObservationRepository.existsPendingIndexing(any(), any())).thenReturn(true);
      givenDocuments(List.of());

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(null, null, null, null));

      // -- ASSERT --
      assertThat(output.indexedThrough()).isEqualTo(cursor);
    }

    @Test
    @DisplayName("AC6 steady state: probe finds nothing pending, horizon falls back to now - grace")
    void given_probe_false_should_use_fallback_as_horizon() {
      // -- ARRANGE --
      when(engineConfig.getIndexingGraceWindowSeconds()).thenReturn(60L);
      Instant cursor = Instant.now().minusSeconds(300);
      when(indexingStatusRepository.findByType(any()))
          .thenReturn(Optional.of(indexingStatus(cursor)));
      when(attackObservationRepository.existsPendingIndexing(any(), any())).thenReturn(false);
      givenDocuments(List.of());

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(null, null, null, null));

      // -- ASSERT --
      assertThat(output.indexedThrough()).isEqualTo(output.serverTime().minusSeconds(60));
    }

    @Test
    @DisplayName("AC9: no indexing_status row falls back to EPOCH, snapshot is not ready")
    void given_no_indexing_status_row_should_use_epoch() {
      // -- ARRANGE: no row ever indexed, so the probe finds pending data from EPOCH --
      when(engineConfig.getIndexingGraceWindowSeconds()).thenReturn(60L);
      when(indexingStatusRepository.findByType(any())).thenReturn(Optional.empty());
      when(attackObservationRepository.existsPendingIndexing(any(), any())).thenReturn(true);
      givenDocuments(List.of());

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(null, null, null, null));

      // -- ASSERT --
      assertThat(output.indexedThrough()).isEqualTo(Instant.EPOCH);
      assertThat(output.snapshotReady()).isFalse();
    }

    @Test
    @DisplayName("§3.1 skip: cursor within safety_lag of now skips the probe entirely")
    void given_cursor_within_safety_lag_should_skip_probe() {
      // -- ARRANGE --
      when(engineConfig.getIndexingGraceWindowSeconds()).thenReturn(60L);
      Instant cursor = Instant.now().minusSeconds(30);
      when(indexingStatusRepository.findByType(any()))
          .thenReturn(Optional.of(indexingStatus(cursor)));
      givenDocuments(List.of());

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(null, null, null, 60));

      // -- ASSERT --
      verify(attackObservationRepository, never()).existsPendingIndexing(any(), any());
      assertThat(output.indexedThrough()).isEqualTo(output.serverTime().minusSeconds(60));
    }

    @Test
    @DisplayName("§3.1 boundary: the old 2xgrace rule would have skipped, ours must not")
    void given_cursor_outside_safety_lag_but_inside_2x_grace_should_probe() {
      // -- ARRANGE: lag = 60, grace = 60, cursor = now - 90 (90 < 2*60, but 90 >= 60) --
      when(engineConfig.getIndexingGraceWindowSeconds()).thenReturn(60L);
      Instant cursor = Instant.now().minusSeconds(90);
      when(indexingStatusRepository.findByType(any()))
          .thenReturn(Optional.of(indexingStatus(cursor)));
      when(attackObservationRepository.existsPendingIndexing(any(), any())).thenReturn(false);
      givenDocuments(List.of());

      // -- ACT --
      search(input(null, null, null, 60));

      // -- ASSERT --
      verify(attackObservationRepository).existsPendingIndexing(any(), any());
    }
  }

  @Nested
  @DisplayName("Window and readiness")
  class WindowAndReadiness {

    @Test
    @DisplayName("snapshot_window_end is the smaller of (now - lag) and indexed_through")
    void given_indexedThrough_before_lag_bound_should_clamp_to_indexedThrough() {
      // -- ARRANGE --
      when(engineConfig.getIndexingGraceWindowSeconds()).thenReturn(60L);
      Instant farBehind = Instant.now().minusSeconds(600);
      when(indexingStatusRepository.findByType(any()))
          .thenReturn(Optional.of(indexingStatus(farBehind)));
      when(attackObservationRepository.existsPendingIndexing(any(), any())).thenReturn(true);
      givenDocuments(List.of());

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(null, null, null, 60));

      // -- ASSERT --
      assertThat(output.snapshotWindowEnd()).isEqualTo(farBehind);
      assertThat(output.snapshotReady()).isFalse();
    }

    @Test
    @DisplayName("safety_lag == grace makes readiness a stable comparison (§3.8)")
    void given_safetyLag_equals_grace_should_be_ready() {
      // -- ARRANGE --
      when(engineConfig.getIndexingGraceWindowSeconds()).thenReturn(60L);
      Instant cursor = Instant.now().minusSeconds(5);
      when(indexingStatusRepository.findByType(any()))
          .thenReturn(Optional.of(indexingStatus(cursor)));
      givenDocuments(List.of());

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(null, null, null, 60));

      // -- ASSERT --
      assertThat(output.snapshotReady()).isTrue();
      assertThat(output.snapshotWindowEnd()).isEqualTo(output.indexedThrough());
    }
  }

  @Nested
  @DisplayName("Paging")
  class Paging {

    @Test
    @DisplayName("has_more is true when the page is exactly full")
    void given_full_page_should_have_more() {
      // -- ARRANGE --
      EsAttackObservation first = observation("a", Instant.now().minusSeconds(120));
      EsAttackObservation second = observation("b", Instant.now().minusSeconds(60));
      givenDocuments(List.of(first, second));

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(null, null, 2, null));

      // -- ASSERT --
      assertThat(output.hasMore()).isTrue();
      assertThat(output.nextCursor()).isNotNull();
    }

    @Test
    @DisplayName("an empty page echoes back the cursor it was given")
    void given_empty_page_should_echo_incoming_cursor() {
      // -- ARRANGE --
      String incoming =
          cursorCodec.encode(
              new SnapshotCursorCodec.SnapshotCursor(
                  1, TENANT_ID, Instant.now().minusSeconds(600), "a"));
      givenDocuments(List.of());

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(incoming, null, 5, null));

      // -- ASSERT --
      assertThat(output.hasMore()).isFalse();
      assertThat(output.nextCursor()).isEqualTo(incoming);
    }

    @Test
    @DisplayName("next_cursor stays null on an empty page of a since walk")
    void given_empty_page_without_cursor_should_return_null_cursor() {
      // -- ARRANGE --
      givenDocuments(List.of());

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(null, null, 5, null));

      // -- ASSERT --
      assertThat(output.hasMore()).isFalse();
      assertThat(output.nextCursor()).isNull();
    }

    @Test
    @DisplayName("has_more is false when the page is short of the requested size")
    void given_partial_page_should_not_have_more() {
      // -- ARRANGE --
      givenDocuments(List.of(observation("a", Instant.now())));

      // -- ACT --
      SnapshotSearchOutput<?> output = search(input(null, null, 5, null));

      // -- ASSERT --
      assertThat(output.hasMore()).isFalse();
      assertThat(output.nextCursor()).isNotNull();
    }

    private EsAttackObservation observation(String id, Instant updatedAt) {
      EsAttackObservation es = new EsAttackObservation();
      es.setBase_id(id);
      es.setBase_updated_at(updatedAt);
      return es;
    }
  }
}
