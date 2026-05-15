package io.openaev.scheduler.jobs;

import io.openaev.api.url_access_token.UrlAccessTokenService;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/** Purges old expired or revoked URL access tokens. */
@Component
@RequiredArgsConstructor
@Slf4j
public class UrlAccessTokenPurgeJob implements Job {

  public static final String URL_ACCESS_TOKEN_PURGE_JOB = "urlAccessTokenPurgeJob";
  public static final String URL_ACCESS_TOKEN_PURGE_TRIGGER = "urlAccessTokenPurgeTrigger";

  private final UrlAccessTokenService urlAccessTokenService;
  private final PreviewFeatureService previewFeatureService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.URL_ACCESS_TOKEN)) {
      log.debug("Skipping URL access token purge job because feature flag is disabled");
      return;
    }

    int deletedCount = urlAccessTokenService.purgeExpiredAndRevokedTokens();
    if (deletedCount > 0) {
      log.info("Purged {} URL access token(s)", deletedCount);
    }
  }
}
