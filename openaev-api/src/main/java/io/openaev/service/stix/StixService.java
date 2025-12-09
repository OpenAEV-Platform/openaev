package io.openaev.service.stix;

import io.openaev.cron.ScheduleFrequency;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.service.ScenarioService;
import io.openaev.service.cron.CronService;
import io.openaev.stix.parsing.ParsingException;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class StixService {

  private final SecurityCoverageService securityCoverageService;
  private final CronService cronService;
  private final ScenarioService scenarioService;

  /**
   * Generate or update a Scenario from Stix bundle
   *
   * @param stixJson
   * @return Scenario
   */
  @Transactional(rollbackFor = Exception.class)
  public ScenarioSecurityCoverage processBundle(String stixJson)
      throws IOException, ParsingException {
    // Update securityCoverage with the last bundle
    SecurityCoverage securityCoverage =
        securityCoverageService.buildSecurityCoverageFromStix(stixJson);
    // Update Scenario using the last SecurityCoverage
    Scenario scenario = securityCoverageService.buildScenarioFromSecurityCoverage(securityCoverage);
    return new ScenarioSecurityCoverage(scenario, securityCoverage);
  }

  public record ScenarioSecurityCoverage(Scenario scenario, SecurityCoverage securityCoverage) {}

  /**
   * Set recurrence for the scenario coming from OpenCTI. The scenario will start immediately after
   * the save
   *
   * @param scenarioSecurityCoverage
   * @return Scenario
   */
  @Transactional(rollbackFor = Exception.class)
  public Scenario setRecurrence(ScenarioSecurityCoverage scenarioSecurityCoverage) {
    Scenario scenario = scenarioSecurityCoverage.scenario();
    SecurityCoverage securityCoverage = scenarioSecurityCoverage.securityCoverage();
    if (scenario.getRecurrence() == null) {
      // Start date must be before the recurrence and now
      Instant start = Instant.now().minusSeconds(60);
      // Recurrence must be at least 1 "true" minute after now to be scheduled and executed (see
      // ScenarioExecutionJob and examples below)
      // Example 1: recurrence 11:33:00 + 120 seconds = 11:35:00 -> job each minute to schedule and
      // execute at recurrence (without second) 11:35 - 1 minute = 11:34
      // Example 2: recurrence 11:33:59 + 120 seconds = 11:35:59 -> job each minute to schedule and
      // execute at recurrence (without second) 11:35 - 1 minute = 11:34
      Instant recurrence = Instant.now().plusSeconds(120);
      if (securityCoverage.getScheduling() != null && !securityCoverage.getScheduling().isEmpty()) {
        scenario.setRecurrenceStart(start);
        ScheduleFrequency frequency = ScheduleFrequency.DAILY;
        if (securityCoverage.getScheduling().contains("W")) {
          frequency = ScheduleFrequency.WEEKLY;
        } else if (securityCoverage.getScheduling().contains("M")) {
          frequency = ScheduleFrequency.MONTHLY;
        }
        // TODO cron should be generated from start-date + iso duration
        // Currently UI is not able to support any cron expression
        // Parsing is limited to same case like 1 day at 9h00.
        // Monthly option is not supported yet back in the UI.
        String cron = cronService.getCronExpression(frequency, recurrence);
        scenario.setRecurrence(cron);
      } else {
        String cron = cronService.getCronExpression(ScheduleFrequency.ONESHOT, recurrence);
        scenario.setRecurrence(cron);
      }
      scenario = scenarioService.updateScenario(scenario);
    }
    return scenario;
  }

  /**
   * Builds a bundle import report
   *
   * @param scenario
   * @return string contains bundle import report
   */
  public String generateBundleImportReport(Scenario scenario) {
    String summary = null;
    if (scenario.getInjects().isEmpty()) {
      summary =
          "The current scenario does not contain injects. "
              + "This can occur when: (1) no Attack Patterns or vulnerabilities are defined in the STIX bundle, "
              + "or (2) the specified Attack Patterns and vulnerabilities are not available in the OAEV platform.";
    } else {
      summary = "Scenario with Injects created successfully";
    }
    return summary;
  }
}
