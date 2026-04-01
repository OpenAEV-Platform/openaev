package io.openaev.scheduler.jobs;

import io.openaev.service.tenants.TenantService;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

/** Job that permanently deletes all soft-deleted tenants whose the grace period has expired. */
@Component
@RequiredArgsConstructor
public class TenantPurgeJob implements Job {

  public static final String TENANT_PURGE_JOB = "tenantPurgeJob";
  public static final String TENANT_PURGE_TRIGGER = "tenantPurgeTrigger";

  private static final Logger log = Logger.getLogger(TenantPurgeJob.class.getName());

  private final TenantService tenantService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    int count = tenantService.purgeExpiredTenants();
    if (count > 0) {
      log.info("Permanently deleted " + count + " expired tenant(s).");
    }
  }
}
