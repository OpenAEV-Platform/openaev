package io.openaev.scheduler.jobs;

import io.openaev.service.autonomous.AutonomousTimeoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Quartz job that enforces the OpenAEV-owned autonomous-run timeout. It periodically sweeps every
 * tenant's live runs and, for each whose deadline is near or passed, queues a winddown steering
 * nudge (5 min / 1 min before) or hard-stops the run - so an autonomous run can never stay RUNNING
 * indefinitely when its orchestrator crashes, disconnects, or never posts a terminal status.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DisallowConcurrentExecution
public class AutonomousTimeoutJob implements Job {

  private final AutonomousTimeoutService autonomousTimeoutService;

  @Override
  public void execute(JobExecutionContext jobExecutionContext) {
    try {
      autonomousTimeoutService.sweep();
    } catch (Exception e) {
      log.error("[Autonomous] Timeout sweep failed. Will retry on next cycle.", e);
    }
  }
}
