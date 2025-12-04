package io.openaev.rest.connector_instance;

import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.ConnectorInstance;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceHealthInput;
import io.openaev.rest.connector_instance.dto.ConnectorInstanceLogsInput;
import io.openaev.rest.connector_instance.dto.CreateConnectorInstanceInput;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.ConnectorInstanceService;
import io.openaev.service.XtmComposerConnectorOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Connector Instance API", description = "Operations related to Connector Instances")
public class ConnectorInstanceApi extends RestBehavior {
  private static final String CONNECTOR_INSTANCE_URI = "/api/connector-instances";

  private final ConnectorInstanceService connectorInstanceService;
  private final XtmComposerConnectorOrchestrationService orchestrationService;

  @PostMapping(value = CONNECTOR_INSTANCE_URI)
  @Operation(
      summary = "Create a new connector instance",
      description = "Create a new connector instance in the platform")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.CATALOG)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully created connector instance")
      })
  public ConnectorInstance createConnectorInstance(
      @Valid @RequestBody CreateConnectorInstanceInput input) {
    // only instance managed by XTM Composer can be created through this API
    return orchestrationService.createConnectorInstance(input);
  }

  @GetMapping(value = CONNECTOR_INSTANCE_URI+"/{connectorInstanceId}")
  @Operation(
          summary = "Retrieve connector Instance by id")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.CATALOG)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successfully retrieved connector instance")})
  public ConnectorInstance getConnectorInstance(
          @PathVariable @NotBlank final String connectorInstanceId) {
    return connectorInstanceService.connectorInstanceById(connectorInstanceId);
  }

  @PostMapping(value = CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}/logs")
  @Operation(
      summary = "Received connector instance logs",
      description = "Receive logs from connector instances")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Successful reception")})
  public void receiveConnectorInstanceLogs(
      @PathVariable @NotBlank final String connectorInstanceId,
      @Valid @RequestBody ConnectorInstanceLogsInput input) {
    connectorInstanceService.pushLogsByConnectorInstance(connectorInstanceId, input.getLogs());
  }

  @PutMapping(value = CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}/health-check")
  @Operation(
      summary = "Health check of connector instance",
      description = "Receive health check of connector instances from xtm composer")
  @RBAC(actionPerformed = Action.WRITE, resourceType = ResourceType.PLATFORM_SETTING)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successful health check reception")
      })
  public void receiveConnectorInstanceHealthCheck(
      @PathVariable @NotBlank final String connectorInstanceId,
      @Valid @RequestBody ConnectorInstanceHealthInput input) {
    connectorInstanceService.patchConnectorInstanceHealthCheck(connectorInstanceId, input);
  }
}
