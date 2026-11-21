package io.openaev.rest.exercise;

import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.exposure.ExposureResult;
import io.openaev.service.exposure.ExposureResultService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Exposure result of a simulation run, versioned contract (schema_version). */
@RequiredArgsConstructor
@RestController
public class ExerciseExposureResultApi extends RestBehavior {

  private final ExposureResultService exposureResultService;

  @LogExecutionTime
  @GetMapping({
    "/api/exercises/{exerciseId}/exposure-result",
    TENANT_EXERCISE_URI + "/{exerciseId}/exposure-result"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  @Operation(
      summary = "Exposure result of a simulation",
      description =
          "Per vulnerability or technique tested: the assets tested with their verdict (exposed,"
              + " not_exposed, prevented, detected, not_tested), the conditions checked and a"
              + " confidence; the assets the attack path reached beyond the entry points; the"
              + " security coverage external id when the run comes from a coverage.")
  public ExposureResult exposureResult(TxCtx ctx, @PathVariable @NotBlank final String exerciseId) {
    return exposureResultService.exposureResult(ctx, exerciseId);
  }
}
