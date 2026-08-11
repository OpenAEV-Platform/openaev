package io.openaev.scheduler.jobs;

import static java.util.Optional.ofNullable;

import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.SecurityCoverageSendJob;
import io.openaev.database.model.Tenant;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.stix.SecurityCoverageService;
import io.openaev.stix.objects.Bundle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class SecurityCoverageJob implements Job {
  private final SecurityCoverageSendJobService securityCoverageSendJobService;
  private final SecurityCoverageService securityCoverageService;
  private final OpenCTIConnectorService openCTIConnectorService;

  // No job-level @Transactional here: a transaction spanning the whole loop held a pooled DB
  // connection across every external OpenCTI HTTP push (potentially minutes when OpenCTI is slow
  // or unreachable), contributing to Hikari pool exhaustion. Bundle creation is transactional
  // inside SecurityCoverageService#createBundleFromSendJobs; the push runs connection-free.
  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    List<SecurityCoverageSendJob> jobs =
        securityCoverageSendJobService.getPendingSecurityCoverageSendJobs();
    List<SecurityCoverageSendJob> successfulJobs = new ArrayList<>();
    // Tenants without an active Security Coverage connector: skip their jobs upfront (before any
    // bundle creation) and log a single concise warning per tenant instead of one ERROR with a
    // full stack trace per pending simulation on every run (log flooding + useless DB load).
    Set<String> tenantsWithoutConnector = new HashSet<>();
    for (SecurityCoverageSendJob securityCoverageSendJob : jobs) {
      try {
        String tenantId =
            ofNullable(securityCoverageSendJob.getSimulation())
                .map(Exercise::getTenant)
                .map(Tenant::getId)
                .orElseThrow(() -> new IllegalStateException("Simulation or tenant not found"));
        if (tenantsWithoutConnector.contains(tenantId)) {
          continue;
        }
        if (openCTIConnectorService.getConnectorBase(tenantId).isEmpty()) {
          tenantsWithoutConnector.add(tenantId);
          continue;
        }
        // Set tenant context for downstream Hibernate filters and audit
        TenantContext.setCurrentTenant(tenantId);
        // send bundle
        log.info(
            "Bundle creating for Security coverage job id {} for tenant {}",
            securityCoverageSendJob.getId(),
            tenantId);
        Bundle resultBundle =
            securityCoverageService.createBundleFromSendJobs(List.of(securityCoverageSendJob));
        log.info(
            "Bundle {} created for Security coverage job id {} for tenant {}",
            resultBundle.getId(),
            securityCoverageSendJob.getId(),
            tenantId);
        openCTIConnectorService.pushSecurityCoverageStixBundle(resultBundle, tenantId);
        successfulJobs.add(securityCoverageSendJob);
      } catch (Exception e) {
        // don't crash the job; getSimulation() can be null (that very case throws above)
        log.error(
            "Could not create the STIX bundle for coverage {} of simulation {} and tenant {}",
            securityCoverageSendJob.getId(),
            ofNullable(securityCoverageSendJob.getSimulation()).map(Exercise::getId).orElse("?"),
            ofNullable(securityCoverageSendJob.getSimulation())
                .map(Exercise::getTenant)
                .map(Tenant::getId)
                .orElse("?"),
            e);
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }
    if (!tenantsWithoutConnector.isEmpty()) {
      log.warn(
          "Security coverage bundles not sent: no active Security Coverage connector for tenant(s) {}. Jobs stay pending until a connector is configured.",
          tenantsWithoutConnector);
    }
    if (!successfulJobs.isEmpty()) {
      securityCoverageSendJobService.consumeJobs(successfulJobs);
    }
  }
}
