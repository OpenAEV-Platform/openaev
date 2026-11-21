package io.openaev.scheduler.jobs;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;
import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.IntegrationTest;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.audit.BaseEvent;
import io.openaev.database.model.Endpoint;
import io.openaev.database.model.Tenant;
import io.openaev.database.repository.EndpointRepository;
import io.openaev.rest.settings.form.AssetFreshnessSettingsInput;
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
 * The freshness job turns an asset unseen for longer than the tenant allows stale, once, and fresh
 * again when it is seen. Not transactional: {@code forEachTenant} refuses to run inside an active
 * transaction, as in production. State is asserted through JDBC.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@RecordApplicationEvents
@DisplayName("AssetFreshnessJob")
class AssetFreshnessJobIntegrationTest extends IntegrationTest {

  @Autowired private AssetFreshnessJob assetFreshnessJob;
  @Autowired private EndpointRepository endpointRepository;
  @Autowired private TenantSettingsService tenantSettingsService;
  @Autowired private TenantScopedTransaction tenantTx;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ApplicationEvents applicationEvents;

  private final List<String> seededAssets = new ArrayList<>();

  @BeforeEach
  void optIn() {
    // The check is off by default: the tests that exercise it opt in with a 30-day threshold.
    setStaleAfterDays(30);
  }

  @AfterEach
  void cleanup() {
    TenantContext.clearCurrentTenant();
    seededAssets.forEach(id -> jdbc.update("DELETE FROM assets WHERE asset_id = ?", id));
    seededAssets.clear();
    clearSetting();
  }

  @Test
  @DisplayName("given the default setting should change no asset and emit no asset event")
  void given_defaultSetting_should_changeNothingAndEmitNoEvent() {
    // Arrange: no tenant value, so the default applies
    clearSetting();
    assertThat(inScope(() -> tenantSettingsService.findAssetFreshnessSettings(DEFAULT_TENANT_UUID)))
        .isEqualTo(new AssetFreshnessSettingsInput(0));
    String asset = seedEndpoint(Duration.ofDays(400));
    applicationEvents.clear();

    // Act
    assetFreshnessJob.execute(null);
    assetFreshnessJob.execute(null);

    // Assert: behaviour identical to main, no state change and no event on the asset
    assertThat(isStale(asset)).isFalse();
    assertThat(staleSince(asset)).isNull();
    assertThat(
            applicationEvents.stream(BaseEvent.class)
                .filter(event -> event.getInstance() != null)
                .filter(event -> asset.equals(event.getInstance().getId())))
        .as("no event on the asset with the defaults")
        .isEmpty();
  }

  @Test
  @DisplayName("given an asset unseen past the threshold should turn stale once, then fresh")
  void given_unseenAsset_should_turnStaleOnceThenFresh() {
    // Arrange
    String unseen = seedEndpoint(Duration.ofDays(31));
    String recent = seedEndpoint(Duration.ofDays(1));
    String neverSeen = seedEndpoint(null);

    // Act 1
    assetFreshnessJob.execute(null);

    // Assert 1
    assertThat(isStale(unseen)).as("unseen for 31 days, threshold 30").isTrue();
    assertThat(staleSince(unseen)).isNotNull();
    assertThat(isStale(recent)).isFalse();
    assertThat(isStale(neverSeen)).as("never observed: no verdict").isFalse();

    // Act 2: a second pass does not move the stale date (one transition, one event)
    Instant firstStaleSince = staleSince(unseen);
    assetFreshnessJob.execute(null);
    assertThat(staleSince(unseen)).isEqualTo(firstStaleSince);

    // Arrange 3: the asset is seen again through the model
    inScopeDo(
        () -> {
          Endpoint endpoint = endpointRepository.findById(unseen).orElseThrow();
          endpoint.markSeen(Instant.now(), "agent:openaev_agent");
          endpointRepository.save(endpoint);
        });

    // Assert 3
    assertThat(isStale(unseen)).as("seen again: fresh at once").isFalse();
    assertThat(staleSince(unseen)).isNull();
  }

  @Test
  @DisplayName("given a stale asset whose last seen moved without markSeen should turn fresh")
  void given_staleAssetSeenByBulkWrite_should_turnFresh() {
    // Arrange
    String asset = seedEndpoint(Duration.ofDays(40));
    assetFreshnessJob.execute(null);
    assertThat(isStale(asset)).isTrue();
    jdbc.update(
        "UPDATE assets SET asset_last_seen = ? WHERE asset_id = ?",
        java.sql.Timestamp.from(Instant.now()),
        asset);

    // Act
    assetFreshnessJob.execute(null);

    // Assert
    assertThat(isStale(asset)).isFalse();
  }

  @Test
  @DisplayName("given the setting at 0 should never turn an asset stale")
  void given_settingOff_should_neverTurnStale() {
    // Arrange
    setStaleAfterDays(0);
    String asset = seedEndpoint(Duration.ofDays(400));

    // Act
    assetFreshnessJob.execute(null);

    // Assert
    assertThat(isStale(asset)).isFalse();
  }

  // -- HELPERS --

  private void setStaleAfterDays(int days) {
    inScopeDo(
        () ->
            tenantSettingsService.updateAssetFreshnessSettings(
                DEFAULT_TENANT_UUID, new AssetFreshnessSettingsInput(days)));
  }

  private void clearSetting() {
    jdbc.update(
        "DELETE FROM tenant_settings WHERE tenant_setting_key = 'asset_stale_after_days'"
            + " AND tenant_id = ?",
        DEFAULT_TENANT_UUID);
  }

  private String seedEndpoint(Duration lastSeenAgo) {
    return inScope(
        () -> {
          Endpoint endpoint = EndpointFixture.createEndpoint("freshness-" + UUID.randomUUID());
          endpoint.setTenant(new Tenant(DEFAULT_TENANT_UUID));
          if (lastSeenAgo != null) {
            endpoint.markSeen(Instant.now().minus(lastSeenAgo), "test");
          }
          endpointRepository.save(endpoint);
          seededAssets.add(endpoint.getId());
          return endpoint.getId();
        });
  }

  private boolean isStale(String assetId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT asset_stale FROM assets WHERE asset_id = ?", Boolean.class, assetId));
  }

  private Instant staleSince(String assetId) {
    java.sql.Timestamp ts =
        jdbc.queryForObject(
            "SELECT asset_stale_since FROM assets WHERE asset_id = ?",
            java.sql.Timestamp.class,
            assetId);
    return ts == null ? null : ts.toInstant();
  }

  private <T> T inScope(Supplier<T> work) {
    return tenantTx.execute(TxCtx.forTenant(DEFAULT_TENANT_UUID), work);
  }

  private void inScopeDo(Runnable work) {
    tenantTx.execute(TxCtx.forTenant(DEFAULT_TENANT_UUID), work);
  }
}
