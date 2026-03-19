package io.openaev.api.stix_process;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.stix.StixService;
import io.openaev.service.stix.error.BundleValidationError;
import io.openaev.stix.parsing.ParsingException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(StixApi.STIX_URI)
@Tag(name = "STIX API", description = "Operations related to STIX bundles")
public class StixApi extends RestBehavior {

  public static final String STIX_URI = "/api/stix";
  private final ObjectMapper objectMapper;
  private final StixService stixService;
  private final OpenCTIConnectorService openCTIService;

  @PostMapping(
      value = "/process-bundle",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Process a STIX bundle",
      description =
          "Processes a STIX bundle and generates related entities such as Scenarios and Injects.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "STIX bundle processed successfully"),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid STIX bundle (e.g., too many security coverages)"),
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
  })
  @RBAC(actionPerformed = Action.PROCESS, resourceType = ResourceType.STIX_BUNDLE)
  public ResponseEntity<?> processBundle(@RequestBody String ctiEvent)
      throws ParsingException, ConnectorError, IOException {
    String workId = null;
    try {
      JsonNode root = objectMapper.readTree(ctiEvent);
      workId = root.path("internal").path("work_id").asText();
      String stixJson =
          root.path("event").path("stix_objects").asText(); // As text is required here
      // Acknowledge the scenario creation / enrichment by sending back the security coverage
      openCTIService.acknowledgeReceivedOfCoverage(
          workId, "OpenAEV ready to process the operation");
      // Create scenario from stix bundle
      // If no simulation for this scenario is in progress, start an execution right away

      Scenario scenario = stixService.processBundle(stixJson);
      openCTIService.acknowledgeProcessedOfCoverage(
          workId, "Coverage successfully created or updated", false);
      // Generate response
      String summary = stixService.generateBundleImportReport(scenario);
      return ResponseEntity.ok(new BundleImportReport(scenario.getId(), summary));
    } catch (BundleValidationError e) {
      // OCTI-specific behaviour
      // in the case of a Bundle validation error,
      // we will submit to the specific behaviour of the OCTI worker which is unable
      // to recover in the event of a permanent error.
      // we will signal the failure with a log in the OAEV process and an "isError" ack
      // for OpenCTI
      log.error(
          String.format(
              "OpenAEV did not process this STIX bundle due to processing rules (workId=%s). ctiEvent=%s. Error: %s",
              workId, ctiEvent, e.getMessage()),
          e);
      openCTIService.acknowledgeProcessedOfCoverage(
          workId,
          "OpenAEV did not process this STIX bundle due to processing rules: %s"
              .formatted(e.getMessage()),
          true);
      // here we explicitly return a status of HTTP 200 OK
      // it's a silent error
      return ResponseEntity.status(HttpStatus.OK).build();
    } catch (JsonParseException e) {
      log.error(
          String.format(
              "Input STIX bundle is malformed (workId=%s). ctiEvent=%s. Error: %s",
              workId, ctiEvent, e.getMessage()),
          e);
      openCTIService.acknowledgeProcessedOfCoverage(
          workId, "Input STIX bundle is malformed: %s".formatted(e.getMessage()), true);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    } catch (Exception e) {
      log.error(
          String.format(
              "An error occurred while processing STIX bundle (workId=%s). ctiEvent=%s. Error: %s",
              workId, ctiEvent, e.getMessage()),
          e);
      openCTIService.acknowledgeProcessedOfCoverage(
          workId,
          "An error occurred while processing STIX bundle: %s".formatted(e.getMessage()),
          true);
      throw e;
    }
  }

  public record BundleImportReport(String scenarioId, String importSummary) {}
}
