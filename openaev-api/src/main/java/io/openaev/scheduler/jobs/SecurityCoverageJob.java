package io.openaev.scheduler.jobs;

import static java.util.Optional.ofNullable;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.SecurityCoverageSendJob;
import io.openaev.database.model.Tenant;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.stix.SecurityCoverageService;
import io.openaev.stix.objects.Bundle;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class SecurityCoverageJob implements Job {
  private final SecurityCoverageSendJobService securityCoverageSendJobService;
  private final SecurityCoverageService securityCoverageService;
  private final OpenCTIConnectorService openCTIConnectorService;

  @Lazy @Autowired private SecurityCoverageJob proxySelf;

  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    TxCtx ctx = TxCtx.noTenant();
    proxySelf.txExecute(ctx);
  }

  @Transactional(rollbackFor = Exception.class)
  public void txExecute(@SuppressWarnings("unused") TxCtx _ctx) {
    List<SecurityCoverageSendJob> jobs =
        securityCoverageSendJobService.getPendingSecurityCoverageSendJobs();
    List<SecurityCoverageSendJob> successfulJobs = new ArrayList<>();
    for (SecurityCoverageSendJob securityCoverageSendJob : jobs) {
      try {
        String tenantId =
            ofNullable(securityCoverageSendJob.getSimulation())
                .map(Exercise::getTenant)
                .map(Tenant::getId)
                .orElseThrow(() -> new IllegalStateException("Simulation or tenant not found"));
        // send bundle
        Bundle resultBundle =
            securityCoverageService.createBundleFromSendJobs(List.of(securityCoverageSendJob));
        openCTIConnectorService.pushSecurityCoverageStixBundle(resultBundle, tenantId);
        successfulJobs.add(securityCoverageSendJob);
      } catch (Exception e) {
        // don't crash the job
        log.error(
            "Could not create the STIX bundle for coverage of simulation {}",
            securityCoverageSendJob.getSimulation().getId(),
            e);
      }
    }
    if (!successfulJobs.isEmpty()) {
      securityCoverageSendJobService.consumeJobs(successfulJobs);
    }
  }
}
