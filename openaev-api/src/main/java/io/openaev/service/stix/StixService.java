package io.openaev.service.stix;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.stix.parsing.Parser;
import io.openaev.stix.parsing.ParsingException;
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
  private final ObjectMapper objectMapper;
  private final Parser stixParser;

  /**
   * Generate or update a Scenario from Stix bundle
   *
   * @param stixJson
   * @return Scenario
   */
  @Transactional(rollbackFor = Exception.class)
  public Scenario processBundle(String stixJson) throws IOException, ParsingException {

    try {
      // Update securityCoverage with the last bundle
      SecurityCoverage securityCoverage =
          securityCoverageService.processAndBuildStixToSecurityCoverage(stixJson);

      // Update Scenario using the last SecurityCoverage
      Scenario scenario =
          securityCoverageService.buildScenarioFromSecurityCoverage(securityCoverage);
      return scenario;
    } catch (BadRequestException | ParsingException e) {
      log.error(String.format("Error while processing STIX bundle: %s", e.getMessage()), e);
      throw e;
    }
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
