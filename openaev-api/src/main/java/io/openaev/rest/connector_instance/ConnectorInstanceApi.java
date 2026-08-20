package io.openaev.rest.connector_instance;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.config.RequireTenantSelector;
import io.openaev.config.TenantWriteScopeResolver;
import io.openaev.context.TxCtx;
import io.openaev.database.model.*;
import io.openaev.rest.connector_instance.dto.*;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.connector_instances.ConnectorInstanceLogService;
import io.openaev.service.connector_instances.ConnectorInstanceService;
import io.openaev.service.connectors.ConnectorOrchestrationService;
import io.openaev.service.exception.ConnectorStatusException;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "Connector Instance API", description = "Operations related to Connector Instances")
public class ConnectorInstanceApi extends RestBehavior {
  public static final String CONNECTOR_INSTANCE_URI = "/api/connector-instances";
  private static final String TENANT_CONNECTOR_INSTANCE_URI =
      TENANT_PREFIX + "/connector-instances";

  private final ConnectorInstanceService connectorInstanceService;
  private final ConnectorInstanceLogService connectorInstanceLogService;
  private final ConnectorOrchestrationService orchestrationService;
  private final TenantWriteScopeResolver writeScopeResolver;

  @PostMapping(value = {CONNECTOR_INSTANCE_URI, TENANT_CONNECTOR_INSTANCE_URI})
  @Transactional
  @Operation(
      summary = "Create a new connector instance",
      description = "Create a new connector instance in the platform")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.CATALOG)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully created connector instance")
      })
  public ConnectorInstancePersisted createConnectorInstance(
      @RequireTenantSelector TxCtx ctx, @Valid @RequestBody CreateConnectorInstanceInput input) {
    String tenantId = writeScopeResolver.tenantForWrite(ctx, null);
    // --- /!\ --- SECURITY START : Encrypt sensitive values before any LOGGING or processing
    ConnectorOrchestrationService.CatalogConnectorWithConfigMap catalogConnectorWithConfigMap =
        this.orchestrationService.getCatalogConnectorWithConfigurationsMap(
            input.getCatalogConnectorId());
    CreateConnectorInstanceInput safeInput =
        this.connectorInstanceService.sanitizeConnectorInstanceInput(
            catalogConnectorWithConfigMap, input);
    // --- /!\ --- SECURITY END

    // only instance managed by XTM Composer can be created through this API
    return orchestrationService.createConnectorInstance(
        catalogConnectorWithConfigMap, safeInput, tenantId);
  }

  @GetMapping(
      value = {
        CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}",
        TENANT_CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}"
      })
  @Operation(summary = "Retrieve connector Instance by id")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.CATALOG)
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Successfully retrieved connector instance")
      })
  public ConnectorInstanceOutput getConnectorInstance(
      @RequireTenantSelector TxCtx ctx, @PathVariable @NotBlank final String connectorInstanceId) {
    return connectorInstanceService.connectorInstanceOutputById(connectorInstanceId);
  }

  @GetMapping(
      value = {
        CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}/configurations",
        TENANT_CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}/configurations"
      })
  @Operation(summary = "Retrieve connector Instance configuratiosn by instance id")
  @Transactional
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.CATALOG)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = "application/json",
              array =
                  @ArraySchema(
                      schema = @Schema(implementation = ConnectorInstanceConfiguration.class))))
  public Set<ConnectorInstanceConfiguration> getConnectorInstanceConfiguration(
      @RequireTenantSelector TxCtx ctx, @PathVariable @NotBlank final String connectorInstanceId) {
    return connectorInstanceService.getConnectorInstanceConfigurationsNoSecrets(
        connectorInstanceId);
  }

  @PutMapping(
      value = {
        CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}/configurations",
        TENANT_CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}/configurations"
      })
  @Transactional
  @Operation(summary = "Update connector instance configuration")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.CATALOG)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = "application/json",
              array =
                  @ArraySchema(
                      schema = @Schema(implementation = ConnectorInstanceConfiguration.class))))
  public List<ConnectorInstanceConfiguration> updateConnectorInstanceConfigurations(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable @NotBlank final String connectorInstanceId,
      @Valid @RequestBody CreateConnectorInstanceInput input) {
    // --- /!\ --- SECURITY START : Encrypt sensitive values before any LOGGING or processing
    ConnectorOrchestrationService.CatalogConnectorWithConfigMap catalogConnectorWithConfigMap =
        this.orchestrationService.getCatalogConnectorWithConfigurationsMap(
            input.getCatalogConnectorId());
    CreateConnectorInstanceInput safeInput =
        this.connectorInstanceService.sanitizeConnectorInstanceInput(
            catalogConnectorWithConfigMap, input);
    // --- /!\ --- SECURITY END
    return orchestrationService.updateConnectorInstanceConfiguration(
        catalogConnectorWithConfigMap, connectorInstanceId, safeInput);
  }

  @PostMapping(
      value = {
        CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}/logs/search",
        TENANT_CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}/logs/search"
      })
  @Operation(summary = "Search connector instance logs")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.CATALOG)
  @ApiResponse(
      responseCode = "200",
      content =
          @Content(
              mediaType = "application/json",
              schema = @Schema(implementation = PageConnectorInstanceLog.class)))
  public Page<ConnectorInstanceLog> searchConnectorInstanceLogs(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable @NotBlank final String connectorInstanceId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    return connectorInstanceLogService.searchLogsByConnectorInstanceId(
        connectorInstanceId, searchPaginationInput);
  }

  @PutMapping(
      value = {
        CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}/requested-status",
        TENANT_CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}/requested-status"
      })
  @Transactional
  @Operation(
      summary = "Update requested status",
      description = "Update requested status of connector instance")
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.CATALOG)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully updated requested status")
      })
  public ConnectorInstancePersisted updateRequestedStatus(
      @RequireTenantSelector TxCtx ctx,
      @PathVariable @NotBlank final String connectorInstanceId,
      @Valid @RequestBody UpdateConnectorInstanceRequestedStatus input) {
    return orchestrationService.updateRequestedStatus(
        connectorInstanceId, input.getRequestedStatus());
  }

  @DeleteMapping(
      value = {
        CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}",
        TENANT_CONNECTOR_INSTANCE_URI + "/{connectorInstanceId}"
      })
  @Operation(summary = "Delete connector instance")
  @Transactional
  @AccessControl(actionPerformed = Action.DELETE, resourceType = ResourceType.CATALOG)
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Successfully deleted connector instance")
      })
  public void deleteConnectorInstance(
      @RequireTenantSelector TxCtx ctx, @PathVariable @NotBlank final String connectorInstanceId)
      throws ConnectorStatusException {
    // Enforce a single-tenant write scope (400 on ambiguous selector) before deleteById tears down
    // the executor/collector/injector row it owns: those deletes rely on this method's ambient
    // scope, not an explicit tenant predicate (see ConnectorInstanceService#deleteById).
    writeScopeResolver.tenantForWrite(ctx, null);
    connectorInstanceService.deleteById(connectorInstanceId);
  }
}
