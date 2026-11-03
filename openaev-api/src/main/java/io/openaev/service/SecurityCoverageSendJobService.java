package io.openaev.service;

import io.openaev.database.model.Exercise;
import io.openaev.database.model.ExerciseStatus;
import io.openaev.database.model.SecurityCoverageSendJob;
import io.openaev.database.raw.RawSecurityCoverageSendJobTenant;
import io.openaev.database.repository.ExerciseRepository;
import io.openaev.database.repository.SecurityCoverageSendJobRepository;
import io.openaev.rest.exception.ElementNotFoundException;
import io.openaev.telemetry.metric_collectors.ResultsMetricCollector;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SecurityCoverageSendJobService {
  private final SecurityCoverageSendJobRepository securityCoverageSendJobRepository;
  private final ExerciseRepository exerciseRepository;
  private final EntityManager entityManager;
  private final ResultsMetricCollector resultsMetricCollector;

  public void createOrUpdateCoverageSendJobForSimulationsIfReady(List<Exercise> exercises) {
    List<SecurityCoverageSendJob> jobs = new ArrayList<>();
    for (Exercise exercise : new HashSet<>(exercises)) { // deduplicate
      createOrUpdateCoverageSendJobForSimulationIfReady(exercise).ifPresent(jobs::add);
    }
    if (!jobs.isEmpty()) {
      securityCoverageSendJobRepository.saveAll(jobs);
    }
  }

  public Optional<SecurityCoverageSendJob> createOrUpdateCoverageSendJobForSimulationIfReady(
      Exercise exercise) {
    if (!shouldCreateCoverageSendJob(exercise)) {
      return Optional.empty();
    }
    Optional<SecurityCoverageSendJob> scsj =
        securityCoverageSendJobRepository.findBySimulation(exercise);
    if (scsj.isPresent()) {
      scsj.get().setStatus("PENDING");
      scsj.get().setUpdatedAt(Instant.now());
      return scsj;
    } else {
      SecurityCoverageSendJob newJob = new SecurityCoverageSendJob();
      newJob.setSimulation(exercise);
      newJob.setUpdatedAt(Instant.now());
      return Optional.of(newJob);
    }
  }

  /** The pending send jobs with their simulation's tenant, limited to the caller's scope. */
  public List<RawSecurityCoverageSendJobTenant> getPendingSecurityCoverageSendJobs() {
    return securityCoverageSendJobRepository.findWithTenantByStatusAndUpdatedAtBefore(
        "PENDING", Instant.now().minus(1, ChronoUnit.MINUTES));
  }

  /** A send job by id, read in the caller's transaction and tenant scope. */
  public SecurityCoverageSendJob securityCoverageSendJob(String id) {
    return securityCoverageSendJobRepository
        .findById(id)
        .orElseThrow(() -> new ElementNotFoundException("Security coverage send job not found"));
  }

  /**
   * Marks the jobs as sent. Joins the caller's transaction: the scheduler runs it under the tenant
   * scope of the jobs' simulation, which a new transaction would drop.
   */
  @Transactional
  public void consumeJobs(List<SecurityCoverageSendJob> jobs) {
    /* force hibernate to forget cache and refetch new data */
    entityManager.flush();
    entityManager.clear();
    /* end clear */
    Map<String, SecurityCoverageSendJob> refetchedJobsById =
        securityCoverageSendJobRepository
            .findAllByIdForUpdate(jobs.stream().map(SecurityCoverageSendJob::getId).toList())
            .stream()
            .collect(Collectors.toMap(SecurityCoverageSendJob::getId, Function.identity()));

    List<SecurityCoverageSendJob> jobsToUpdate = new ArrayList<>();
    for (SecurityCoverageSendJob job : jobs) {
      SecurityCoverageSendJob refetched = refetchedJobsById.get(job.getId());
      if (refetched != null
          && job.getSimulation().equals(refetched.getSimulation())
          && job.getStatus().equals(refetched.getStatus())
          && job.getUpdatedAt().equals(refetched.getUpdatedAt())) {
        refetched.setStatus("SENT");
        jobsToUpdate.add(refetched);
      }
    }
    if (!jobsToUpdate.isEmpty()) {
      securityCoverageSendJobRepository.saveAll(jobsToUpdate);
      // Telemetry: coverage results sent back to the CTI platform.
      resultsMetricCollector.recordCoverageResultsSent(jobsToUpdate.size());
    }
  }

  private boolean shouldCreateCoverageSendJob(Exercise exercise) {
    return exercise != null
        && exercise.getSecurityCoverage() != null
        && ExerciseStatus.FINISHED.equals(exercise.getStatus()) // inlined
        && (exercise.getScenario() == null
            || exerciseRepository.following(exercise).isEmpty()); // inlined
  }
}
