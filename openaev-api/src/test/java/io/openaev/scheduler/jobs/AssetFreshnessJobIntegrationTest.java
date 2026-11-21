package io.openaev.scheduler.jobs;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Asset;
import io.openaev.database.model.Asset.ASSET_ACTIVITY_STATUS;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.rest.settings.form.AssetFreshnessSettingsInput;
import io.openaev.service.asset.AssetFreshnessService;
import io.openaev.service.settings.TenantSettingsService;
import io.openaev.utils.fixtures.EndpointFixture;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

/**
 * Freshness: STALE is a value of asset_status, read from the last seen stamp and the tenant's
 * threshold, and the freshness pass raises one asset update event per transition. Not
 * transactional: {@code forEachTenant} refuses to run inside an active transaction, as in
 * production.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
@DisplayName("Asset freshness: STALE status and transition events")
class AssetFreshnessJobIntegrationTest extends IntegrationTest {

  private static final Duration INTERVAL = Duration.ofHours(1);

  @Autowired private AssetFreshnessJob assetFreshnessJob;
  @Autowired private AssetFreshnessService assetFreshnessService;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private TenantSettingsService tenantSettingsService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents applicationEvents;

  private final List<String> seededAssets = new ArrayList<>();
  private String tenantB;

  @BeforeEach
  void arrange() {
    // The check is off by default: the tests that exercise it opt in with a 30-day threshold.
    setStaleAfterDays(DEFAULT_TENANT_UUID, 30);
    tenantB = UUID.randomUUID().toString();
    jdbc.update(
        "INSERT INTO tenants (tenant_id, tenant_name) VALUES (?, ?)",
        tenantB,
        "asset-freshness-b-" + tenantB);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    seededAssets.forEach(id -> jdbc.update("DELETE FROM assets WHERE asset_id = ?", id));
    seededAssets.clear();
    clearSetting(DEFAULT_TENANT_UUID);
    clearSetting(tenantB);
    jdbc.update("DELETE FROM tenants WHERE tenant_id = ?", tenantB);
  }

  @Test
  @DisplayName("given the default setting should report no STALE asset and emit no asset event")
  void given_defaultSetting_should_changeNothingAndEmitNoEvent() {
    // Arrange: no tenant value, so the default applies
    clearSetting(DEFAULT_TENANT_UUID);
    assertThat(
            inScope(
                DEFAULT_TENANT_UUID,
                () -> assetFreshnessService.staleAfterDays(DEFAULT_TENANT_UUID)))
        .isZero();
    String asset = seedEndpoint(DEFAULT_TENANT_UUID, Duration.ofDays(400));
    applicationEvents.clear();

    // Act
    assetFreshnessJob.execute(null);
    assetFreshnessJob.execute(null);

    // Assert: behaviour identical to main, no STALE status and no event on the asset
    assertThat(status(DEFAULT_TENANT_UUID, asset)).isNotEqualTo(ASSET_ACTIVITY_STATUS.STALE);
    assertThat(eventsOn(asset)).as("no event on the asset with the defaults").isZero();
  }

  @Test
  @DisplayName("given an asset unseen past the threshold should read STALE, then not once seen")
  void given_unseenAsset_should_readStaleThenFreshWhenSeen() {
    // Arrange
    String unseen = seedEndpoint(DEFAULT_TENANT_UUID, Duration.ofDays(31));
    String recent = seedEndpoint(DEFAULT_TENANT_UUID, Duration.ofDays(1));
    String neverSeen = seedEndpoint(DEFAULT_TENANT_UUID, null);

    // Assert: the status is read, nothing has to run
    assertThat(status(DEFAULT_TENANT_UUID, unseen)).isEqualTo(ASSET_ACTIVITY_STATUS.STALE);
    assertThat(status(DEFAULT_TENANT_UUID, recent)).isNotEqualTo(ASSET_ACTIVITY_STATUS.STALE);
    assertThat(status(DEFAULT_TENANT_UUID, neverSeen))
        .as("never observed: no verdict")
        .isNotEqualTo(ASSET_ACTIVITY_STATUS.STALE);

    // Act: the asset is seen again through the model
    inScopeDo(
        DEFAULT_TENANT_UUID,
        () -> {
          Endpoint endpoint = endpointRepository.findById(unseen).orElseThrow();
          endpoint.markSeen(Instant.now(), "agent:openaev_agent");
          endpointRepository.save(endpoint);
        });

    // Assert
    assertThat(status(DEFAULT_TENANT_UUID, unseen))
        .as("seen again: out of STALE at once")
        .isNotEqualTo(ASSET_ACTIVITY_STATUS.STALE);
  }

  @Test
  @DisplayName("given an asset crossing the threshold should raise one event, not twice")
  void given_assetCrossingThreshold_should_raiseOneEvent() {
    // Arrange: crossed the 30-day threshold 10 minutes ago, inside the pass window
    Instant now = Instant.now();
    String crossed = seedEndpoint(DEFAULT_TENANT_UUID, Duration.ofDays(30).plusMinutes(10));
    String longStale = seedEndpoint(DEFAULT_TENANT_UUID, Duration.ofDays(31));
    applicationEvents.clear();

    // Act: two consecutive passes
    List<Asset> first =
        inScope(
            DEFAULT_TENANT_UUID,
            () ->
                assetFreshnessService.raiseStaleTransitions(
                    DEFAULT_TENANT_UUID, now.minus(INTERVAL), now));
    List<Asset> second =
        inScope(
            DEFAULT_TENANT_UUID,
            () ->
                assetFreshnessService.raiseStaleTransitions(
                    DEFAULT_TENANT_UUID, now, now.plus(INTERVAL)));

    // Assert
    assertThat(first).extracting(Asset::getId).contains(crossed).doesNotContain(longStale);
    assertThat(second).extracting(Asset::getId).doesNotContain(crossed);
    assertThat(eventsOn(crossed)).as("one transition, one event").isEqualTo(1);
    assertThat(eventsOn(longStale)).as("crossed before the window: no event").isZero();
  }

  @Test
  @DisplayName("given the setting at 0 should never read STALE")
  void given_settingOff_should_neverReadStale() {
    // Arrange
    setStaleAfterDays(DEFAULT_TENANT_UUID, 0);
    String asset = seedEndpoint(DEFAULT_TENANT_UUID, Duration.ofDays(400));

    // Assert
    assertThat(status(DEFAULT_TENANT_UUID, asset)).isNotEqualTo(ASSET_ACTIVITY_STATUS.STALE);
  }

  @Test
  @DisplayName("given another tenant without the setting should see no STALE and no transition")
  void given_otherTenant_should_seeNoStaleTransition() {
    // Arrange: tenant A opted in (30 days), tenant B did not; both have an asset that crossed
    Instant now = Instant.now();
    String assetA = seedEndpoint(DEFAULT_TENANT_UUID, Duration.ofDays(30).plusMinutes(10));
    String assetB = seedEndpoint(tenantB, Duration.ofDays(30).plusMinutes(10));
    applicationEvents.clear();

    // Act: each tenant's pass under its own scope
    List<Asset> passA =
        inScope(
            DEFAULT_TENANT_UUID,
            () ->
                assetFreshnessService.raiseStaleTransitions(
                    DEFAULT_TENANT_UUID, now.minus(INTERVAL), now));
    List<Asset> passB =
        inScope(
            tenantB,
            () -> assetFreshnessService.raiseStaleTransitions(tenantB, now.minus(INTERVAL), now));

    // Assert: tenant A's threshold never reaches tenant B
    assertThat(passA).extracting(Asset::getId).contains(assetA).doesNotContain(assetB);
    assertThat(passB).isEmpty();
    assertThat(status(tenantB, assetB)).isNotEqualTo(ASSET_ACTIVITY_STATUS.STALE);
    assertThat(eventsOn(assetB)).isZero();
  }

  // -- HELPERS --

  private void setStaleAfterDays(String tenantId, int days) {
    inScopeDo(
        tenantId,
        () ->
            tenantSettingsService.updateAssetFreshnessSettings(
                tenantId, new AssetFreshnessSettingsInput(days)));
  }

  private void clearSetting(String tenantId) {
    jdbc.update(
        "DELETE FROM parameters WHERE parameter_key = 'asset_stale_after_days'"
            + " AND tenant_id = ?",
        tenantId);
  }

  private String seedEndpoint(String tenantId, Duration lastSeenAgo) {
    return inScope(
        tenantId,
        () -> {
          Endpoint endpoint = EndpointFixture.createEndpoint("freshness-" + UUID.randomUUID());
          endpoint.setTenant(new Tenant(tenantId));
          if (lastSeenAgo != null) {
            endpoint.markSeen(Instant.now().minus(lastSeenAgo), "test");
          }
          endpointRepository.save(endpoint);
          seededAssets.add(endpoint.getId());
          return endpoint.getId();
        });
  }

  private ASSET_ACTIVITY_STATUS status(String tenantId, String assetId) {
    return inScope(
        tenantId, () -> endpointRepository.findById(assetId).orElseThrow().getActivityStatus());
  }

  private long eventsOn(String assetId) {
    return applicationEvents.stream(BaseEvent.class)
        .filter(event -> event.getInstance() != null)
        .filter(event -> assetId.equals(event.getInstance().getId()))
        .count();
  }

  private <T> T inScope(String tenantId, Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(tenantId), work);
  }

  private void inScopeDo(String tenantId, Runnable work) {
    tenantTx.execute(TxCtx.forTenant(tenantId), work);
  }
}
