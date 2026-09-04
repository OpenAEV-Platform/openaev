package io.openaev.api.chaining;

import static io.openaev.api.chaining.ConditionMapper.toOutput;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.api.chaining.dto.EventOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.chaining.ConditionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ConditionApi.TENANT_CONDITION_URI})
@RequiredArgsConstructor
@Tag(
    name = "Condition API",
    description =
        "CRUD operations for chaining condition trees (frontend event payload maps to backend conditions)")
public class ConditionApi extends RestBehavior {

  public static final String TENANT_CONDITION_URI = TENANT_PREFIX + "/conditions";

  private final ConditionService conditionService;

  // -- CREATE --

  @Operation(
      summary = "Create a condition tree",
      description =
          "Creates a root condition (AND/OR) and its child conditions from the frontend event payload")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Condition tree created successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid input")
  })
  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.SIMULATION_OR_SCENARIO,
      isEnterpriseEdition = true)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public EventOutput create(TxCtx ctx, @Valid @RequestBody EventInput input) {
    return toOutput(conditionService.createConditionTree(input));
  }

  // -- READ --
  @Operation(
      summary = "Get a condition tree by root ID",
      description = "Retrieves a condition tree by its root condition ID")
  @Transactional
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Condition tree found"),
    @ApiResponse(responseCode = "404", description = "Condition tree not found")
  })
  @AccessControl(
      resourceId = "#conditionId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.CONDITION,
      isEnterpriseEdition = true)
  @GetMapping("/{conditionId}")
  public EventOutput findById(TxCtx ctx, @PathVariable String conditionId) {
    return toOutput(conditionService.findConditionRootById(conditionId));
  }

  @Operation(
      summary = "Get condition trees by workflow",
      description = "Lists all root conditions for a given workflow")
  @Transactional
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Condition trees retrieved")})
  @AccessControl(
      resourceId = "#workflowId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  @GetMapping(params = "workflow_id")
  public List<EventOutput> findAllByWorkflow(
      TxCtx ctx, @RequestParam("workflow_id") String workflowId) {
    return conditionService.findEventsByWorkflowId(workflowId);
  }

  // -- UPDATE --

  @Operation(
      summary = "Update a condition tree",
      description = "Replaces root metadata and child conditions")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Condition tree updated"),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "404", description = "Condition tree not found")
  })
  @AccessControl(
      resourceId = "#conditionId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.CONDITION,
      isEnterpriseEdition = true)
  @PutMapping("/{conditionId}")
  @Transactional
  public EventOutput update(
      TxCtx ctx, @PathVariable String conditionId, @Valid @RequestBody EventInput input) {
    return toOutput(conditionService.updateConditionTree(conditionId, input));
  }

  // -- DELETE --

  @Operation(
      summary = "Delete a condition tree",
      description = "Deletes a root condition and all child conditions")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Condition tree deleted"),
    @ApiResponse(responseCode = "404", description = "Condition tree not found")
  })
  @AccessControl(
      resourceId = "#conditionId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.CONDITION,
      isEnterpriseEdition = true)
  @DeleteMapping("/{conditionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(TxCtx ctx, @PathVariable String conditionId) {
    conditionService.deleteConditionTree(conditionId);
  }
}
