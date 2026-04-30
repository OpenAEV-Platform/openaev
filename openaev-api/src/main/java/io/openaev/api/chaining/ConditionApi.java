package io.openaev.api.chaining;

import static io.openaev.api.chaining.ChainingApi.CHAINING_URI;
import static io.openaev.api.chaining.ConditionMapper.toOutput;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.chaining.dto.EventInput;
import io.openaev.api.chaining.dto.EventOutput;
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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({ConditionApi.TENANT_CONDITION_URI})
@RequiredArgsConstructor
@Tag(
    name = "Condition API",
    description =
        "CRUD operations for chaining condition trees (frontend event payload maps to backend conditions)")
public class ConditionApi extends RestBehavior {

  public static final String TENANT_CONDITION_URI = TENANT_PREFIX + CHAINING_URI + "/conditions";

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
      resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public EventOutput create(@Valid @RequestBody EventInput input) {
    return toOutput(conditionService.createConditionTree(input));
  }

  // -- READ --
  @Operation(
      summary = "Get a condition tree by root ID",
      description = "Retrieves a condition tree by its root condition ID")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Condition tree found"),
    @ApiResponse(responseCode = "404", description = "Condition tree not found")
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @GetMapping("/{conditionId}")
  public EventOutput findById(@PathVariable String conditionId) {
    return toOutput(conditionService.findConditionRootById(conditionId));
  }

  @Operation(
      summary = "Get condition trees by workflow",
      description = "Lists all root conditions for a given workflow")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Condition trees retrieved")})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @GetMapping(params = "workflow_id")
  public List<EventOutput> findAllByWorkflow(@RequestParam("workflow_id") String workflowId) {
    return conditionService.findConditionRootsByWorkflowId(workflowId).stream()
        .map(ConditionMapper::toOutput)
        .toList();
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
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @PutMapping("/{conditionId}")
  public EventOutput update(
      @PathVariable String conditionId, @Valid @RequestBody EventInput input) {
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
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @DeleteMapping("/{conditionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String conditionId) {
    conditionService.deleteConditionTree(conditionId);
  }
}
