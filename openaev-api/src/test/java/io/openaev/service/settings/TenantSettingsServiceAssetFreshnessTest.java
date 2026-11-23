package io.openaev.service.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Setting;
import io.openaev.database.repository.SettingRepository;
import io.openaev.opencti.config.XtmConfig;
import io.openaev.rest.settings.form.AssetFreshnessSettingsInput;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TenantSettingsService: asset freshness values stored badly fall back to the defaults")
class TenantSettingsServiceAssetFreshnessTest {

  private static final String TENANT = "tenant-a";

  private final SettingRepository settingRepository = mock(SettingRepository.class);
  private final TenantSettingsService service =
      new TenantSettingsService(
          settingRepository,
          mock(OpenAEVConfig.class),
          mock(ThemeAssetUrlValidator.class),
          mock(XtmConfig.class));

  @Test
  @DisplayName("given a threshold that is not a number should read the default threshold")
  void given_thresholdNotANumber_should_readTheDefault() {
    // Arrange
    when(settingRepository.findAllByTenantId(TENANT))
        .thenReturn(
            List.of(
                new Setting("asset_stale_after_days", "thirty"),
                new Setting("asset_discovery_tag", " TRUE ")));

    // Act
    AssetFreshnessSettingsInput settings = service.findAssetFreshnessSettings(TENANT);

    // Assert
    assertThat(settings.staleAfterDays()).isZero();
    assertThat(settings.discoveryTag()).isTrue();
  }

  @Test
  @DisplayName("given stored values that are null should read the defaults")
  void given_nullValues_should_readTheDefaults() {
    // Arrange
    when(settingRepository.findAllByTenantId(TENANT))
        .thenReturn(
            List.of(
                new Setting("asset_stale_after_days", null),
                new Setting("asset_discovery_tag", null)));

    // Act
    AssetFreshnessSettingsInput settings = service.findAssetFreshnessSettings(TENANT);

    // Assert
    assertThat(settings.staleAfterDays()).isZero();
    assertThat(settings.discoveryTag()).isFalse();
  }
}
