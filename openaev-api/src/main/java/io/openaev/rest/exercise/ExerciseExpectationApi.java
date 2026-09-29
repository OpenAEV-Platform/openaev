package io.openaev.rest.exercise;

import static io.openaev.api.expectations.mapper.InjectExpectationMapper.toOutputs;
import static io.openaev.rest.exercise.ExerciseApi.TENANT_EXERCISE_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.api.expectations.dto.InjectExpectationOutput;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.service.ExerciseExpectationService;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class ExerciseExpectationApi extends RestBehavior {

  private final ExerciseExpectationService exerciseExpectationService;

  @LogExecutionTime
  @Transactional
  @GetMapping({
    "/api/exercises/{exerciseId}/expectations",
    TENANT_EXERCISE_URI + "/{exerciseId}/expectations"
  })
  @AccessControl(
      resourceId = "#exerciseId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public List<InjectExpectationOutput> exerciseInjectExpectations(
      TxCtx ctx, @PathVariable @NotBlank final String exerciseId) {
    return toOutputs(this.exerciseExpectationService.injectExpectations(exerciseId));
  }
}
