package io.openaev.api.stix_process;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.dto.CTIEvent;
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
import org.springframework.validation.annotation.Validated;
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
  @AccessControl(actionPerformed = Action.PROCESS, resourceType = ResourceType.STIX_BUNDLE)
  public ResponseEntity<?> processBundle(@RequestBody @Validated CTIEvent ctiEvent)
      throws ParsingException, ConnectorError, IOException {
    try {
      openCTIService.acknowledgeReceivedOfCoverage(
          ctiEvent.getInternal().getWorkId(), "OpenAEV ready to process the operation");

      Scenario scenario = stixService.processBundle(ctiEvent.getEvent().getStixObjects());

      openCTIService.acknowledgeProcessedOfCoverage(
          ctiEvent.getInternal().getWorkId(), "Coverage successfully created or updated", false);
      return ResponseEntity.ok(
          new BundleImportReport(
              scenario.getId(), stixService.generateBundleImportReport(scenario)));
    } catch (BundleValidationError e) {
      // OCTI-specific behaviour
      // in the case of a Bundle validation error,
      // we will submit to the specific behaviour of the OCTI worker which is unable
      // to recover in the event of a permanent error.
      // we will signal the failure with a log in the OAEV process and an "isError" ack
      // for OpenCTI
      log.error(
          "OpenAEV did not process this STIX bundle due to processing rules (workId={}). ctiEvent={}",
          ctiEvent.getInternal().getWorkId(),
          ctiEvent,
          e);
      openCTIService.acknowledgeProcessedOfCoverage(
          ctiEvent.getInternal().getWorkId(),
          "OpenAEV did not process this STIX bundle due to processing rules: %s"
              .formatted(e.getMessage()),
          true);
      // here we explicitly return a status of HTTP 200 OK
      // it's a silent error
      return ResponseEntity.status(HttpStatus.OK).build();
    } catch (Exception e) {
      log.error(
          "An error occurred while processing STIX bundle (workId={}). ctiEvent={}",
          ctiEvent.getInternal().getWorkId(),
          ctiEvent,
          e);
      openCTIService.acknowledgeProcessedOfCoverage(
          ctiEvent.getInternal().getWorkId(),
          "An error occurred while processing STIX bundle: %s".formatted(e.getMessage()),
          true);
      throw e;
    }
  }

  public record BundleImportReport(String scenarioId, String importSummary) {}
}
