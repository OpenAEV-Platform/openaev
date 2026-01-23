package io.openaev.rest.inject_test_status;

import static io.openaev.database.specification.InjectSpecification.testable;
import static io.openaev.rest.exercise.ExerciseApi.EXERCISE_URI;

import io.openaev.aop.LogExecutionTime;
import io.openaev.aop.RBAC;
import io.openaev.database.model.Action;
import io.openaev.database.model.Grant;
import io.openaev.database.model.Inject;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.rest.inject.form.InjectBulkProcessingInput;
import io.openaev.rest.inject.output.InjectTestStatusOutput;
import io.openaev.rest.inject.service.InjectService;
import io.openaev.service.InjectTestStatusService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class SimulationInjectTestApi extends RestBehavior {

  private final InjectTestStatusService injectTestStatusService;
  private final InjectService injectService;

  /**
   * @deprecated since 1.16.0, forRemoval = true
   * @see #findExercisePageInjectTests
   */
  @PostMapping("/api/exercise/{simulationId}/injects/test")
  @RBAC(
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION,
      resourceId = "#simulationId")
  public Page<InjectTestStatusOutput> findAllExerciseInjectTests(
      @PathVariable @NotBlank String simulationId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return injectTestStatusService.findAllInjectTestsByExerciseId(
        simulationId, searchPaginationInput);
  }

  @PostMapping(EXERCISE_URI + "/{simulationId}/injects/test/search")
  @RBAC(
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION,
      resourceId = "#simulationId")
  public Page<InjectTestStatusOutput> findExercisePageInjectTests(
      @PathVariable @NotBlank String simulationId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return injectTestStatusService.findAllInjectTestsByExerciseId(
        simulationId, searchPaginationInput);
  }

  @Transactional(rollbackFor = Exception.class)
  @GetMapping(EXERCISE_URI + "/{simulationId}/injects/{injectId}/test")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SIMULATION)
  public InjectTestStatusOutput testInject(
      @PathVariable @NotBlank String simulationId, @PathVariable @NotBlank String injectId)
      throws Exception {
    return injectTestStatusService.testInject(injectId);
  }

  @Transactional(rollbackFor = Exception.class)
  @GetMapping(EXERCISE_URI + "/injects/test/{testId}")
  @RBAC(
      actionPerformed = Action.SEARCH,
      resourceType =
          ResourceType.SIMULATION) // fixme : should use action search on resourceType simulation
  public InjectTestStatusOutput findInjectTestStatus(@PathVariable @NotBlank String testId) {
    return injectTestStatusService.findInjectTestStatusById(testId);
  }

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(EXERCISE_URI + "/{simulationId}/injects/test/{testId}")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  public void deleteInjectTest(
      @PathVariable @NotBlank String simulationId, @PathVariable String testId) {
    injectTestStatusService.deleteInjectTest(testId);
  }

  @Operation(
      description = "Bulk tests of injects",
      tags = {"Injects", "Tests"})
  @Transactional(rollbackFor = Exception.class)
  @PostMapping(EXERCISE_URI + "/{simulationId}/injects/test")
  @RBAC(
      resourceId = "#simulationId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SIMULATION)
  @LogExecutionTime
  public List<InjectTestStatusOutput> bulkTestInject(
      @PathVariable @NotBlank String simulationId,
      @RequestBody @Valid final InjectBulkProcessingInput input) {

    // Control and format inputs
    if (!simulationId.equals(input.getSimulationOrScenarioId())) {
      throw new BadRequestException(
          "Provided simulation ID does not match the input simulation ID");
    }
    if (CollectionUtils.isEmpty(input.getInjectIDsToProcess())
        && input.getSearchPaginationInput() == null) {
      throw new BadRequestException(
          "Either search_pagination_input or inject_ids_to_process must be provided");
    }

    // Specification building
    Specification<Inject> filterSpecifications =
        this.injectService.getInjectSpecification(input, Grant.GRANT_TYPE.PLANNER).and(testable());

    // Services calls
    // Bulk test
    return injectTestStatusService.bulkTestInjects(filterSpecifications);
  }
}
