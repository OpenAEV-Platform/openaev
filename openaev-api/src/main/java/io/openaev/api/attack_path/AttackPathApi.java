package io.openaev.api.attack_path;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.settings.PreviewFeature;
import io.openaev.service.PreviewFeatureService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@Tag(
    name = "Attack Path",
    description = "Attack path visualization for simulation results")
public class AttackPathApi extends RestBehavior {

  private static final String ATTACK_PATH_URI = "/api/exercises/{exerciseId}/attack-path";
  private static final String TENANT_ATTACK_PATH_URI =
      TENANT_PREFIX + "/exercises/{exerciseId}/attack-path";

  private final AttackPathService attackPathService;
  private final PreviewFeatureService previewFeatureService;

  // -- READ --

  @LogExecutionTime
  @GetMapping({ATTACK_PATH_URI, TENANT_ATTACK_PATH_URI})
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Get attack path graph",
      description = "Returns the attack path graph data for a simulation exercise")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Attack path graph data"),
    @ApiResponse(responseCode = "404", description = "Exercise not found or feature disabled")
  })
  public AttackPathOutput getAttackPath(
      @PathVariable @NotBlank final String exerciseId) {
    if (!previewFeatureService.isFeatureEnabled(PreviewFeature.CHAINING_ATTACK_PATH)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    return attackPathService.buildAttackPath(exerciseId);
  }
}
