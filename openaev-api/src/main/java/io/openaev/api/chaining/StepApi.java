package io.openaev.api.chaining;

import static io.openaev.api.chaining.StepMapper.toOutput;

import io.openaev.aop.AccessControl;
import io.openaev.api.chaining.dto.StepInput;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.ChainingException;
import io.openaev.service.chaining.StepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping(StepApi.STEP_API)
@RequiredArgsConstructor
@Tag(name = "Step API", description = "CRUD operations for workflow step templates")
public class StepApi {

  public static final String STEP_API = "/api/chaining/steps";

  private final StepService stepService;

  // -- CREATE --
  @Operation(summary = "Create a step template")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Step template created"),
    @ApiResponse(responseCode = "400", description = "Invalid input")
  })
  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public StepOutput createStep(@Valid @RequestBody StepInput input) throws ChainingException {
    StepsCreateInput.StepInput createInput =
        StepsCreateInput.StepInput.builder()
            .stepAction(input.getStepAction())
            .conditions(input.getConditions())
            .conditionIds(input.getConditionIds())
            .dataStep(input.getDataStep())
            .build();
    return toOutput(stepService.createStepTemplate(input.getWorkflowId(), createInput));
  }

  // -- READ --

  @Operation(summary = "Get a step template by ID")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Step template found"),
    @ApiResponse(responseCode = "404", description = "Step template not found")
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @GetMapping("/{stepId}")
  public StepOutput findById(@PathVariable String stepId) {
    return toOutput(stepService.findStepTemplateById(stepId));
  }

  @Operation(summary = "List step templates by workflow")
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Step templates retrieved")})
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @GetMapping(params = "workflow_id")
  public List<StepOutput> findByWorkflowId(@RequestParam("workflow_id") String workflowId) {
    return stepService.findAllStepTemplateByWorkflow(workflowId).stream()
        .map(StepMapper::toOutput)
        .toList();
  }

  // -- UPDATE --

  @Operation(summary = "Update a step template")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Step template updated"),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "404", description = "Step template not found")
  })
  @AccessControl(actionPerformed = Action.WRITE, resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @PutMapping("/{stepId}")
  public StepOutput updateStep(@PathVariable String stepId, @Valid @RequestBody StepInput input)
      throws ChainingException {
    return toOutput(stepService.updateStepTemplate(stepId, input));
  }

  // -- DELETE --

  @Operation(summary = "Delete a step template")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Step template deleted"),
    @ApiResponse(responseCode = "404", description = "Step template not found")
  })
  @AccessControl(
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @DeleteMapping("/{stepId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteStep(@PathVariable String stepId) {
    stepService.deleteStepTemplate(stepId);
  }
}
