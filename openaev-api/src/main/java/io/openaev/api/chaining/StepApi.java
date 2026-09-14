package io.openaev.api.chaining;

import static io.openaev.api.chaining.StepMapper.toOutput;
import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.api.chaining.dto.StepInput;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.database.model.Workflow;
import io.openaev.database.model.WorkflowStatus;
import io.openaev.rest.exception.ChainingException;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping(StepApi.TENANT_STEP_URI)
@RequiredArgsConstructor
@Tag(name = "Step API", description = "CRUD operations for workflow step templates")
public class StepApi {

  public static final String TENANT_STEP_URI = TENANT_PREFIX + "/steps";

  private final StepService stepService;
  private final WorkflowService workflowService;

  // -- CREATE --
  @Operation(summary = "Create a step template")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "Step template created"),
    @ApiResponse(responseCode = "400", description = "Invalid input")
  })
  @AccessControl(
      actionPerformed = Action.CREATE,
      resourceType = ResourceType.SIMULATION_OR_SCENARIO,
      isEnterpriseEdition = true)
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional(rollbackFor = Exception.class)
  public StepOutput createStep(TxCtx ctx, @Valid @RequestBody StepInput input)
      throws ChainingException {
    StepsCreateInput.StepInput createInput =
        StepsCreateInput.StepInput.builder()
            .stepAction(input.getStepAction())
            .conditions(input.getConditions())
            .conditionIds(input.getConditionIds())
            .dataStep(input.getDataStep())
            .build();
    Workflow workflow =
        workflowService.getWorkflowByIdAndStatus(input.getWorkflowId(), WorkflowStatus.TEMPLATE);

    return toOutput(stepService.createStepTemplate(workflow, createInput));
  }

  // -- READ --

  @Operation(summary = "Get a step template by ID")
  @Transactional
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Step template found"),
    @ApiResponse(responseCode = "404", description = "Step template not found")
  })
  @AccessControl(
      resourceId = "#stepId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.STEP,
      isEnterpriseEdition = true)
  @GetMapping("/{stepId}")
  public StepOutput findById(TxCtx ctx, @PathVariable String stepId) {
    return toOutput(stepService.findStepTemplateById(stepId));
  }

  @Operation(summary = "List step templates by workflow")
  @Transactional
  @ApiResponses({@ApiResponse(responseCode = "200", description = "Step templates retrieved")})
  @AccessControl(
      resourceId = "#workflowId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.WORKFLOW,
      isEnterpriseEdition = true)
  @GetMapping(params = "workflow_id")
  public List<StepOutput> findByWorkflowId(
      TxCtx ctx, @RequestParam("workflow_id") String workflowId) {
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
  @AccessControl(
      resourceId = "#stepId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.STEP,
      isEnterpriseEdition = true)
  @PutMapping("/{stepId}")
  @Transactional(rollbackFor = Exception.class)
  public StepOutput updateStep(
      TxCtx ctx, @PathVariable String stepId, @Valid @RequestBody StepInput input)
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
      resourceId = "#stepId",
      actionPerformed = Action.DELETE,
      resourceType = ResourceType.STEP,
      isEnterpriseEdition = true)
  @DeleteMapping("/{stepId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void deleteStep(TxCtx ctx, @PathVariable String stepId) {
    stepService.deleteStepTemplate(stepId);
  }
}
