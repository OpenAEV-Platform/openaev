package io.openaev.scheduler.jobs;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrlAccessTokenPurgeJob")
class UrlAccessTokenPurgeJobTest {

  @Mock private UrlAccessTokenService urlAccessTokenService;

  @InjectMocks private UrlAccessTokenPurgeJob job;

  @Test
  @DisplayName("Given url access token purge job should run purge")
  void given_url_access_token_purge_job_should_run_purge() throws Exception {
    // Arrange
    when(urlAccessTokenService.purgeExpiredAndRevokedTokens()).thenReturn(2);

    // Act
    job.execute(null);

    // Assert
    verify(urlAccessTokenService).purgeExpiredAndRevokedTokens();
  }
}
