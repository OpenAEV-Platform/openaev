package io.openaev.service.stix;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.config.OpenAEVConfig;
import io.openaev.database.model.Scenario;
import io.openaev.database.model.SecurityCoverage;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.stix.objects.Bundle;
import io.openaev.stix.objects.DomainObject;
import io.openaev.stix.objects.constants.CommonProperties;
import io.openaev.stix.parsing.Parser;
import io.openaev.stix.parsing.ParsingException;
import io.openaev.stix.types.Identifier;
import io.openaev.stix.types.StixString;
import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
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
  @Resource private OpenAEVConfig openAEVConfig;
  private final OpenCTIConnectorService openCTIConnectorService;

  /**
   * Generate or update a Scenario from Stix bundle
   *
   * @param stixJson
   * @return Scenario
   */
  @Transactional(rollbackFor = Exception.class)
  public Scenario processBundle(String stixJson)
      throws IOException, ParsingException, ConnectorError {

    try {
      // Update securityCoverage with the last bundle
      SecurityCoverage securityCoverage =
          securityCoverageService.processAndBuildStixToSecurityCoverage(stixJson);

      // Update Scenario using the last SecurityCoverage
      Scenario scenario =
          securityCoverageService.buildScenarioFromSecurityCoverage(securityCoverage);
      pushCoverageToOpenCTI(scenario);
      return scenario;
    } catch (BadRequestException | ParsingException e) {
      throw e;
    }
  }

  /**
   * Pushes the security coverage to OpenCTI. This injects the OpenAEV scenario external URL into
   * the STIX object.
   *
   * @param scenario The scenario containing the security coverage.
   * @throws ParsingException If STIX parsing fails.
   * @throws ConnectorError If the OpenCTI push fails.
   */
  private void pushCoverageToOpenCTI(Scenario scenario)
      throws ParsingException, ConnectorError, IOException {
    SecurityCoverage coverage = scenario.getSecurityCoverage();
    String externalLink = openAEVConfig.getBaseUrl() + "/admin/scenarios/" + scenario.getId();

    DomainObject sdo = (DomainObject) stixParser.parseObject(coverage.getContent());
    sdo.setProperty(CommonProperties.EXTERNAL_URI.toString(), new StixString(externalLink));

    Bundle bundle =
        new Bundle(new Identifier("bundle", UUID.randomUUID().toString()), List.of(sdo));
    openCTIConnectorService.pushSecurityCoverageStixBundle(bundle);
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
