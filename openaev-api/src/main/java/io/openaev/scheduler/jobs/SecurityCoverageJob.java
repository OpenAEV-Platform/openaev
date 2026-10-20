package io.openaev.scheduler.jobs;

import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TenantContext;
import io.openaev.context.TenantScopedTransaction;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Exercise;
import io.openaev.database.model.SecurityCoverageSendJob;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.service.SecurityCoverageSendJobService;
import io.openaev.service.stix.SecurityCoverageService;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.parsing.ParsingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private final TenantScopedTransaction tenantTx;

  /** What leaves the cross-tenant read of the pending jobs: ids, never the entities it loaded. */
  private record PendingSendJob(String jobId, String simulationId, String tenantId) {}

  private record TenantBundle(SecurityCoverageSendJob sendJob, Bundle bundle) {}

  // No job-level @Transactional here: a transaction spanning the whole loop held a pooled DB
  // connection across every external OpenCTI HTTP push (potentially minutes when OpenCTI is slow
  // or unreachable), contributing to Hikari pool exhaustion. Bundle creation is transactional
  // inside SecurityCoverageService#createBundleFromSendJobs; the push runs connection-free.
  @Override
  @LogExecutionTime
  public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
    // The send jobs carry no tenant, their simulations do: the poll spans every tenant, then each
    // job is loaded, bundled and consumed under its simulation's tenant.
    List<PendingSendJob> jobs = tenantTx.execute(TxCtx.allTenants(), this::readPendingJobs);
    Map<String, List<SecurityCoverageSendJob>> successfulJobsByTenant = new LinkedHashMap<>();
    // Tenants without an active Security Coverage connector: skip their jobs upfront (before any
    // bundle creation) and log a single concise warning per tenant instead of one ERROR with a
    // full stack trace per pending simulation on every run (log flooding + useless DB load).
    Set<String> tenantsWithoutConnector = new HashSet<>();
    Set<String> tenantsNotRegistered = new HashSet<>();
    for (PendingSendJob pendingJob : jobs) {
      String tenantId = pendingJob.tenantId();
      try {
        if (tenantId == null) {
          throw new IllegalStateException("Simulation or tenant not found");
        }
        if (tenantsWithoutConnector.contains(tenantId) || tenantsNotRegistered.contains(tenantId)) {
          continue;
        }
        var connector = openCTIConnectorService.getConnectorBase(tenantId);
        if (connector.isEmpty()) {
          tenantsWithoutConnector.add(tenantId);
          continue;
        }
        if (!connector.get().isRegistered()) {
          tenantsNotRegistered.add(tenantId);
          continue;
        }
        // Set tenant context for downstream Hibernate filters and audit
        TenantContext.setCurrentTenant(tenantId);
        // send bundle
        log.info(
            "Bundle creating for Security coverage job id {} for tenant {}",
            pendingJob.jobId(),
            tenantId);
        TenantBundle tenantBundle = buildBundleInTenantScope(tenantId, pendingJob.jobId());
        log.info(
            "Bundle {} created for Security coverage job id {} for tenant {}",
            tenantBundle.bundle().getId(),
            pendingJob.jobId(),
            tenantId);
        openCTIConnectorService.pushSecurityCoverageStixBundle(tenantBundle.bundle(), tenantId);
        successfulJobsByTenant
            .computeIfAbsent(tenantId, key -> new ArrayList<>())
            .add(tenantBundle.sendJob());
      } catch (Exception e) {
        // don't crash the job; a send job without a simulation throws above
        if (e instanceof ConnectorError
            && e.getMessage() != null
            && e.getMessage().contains("hasn't registered yet")) {
          log.warn(
              "Could not create the STIX bundle for coverage {} of simulation {} and tenant {}: {}",
              pendingJob.jobId(),
              ofNullable(pendingJob.simulationId()).orElse("?"),
              ofNullable(tenantId).orElse("?"),
              e.getMessage());
        } else {
          log.error(
              "Could not create the STIX bundle for coverage {} of simulation {} and tenant {}",
              pendingJob.jobId(),
              ofNullable(pendingJob.simulationId()).orElse("?"),
              ofNullable(tenantId).orElse("?"),
              e);
        }
      } finally {
        TenantContext.clearCurrentTenant();
      }
    }
    if (!tenantsWithoutConnector.isEmpty()) {
      log.warn(
          "Security coverage bundles not sent: no active Security Coverage connector for tenant(s)"
              + " {}. Jobs stay pending until a connector is configured.",
          tenantsWithoutConnector);
    }
    if (!tenantsNotRegistered.isEmpty()) {
      log.warn(
          "Security coverage bundles not sent: OpenCTI connector for tenant(s) {} is configured but"
              + " not registered (check URL/token). Jobs stay pending.",
          tenantsNotRegistered);
    }
    successfulJobsByTenant.forEach(this::consumeInTenantScope);
  }

  private List<PendingSendJob> readPendingJobs() {
    return securityCoverageSendJobService.getPendingSecurityCoverageSendJobs().stream()
        .map(
            sendJob -> {
              Exercise simulation = sendJob.getSimulation();
              return simulation == null
                  ? new PendingSendJob(sendJob.getId(), null, null)
                  : new PendingSendJob(
                      sendJob.getId(), simulation.getId(), simulation.getTenant().getId());
            })
        .toList();
  }

  private TenantBundle buildBundleInTenantScope(String tenantId, String sendJobId) {
    return tenantTx.execute(
        TxCtx.forTenant(tenantId),
        () -> {
          SecurityCoverageSendJob sendJob =
              securityCoverageSendJobService.securityCoverageSendJob(sendJobId);
          try {
            return new TenantBundle(
                sendJob, securityCoverageService.createBundleFromSendJobs(List.of(sendJob)));
          } catch (ParsingException | JsonProcessingException e) {
            throw new IllegalStateException(e);
          }
        });
  }

  private void consumeInTenantScope(String tenantId, List<SecurityCoverageSendJob> sendJobs) {
    // v2 scope only: the send jobs are not tenant rows and are re-read natively, and their
    // simulation is loaded by id, which the v1 filter never narrows.
    try {
      tenantTx.execute(
          TxCtx.forTenant(tenantId), () -> securityCoverageSendJobService.consumeJobs(sendJobs));
    } catch (RuntimeException e) {
      // The bundles are pushed; the jobs stay PENDING and are sent again on the next run.
      log.error("Could not mark the security coverage jobs of tenant {} as sent", tenantId, e);
    }
  }
}
