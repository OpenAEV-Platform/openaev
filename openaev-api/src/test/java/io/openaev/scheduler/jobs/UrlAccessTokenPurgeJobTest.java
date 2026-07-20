package io.openaev.scheduler.jobs;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UrlAccessTokenPurgeJob")
class UrlAccessTokenPurgeJobTest {

  @Mock private UrlAccessTokenService urlAccessTokenService;
  @Mock private TenantScopedTransaction tenantTx;

  @InjectMocks private UrlAccessTokenPurgeJob job;

  @Test
  @DisplayName("Given url access token purge job should run purge")
  void given_url_access_token_purge_job_should_run_purge() throws Exception {
    // Arrange
    when(urlAccessTokenService.purgeExpiredAndRevokedTokens()).thenReturn(2);
    // The primitive runs the work it is given; the transaction itself is not this test's subject.
    when(tenantTx.execute(any(TxCtx.class), ArgumentMatchers.<Supplier<Integer>>any()))
        .thenAnswer(invocation -> invocation.<Supplier<Integer>>getArgument(1).get());

    // Act
    job.execute(null);

    // Assert
    verify(urlAccessTokenService).purgeExpiredAndRevokedTokens();
  }
}
