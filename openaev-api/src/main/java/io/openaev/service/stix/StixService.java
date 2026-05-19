package io.openaev.service.stix;

import io.openaev.database.model.Scenario;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.service.stix.error.BundleValidationError;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.objects.ObjectBase;
import io.openaev.stix.objects.constants.CommonProperties;
import io.openaev.stix.parsing.Parser;
import io.openaev.stix.parsing.ParsingException;
import io.openaev.utils.SecurityCoverageUtils;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class StixService {
  private final Parser stixParser;
  private final SecurityCoverageService securityCoverageService;
  private final SecurityCoverageUtils securityCoverageUtils;

  /**
   * Generate or update a Scenario from Stix bundle
   *
   * @param stixJson string form of the provided stix bundle
   * @return Scenario
   */
  public Scenario processBundle(String stixJson)
      throws IOException, ParsingException, ConnectorError, BundleValidationError {
    Bundle bundle = stixParser.parseBundle(stixJson);

    return processSecurityCoverage(bundle);
  }

  private Scenario processSecurityCoverage(Bundle bundle)
      throws BundleValidationError, ParsingException, ConnectorError, IOException {
    ObjectBase securityCoverageObj = securityCoverageUtils.extractAndValidateCoverage(bundle);
    String securityCoverageStixId =
        securityCoverageObj.getRequiredProperty(CommonProperties.ID.toString());

    return securityCoverageService.handleSecurityCoverageProcessing(
        securityCoverageStixId, securityCoverageObj, bundle);
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
