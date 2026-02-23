package io.openaev.service.stix;

import io.openaev.database.model.Scenario;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.stix.parsing.ParsingException;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class StixService {

  private final SecurityCoverageService securityCoverageService;

  /**
   * Generate or update a Scenario from Stix bundle
   *
   * @param stixJson string form of the provided stix bundle
   * @return Scenario
   */
  @Transactional(rollbackFor = Exception.class)
  public Scenario processBundle(String stixJson)
      throws IOException, ParsingException, ConnectorError {

    return processSecurityCoverage(bundle);
  }

      // Update Scenario using the last SecurityCoverage
      Scenario scenario =
          securityCoverageService.buildScenarioFromSecurityCoverage(securityCoverage);
      securityCoverageService.pushSecurityCoverageBundleWithExternalURI(scenario);
      return scenario;
    } catch (BadRequestException | ParsingException e) {
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
