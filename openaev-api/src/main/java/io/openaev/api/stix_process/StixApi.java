package io.openaev.api.stix_process;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.IocValidation;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Scenario;
import io.openaev.opencti.connectors.service.OpenCTIConnectorService;
import io.openaev.opencti.dto.CTIEvent;
import io.openaev.opencti.errors.ConnectorError;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.stix.IocValidationService;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({StixApi.STIX_URI, StixApi.TENANT_STIX_URI})
@Tag(name = "STIX API", description = "Operations related to STIX bundles")
public class StixApi extends RestBehavior {

  public static final String STIX_URI = "/api/stix";
  public static final String TENANT_STIX_URI = TENANT_PREFIX + "/stix";
  private final StixService stixService;
  private final IocValidationService iocValidationService;
  private final OpenCTIConnectorService openCTIService;
  private final TenantWriteScopeResolver writeScopeResolver;

  @PostMapping(
      value = "/process-bundle",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional(rollbackFor = Exception.class)
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
  public ResponseEntity<?> processBundle(TxCtx ctx, @RequestBody @Validated CTIEvent ctiEvent)
      throws ParsingException, ConnectorError, IOException {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    String workId = ctiEvent.getInternal().getWorkId();
    String stixBundle = ctiEvent.getEvent().getStixObjects();

    log.debug("STIX bundle received from OpenCTI (workId={}). bundle={}", workId, stixBundle);

    try {
      openCTIService.acknowledgeReceivedOfCoverage(
          workId, "OpenAEV ready to process the operation", tenantId);

      Scenario scenario = stixService.processBundle(ctx, stixBundle);

      openCTIService.acknowledgeProcessedOfCoverage(
          workId, "Coverage successfully created or updated", false, tenantId);
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
          "OpenAEV did not process this STIX bundle due to processing rules (workId={}). bundle={}",
          workId,
          stixBundle,
          e);
      openCTIService.acknowledgeProcessedOfCoverage(
          workId,
          "OpenAEV did not process this STIX bundle due to processing rules: %s"
              .formatted(e.getMessage()),
          true,
          tenantId);
      // here we explicitly return a status of HTTP 200 OK
      // it's a silent error
      return ResponseEntity.status(HttpStatus.OK).build();
    } catch (Exception e) {
      log.error(
          "An error occurred while processing STIX bundle (workId={}). bundle={}",
          workId,
          stixBundle,
          e);
      openCTIService.acknowledgeProcessedOfCoverage(
          workId,
          "An error occurred while processing STIX bundle: %s".formatted(e.getMessage()),
          true,
          tenantId);
      throw e;
    }
  }

  /**
   * Callback of the OpenAEV IOC validation connector ({@link
   * io.openaev.opencti.connectors.impl.IocValidationConnector#CALLBACK_PATH}). The OpenCTI work
   * acknowledgements are network calls, so they run around the intake transaction of {@link
   * IocValidationService#receiveRequest} instead of inside it.
   */
  @PostMapping(
      value = "/process-ioc-validation",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @LogExecutionTime
  @Operation(
      summary = "Process an IOC validation request",
      description =
          "Records an OpenCTI IOC validation request. Nothing runs until an operator approves it.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description =
            "IOC validation request recorded, or a malformed event acknowledged without record"),
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
  })
  @AccessControl(actionPerformed = Action.PROCESS, resourceType = ResourceType.STIX_BUNDLE)
  public ResponseEntity<IocValidationImportReport> processIocValidation(
      TxCtx ctx, @RequestBody CTIEvent ctiEvent) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    String workId = ctiEvent.getInternal() == null ? null : ctiEvent.getInternal().getWorkId();
    CTIEvent.Event event = ctiEvent.getEvent();
    String entityId = event == null ? null : event.getEntityId();
    log.debug(
        "IOC validation request received from OpenCTI (workId={}, request={})", workId, entityId);

    // The OpenCTI worker delivers an event again on any answer but 200 or 202: a malformed event is
    // answered 200 (acknowledged in error when it names its work), never rejected by validation.
    boolean noWork = workId == null || workId.isBlank();
    if (noWork
        || event == null
        || event.getStixObjects() == null
        || event.getStixObjects().isBlank()) {
      String reason =
          noWork ? "the event names no OpenCTI work" : "the event carries no STIX objects";
      log.error(
          "OpenAEV ignored a malformed IOC validation event (request={}): {}", entityId, reason);
      if (!noWork) {
        openCTIService.acknowledgeReceivedOfIocValidation(
            workId, "OpenAEV received the IOC validation request", tenantId);
        openCTIService.acknowledgeProcessedOfIocValidation(
            workId, "OpenAEV did not record the IOC validation request: " + reason, true, tenantId);
      }
      return ResponseEntity.status(HttpStatus.OK).build();
    }

    openCTIService.acknowledgeReceivedOfIocValidation(
        workId, "OpenAEV received the IOC validation request", tenantId);
    try {
      IocValidation validation =
          iocValidationService.receiveRequest(ctx, event.getStixObjects(), entityId);
      openCTIService.acknowledgeProcessedOfIocValidation(
          workId,
          IocValidationService.intakeAcknowledgement(validation.getStatus()),
          false,
          tenantId);
      iocValidationService.syncLifecycle(tenantId, validation.getId());
      return ResponseEntity.ok(
          new IocValidationImportReport(validation.getId(), validation.getStatusMessage()));
    } catch (BundleValidationError e) {
      // Same OpenCTI worker contract as the coverage bundle: a malformed request cannot be fixed by
      // a retry, so it is acknowledged in error and answered 200 to stop the redelivery.
      log.error(
          "OpenAEV did not record IOC validation request {} (workId={}): {}",
          entityId,
          workId,
          e.getMessage());
      openCTIService.acknowledgeProcessedOfIocValidation(
          workId,
          "OpenAEV did not record the IOC validation request: %s".formatted(e.getMessage()),
          true,
          tenantId);
      return ResponseEntity.status(HttpStatus.OK).build();
    } catch (RuntimeException e) {
      log.error(
          "An error occurred while recording IOC validation request {} (workId={})",
          entityId,
          workId,
          e);
      // Internal details stay in the server log: OpenCTI only learns that recording failed.
      openCTIService.acknowledgeProcessedOfIocValidation(
          workId,
          "An error occurred while recording the IOC validation request, see the OpenAEV logs",
          true,
          tenantId);
      throw e;
    }
  }

  public record BundleImportReport(String scenarioId, String importSummary) {}

  public record IocValidationImportReport(String iocValidationId, String summary) {}
}
