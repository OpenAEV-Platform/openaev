package io.openbas.service.stix;

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

  /**
   * Generate or update a Scenario from Stix bundle
   *
   * @param stixJson
   * @return Scenario
   */
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
              + "This can occur when: (1) no Attack Patterns or vulnerabilities are defined in the STIX bundle, "
              + "or (2) the specified Attack Patterns and vulnerabilities are not available in the OAEV platform.";
    } else {
      summary = "Scenario with Injects created successfully";
    }
    return summary;
  }
}
