package io.openaev.scheduler.jobs;

import static org.mockito.Mockito.*;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrlAccessTokenPurgeJob")
class UrlAccessTokenPurgeJobTest {

  @Mock private UrlAccessTokenService urlAccessTokenService;
  @Mock private PreviewFeatureService previewFeatureService;

  @InjectMocks private UrlAccessTokenPurgeJob job;

  @Nested
  @DisplayName("When feature is disabled")
  class WhenFeatureIsDisabled {

    @Test
    @DisplayName("given_url_access_feature_disabled_should_not_run_purge")
    void given_url_access_feature_disabled_should_not_run_purge() throws Exception {
      // Arrange
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.URL_ACCESS_TOKEN))
          .thenReturn(false);

      // Act
      job.execute(null);

      // Assert
      verify(urlAccessTokenService, never()).purgeExpiredAndRevokedTokens();
    }
  }

  @Nested
  @DisplayName("When feature is enabled")
  class WhenFeatureIsEnabled {

    @Test
    @DisplayName("given_url_access_feature_enabled_should_run_purge")
    void given_url_access_feature_enabled_should_run_purge() throws Exception {
      // Arrange
      when(previewFeatureService.isFeatureEnabled(PreviewFeature.URL_ACCESS_TOKEN))
          .thenReturn(true);
      when(urlAccessTokenService.purgeExpiredAndRevokedTokens()).thenReturn(2);

      // Act
      job.execute(null);

      // Assert
      verify(urlAccessTokenService).purgeExpiredAndRevokedTokens();
    }
  }
}
