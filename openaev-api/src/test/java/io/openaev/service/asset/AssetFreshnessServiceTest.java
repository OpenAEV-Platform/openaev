package io.openaev.service.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Endpoint;
import io.openaev.database.repository.AssetRepository;
import io.openaev.rest.settings.form.AssetFreshnessSettingsInput;
import io.openaev.service.settings.TenantSettingsService;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;

@DisplayName("Asset freshness service: the pass window and its batches")
class AssetFreshnessServiceTest {

  private static final String TENANT = "tenant-a";
  private static final Instant NOW = Instant.parse("2026-11-22T08:00:00Z");

  private final AssetRepository assetRepository = mock(AssetRepository.class);
  private final TenantSettingsService settings = mock(TenantSettingsService.class);
  private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  private final AssetFreshnessService service =
      new AssetFreshnessService(assetRepository, settings, publisher, mock(ObjectMapper.class));

  @BeforeEach
  void optIn() {
    when(settings.findAssetFreshnessSettings(TENANT))
        .thenReturn(new AssetFreshnessSettingsInput(30, false));
  }

  @Test
  @DisplayName("given no previous pass should read nothing")
  void given_noPreviousPass_should_readNothing() {
    assertThat(service.raiseStaleTransitions(TENANT, null, NOW)).isEmpty();
    verifyNoInteractions(assetRepository, publisher);
  }

  @Test
  @DisplayName("given a previous pass that is not before now should read nothing")
  void given_previousPassNotBeforeNow_should_readNothing() {
    assertThat(service.raiseStaleTransitions(TENANT, NOW, NOW)).isEmpty();
    assertThat(service.raiseStaleTransitions(TENANT, NOW.plusSeconds(1), NOW)).isEmpty();
    verifyNoInteractions(assetRepository, publisher);
  }

  @Test
  @DisplayName("given full batches should stop at the batch cap, one event per asset")
  void given_fullBatches_should_stopAtTheCap() {
    // Arrange: every batch comes back full, so only the cap ends the pass
    Endpoint endpoint = new Endpoint();
    endpoint.setId("asset-full");
    List<Asset> fullBatch = Collections.nCopies(AssetFreshnessService.BATCH_SIZE, endpoint);
    when(assetRepository.findAssetsLastSeenBetween(
            eq(TENANT), any(Instant.class), any(Instant.class), any(Pageable.class)))
        .thenReturn(fullBatch);

    // Act
    List<Asset> turnedStale =
        service.raiseStaleTransitions(TENANT, NOW.minus(Duration.ofHours(1)), NOW);

    // Assert
    int expected = AssetFreshnessService.BATCH_SIZE * AssetFreshnessService.MAX_BATCHES;
    assertThat(turnedStale).hasSize(expected);
    verify(assetRepository, times(AssetFreshnessService.MAX_BATCHES))
        .findAssetsLastSeenBetween(
            eq(TENANT), any(Instant.class), any(Instant.class), any(Pageable.class));
    verify(publisher, times(expected)).publishEvent(any(BaseEvent.class));
  }
}
