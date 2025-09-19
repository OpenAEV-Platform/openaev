package io.openbas.api.stix_process;

import io.openbas.aop.RBAC;
import io.openbas.database.model.Action;
import io.openbas.database.model.ResourceType;
import io.openbas.database.model.Scenario;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.service.stix.StixService;
import io.openbas.stix.parsing.ParsingException;
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

  private final StixService stixService;

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
  @RBAC(actionPerformed = Action.CREATE, resourceType = ResourceType.SCENARIO)
  public ResponseEntity<?> processBundle(@RequestBody String stixJson) {
    try {
      Scenario scenario = stixService.processBundle(stixJson);
      String summary = stixService.generateBundleImportReport(scenario);
      BundleImportReport importReport = new BundleImportReport(scenario.getId(), summary);
      return ResponseEntity.ok(importReport);
    } catch (ParsingException | IOException e) {
      log.error(String.format("Parsing error while processing STIX bundle: %s", e.getMessage()), e);
      return ResponseEntity.status(HttpStatus.BAD_REQUEST)
          .body("Parsing error while processing STIX bundle.");
    } catch (Exception e) {
      log.error(
          String.format(
              "An unexpected server error occurred. Please contact support: %s", e.getMessage()),
          e);
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  public record BundleImportReport(String scenarioId, String importSummary) {}
}
