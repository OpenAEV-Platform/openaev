package io.openaev.api.xtm_composer;

import io.openaev.aop.RBAC;
import io.openaev.api.xtm_composer.dto.XtmComposerInstanceOutput;
import io.openaev.api.xtm_composer.dto.XtmComposerOutput;
import io.openaev.api.xtm_composer.dto.XtmComposerRegisterInput;
import io.openaev.api.xtm_composer.dto.XtmComposerUpdateStatusInput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ConnectorInstanceLog;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceHealthInput;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceLogsInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.connectors.ConnectorOrchestrationService;
import io.openaev.service.connectors.XtmComposerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "XTM COMPOSER API", description = "Operations related to XTM Composer")
public class XtmComposerApi extends RestBehavior {
  public static final String XTMCOMPOSER_URI = "/api/xtm-composer";

  private final XtmComposerService xtmComposerService;
  private final ConnectorOrchestrationService orchestrationService;

  @PostMapping(value = XTMCOMPOSER_URI + "/register")
  @Operation(
      summary = "Register XtmComposer",
      description = "Save registration data into settings from XTM Composer registration")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Successful registration")})
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.CATALOG)
  @Transactional(rollbackFor = Exception.class)
  public XtmComposerOutput register(@Valid @RequestBody XtmComposerRegisterInput input) {
    return this.xtmComposerService.register(input);
  }

  @PutMapping(value = XTMCOMPOSER_URI + "/{xtmComposerId}/refresh-connectivity")
  @Operation(
      summary = "Refresh connectivity with XTM composer",
      description = "Refresh last check connectivity in settings and version in XTM Composer")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.CATALOG)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successful refresh")})
  @Transactional(rollbackFor = Exception.class)
  public XtmComposerOutput refreshConnectivity(@PathVariable @NotBlank final String xtmComposerId) {
    return xtmComposerService.refreshConnectivity(xtmComposerId, Instant.now());
  }

  @GetMapping(value = XTMCOMPOSER_URI + "/reachable")
  @Operation(
      summary = "Check if XtmComposer is reachable and registered in OpenAEV",
      description = "Returns true if XtmComposer is reachable, false otherwise")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.CATALOG)
  public boolean isXtmComposerReachable() {
    try {
      this.xtmComposerService.throwIfXtmComposerNotReachable();
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @GetMapping(value = XTMCOMPOSER_URI + "/{xtmComposerId}/connector-instances")
  @Operation(
      summary = "Get all connector instances managed by xtm-composer",
      description = "Retrieve all connector instances managed by xtm-composer")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.CATALOG)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successful retrieval")})
  public List<XtmComposerInstanceOutput> getAllConnectorInstances(
      @PathVariable @NotBlank final String xtmComposerId) {
    return orchestrationService.findConnectorInstancesManagedByComposer(xtmComposerId);
  }

  @PutMapping(
      value = XTMCOMPOSER_URI + "/{xtmComposerId}/connector-instances/{connectorInstanceId}/status")
  @Operation(
      summary = "Update connector instance status",
      description = "Update the status of a specific connector instance")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.CATALOG)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successful update")})
  public XtmComposerInstanceOutput updateConnectorInstanceStatus(
      @PathVariable @NotBlank final String xtmComposerId,
      @PathVariable @NotBlank final String connectorInstanceId,
      @Valid @RequestBody XtmComposerUpdateStatusInput input) {
    return orchestrationService.updateConnectorInstanceStatus(
        xtmComposerId, connectorInstanceId, input.getCurrentStatus());
  }

  @PostMapping(
      value = XTMCOMPOSER_URI + "/{xtmComposerId}/connector-instances/{connectorInstanceId}/logs")
  @Operation(
      summary = "Received connector instance logs",
      description = "Receive logs from connector instances")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.CATALOG)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successful reception")})
  public ConnectorInstanceLog receiveConnectorInstanceLogs(
      @PathVariable @NotBlank final String xtmComposerId,
      @PathVariable @NotBlank final String connectorInstanceId,
      @Valid @RequestBody ConnectorInstanceLogsInput input) {
    return orchestrationService.pushLogsByConnectorInstance(
        xtmComposerId, connectorInstanceId, input.getLogs());
  }

  @PutMapping(
      value =
          XTMCOMPOSER_URI
              + "/{xtmComposerId}/connector-instances/{connectorInstanceId}/health-check")
  @Operation(
      summary = "Health check of connector instance",
      description = "Receive health check of connector instances from xtm composer")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.CATALOG)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successful health check reception")
      })
  public XtmComposerInstanceOutput receiveConnectorInstanceHealthCheck(
      @PathVariable @NotBlank final String xtmComposerId,
      @PathVariable @NotBlank final String connectorInstanceId,
      @Valid @RequestBody ConnectorInstanceHealthInput input) {
    return orchestrationService.patchConnectorInstanceHealthCheck(
        xtmComposerId, connectorInstanceId, input);
  }
}
