package io.openaev.scheduler.jobs;

import io.openaev.api.url_access_token.UrlAccessTokenService;
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

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    int deletedCount = urlAccessTokenService.purgeExpiredAndRevokedTokens();
    if (deletedCount > 0) {
      log.info("Purged {} URL access token(s)", deletedCount);
    }
  }
}
