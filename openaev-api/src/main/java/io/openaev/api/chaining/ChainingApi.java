package io.openaev.api.chaining;

import io.openaev.aop.AccessControl;
import io.openaev.api.chaining.dto.ChainingOutput;
import io.openaev.api.chaining.dto.EventOutput;
import io.openaev.api.chaining.dto.StepOutput;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.chaining.ConditionService;
import io.openaev.service.chaining.StepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ChainingApi.CHAINING_API)
@RequiredArgsConstructor
@Tag(name = "Chaining API", description = "Aggregated operations for chaining")
public class ChainingApi {

  public static final String CHAINING_API = "/api/chaining";

  private final ConditionService conditionService;
  private final StepService stepService;

  // -- READ --

  @Operation(summary = "Get all chaining data", description = "Returns all conditions and steps")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Chaining data retrieved successfully")
  })
  @AccessControl(actionPerformed = Action.READ, resourceType = ResourceType.SIMULATION_OR_SCENARIO)
  @GetMapping
  public ChainingOutput findAll() {
    List<EventOutput> conditions =
        conditionService.findAll().stream().map(ConditionMapper::toOutput).toList();

    List<StepOutput> steps =
        stepService.findAllStepTemplates().stream()
            .map(io.openaev.api.chaining.dto.StepMapper::toOutput)
            .toList();

    return new ChainingOutput(conditions, steps);
  }
}
