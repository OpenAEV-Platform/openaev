package io.openbas.service;

import io.openbas.database.model.Scenario;
import io.openbas.database.model.SecurityCoverage;
import io.openbas.stix.parsing.ParsingException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class StixService {

  private final SecurityCoverageService securityCoverageService;

  @Transactional(rollbackFor = Exception.class)
  public Scenario processBundle(String stixJson) throws IOException, ParsingException {
    // Update securityCoverage with the last bundle
    SecurityCoverage securityCoverage =
        securityCoverageService.buildSecurityCoverageFromStix(stixJson);
    // Update Scenario using the last SecurityCoverage
    Scenario scenario = securityCoverageService.buildScenarioFromSecurityCoverage(securityCoverage);
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
              + "This may happen if no Attack-Pattern is defined in the STIX bundle "
              + "or if the Attack Patterns (TTPs) do not exist in the OAEV platform.";
    } else {
      summary = "Scenario with Injects created successfully";
    }
    return summary;
  }
}
