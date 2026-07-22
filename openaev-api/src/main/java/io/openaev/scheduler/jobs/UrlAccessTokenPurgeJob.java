package io.openaev.scheduler.jobs;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/** Purges old expired or revoked URL access tokens. */
@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class UrlAccessTokenPurgeJob implements Job {

  public static final String URL_ACCESS_TOKEN_PURGE_JOB = "urlAccessTokenPurgeJob";
  public static final String URL_ACCESS_TOKEN_PURGE_TRIGGER = "urlAccessTokenPurgeTrigger";

  private final UrlAccessTokenService urlAccessTokenService;
  private final TenantScopedTransaction tenantTx;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    // Background transactions open through the primitive, never @Transactional. The purge is
    // genuinely global (tokens are not tenant rows), so it carries the allTenants intention.
    int deletedCount =
        tenantTx.execute(TxCtx.allTenants(), urlAccessTokenService::purgeExpiredAndRevokedTokens);
    if (deletedCount > 0) {
      log.info("Purged {} URL access token(s)", deletedCount);
    }
  }
}
