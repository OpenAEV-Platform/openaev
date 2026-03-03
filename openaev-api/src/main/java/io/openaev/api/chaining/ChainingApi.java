package io.openaev.api.chaining;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.api.chaining.dto.ChainingConfigurationOutput;
import io.openaev.database.model.*;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.scenario.ScenarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ChainingApi.CHAINING_API)
@Tag(name = "Chaining API", description = "Operations related to Chaining")
public class ChainingApi extends RestBehavior {

  public static final String CHAINING_API = "/api/chaining";

  private final ScenarioService scenarioService;
  private final ChainingConfigurationMapper chainingConfigurationMapper;

  @Operation(
      summary = "Fetch chaining configuration for a scenario",
      description =
          "Fetch the chaining configuration for a given scenario, including time-out, rate-limit, safe-mode and scope rules.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Chaining configuration retrieved successfully"),
    @ApiResponse(
        responseCode = "404",
        description = "Chaining configuration not found for the specified scenario"),
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
  })
  @GetMapping(SCENARIO_URI + "/{scenarioId}/chaining-configuration")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public ChainingConfigurationOutput fetchChainingConfiguration(
      @PathVariable @NotBlank final String scenarioId) {
    return chainingConfigurationMapper.toOutput(
        scenarioService.fetchChainingConfiguration(scenarioId));
  }

  @Operation(
      summary = "Create chaining configuration for a scenario",
      description = "Create a chaining configuration for a given scenario.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Chaining configuration created successfully"),
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
  })
  @PostMapping(SCENARIO_URI + "/{scenarioId}/chaining-configuration")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public ChainingConfigurationOutput createChainingConfiguration(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ChainingConfigurationInput input) {
    return scenarioService.createChainingConfiguration(scenarioId, input);
  }

  @Operation(
      summary = "Update chaining configuration for a scenario",
      description = "Update chaining configuration for a given scenario.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Chaining configuration updated successfully"),
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
  })
  @PutMapping(SCENARIO_URI + "/{scenarioId}/chaining-configuration")
  @AccessControl(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public ChainingConfigurationOutput updatedChainingConfiguration(
      @PathVariable @NotBlank final String scenarioId,
      @Valid @RequestBody final ChainingConfigurationInput input) {
    return scenarioService.updateChainingConfiguration(scenarioId, input);
  }
}
