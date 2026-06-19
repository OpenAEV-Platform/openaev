package io.openaev.scheduler.jobs;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.service.PreviewFeatureService;
import org.junit.jupiter.api.DisplayName;
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

  @Test
  @DisplayName("given_url_access_feature_enabled_should_run_purge")
  void given_url_access_feature_enabled_should_run_purge() throws Exception {
    // Arrange
    when(urlAccessTokenService.purgeExpiredAndRevokedTokens()).thenReturn(2);

    // Act
    job.execute(null);

    // Assert
    verify(urlAccessTokenService).purgeExpiredAndRevokedTokens();
  }
}
