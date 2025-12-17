package io.openaev.api.chaining;

import io.openaev.aop.RBAC;
import io.openaev.api.chaining.dto.StepsCreateInput;
import io.openaev.api.chaining.dto.WorkflowCreateInput;
import io.openaev.api.chaining.dto.WorkflowOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.chaining.StepService;
import io.openaev.service.chaining.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ChainingApi.CHAINING_API)
@Tag(name = "Chaining API", description = "Operations related to Chaining")
public class ChainingApi extends RestBehavior {

  public static final String CHAINING_API = "/api/chaining";
  private final WorkflowService workflowService;
  private final StepService stepService;

  // private final WorkflowMapper workflowMapper;

  @PostMapping("/workflow")
  @Operation(summary = "Create a new workflow")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Chaining Workflow create successfully"),
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
  })
  @RBAC(actionPerformed = Action.PROCESS, resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  public ResponseEntity<WorkflowOutput> createWorkFlow(@RequestBody WorkflowCreateInput input) {
    try {

      workflowService.creationWorkflow(input.getExerciseId());
      return null; // ResponseEntity.ok(workflowMapper.toOutput(workflowService.creationWorkflow(workflowMapper.toEntity(input))));
    } catch (Exception e) {
      log.error(
          String.format(
              "Unexpected error while creating a new Chaining Workflow: %s", e.getMessage()));
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }

  @PostMapping("/step")
  @Operation(summary = "Create new steps")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Step Workflow create successfully"),
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
  })
  @RBAC(actionPerformed = Action.PROCESS, resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  public ResponseEntity<WorkflowOutput> createStep(@RequestBody StepsCreateInput input) {
    try {

      stepService.createSteps(input.getWorkflowId(), input.steps);
      return null;
    } catch (Exception e) {
      log.error(
          String.format(
              "Unexpected error while creating a new Steps Workflow: %s", e.getMessage()));
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
  }
}
