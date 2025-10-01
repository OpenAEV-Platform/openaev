package io.openaev.rest.inject_test_status;

import static io.openaev.database.specification.InjectSpecification.testable;
import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;

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
public class ScenarioInjectTestApi extends RestBehavior {

  private final InjectTestStatusService injectTestStatusService;
  private final InjectService injectService;

  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects/test/search")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.READ,
      resourceType = ResourceType.SCENARIO)
  public Page<InjectTestStatusOutput> findAllScenarioInjectTests(
      @PathVariable @NotBlank String scenarioId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return injectTestStatusService.findAllInjectTestsByScenarioId(
        scenarioId, searchPaginationInput);
  }

  @Transactional(rollbackFor = Exception.class)
  @GetMapping(SCENARIO_URI + "/injects/test/{testId}")
  @RBAC(actionPerformed = Action.READ, resourceType = ResourceType.SCENARIO)
  public InjectTestStatusOutput findInjectTestStatus(@PathVariable @NotBlank String testId) {
    return injectTestStatusService.findInjectTestStatusById(testId);
  }

  @Transactional(rollbackFor = Exception.class)
  @GetMapping(SCENARIO_URI + "/{scenarioId}/injects/{injectId}/test")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.LAUNCH,
      resourceType = ResourceType.SCENARIO)
  public InjectTestStatusOutput testInject(
      @PathVariable @NotBlank final String scenarioId, @PathVariable @NotBlank String injectId) {
    return injectTestStatusService.testInject(injectId);
  }

  @Transactional(rollbackFor = Exception.class)
  @DeleteMapping(SCENARIO_URI + "/{scenarioId}/injects/test/{testId}")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  public void deleteInjectTest(
      @PathVariable @NotBlank final String scenarioId, @PathVariable String testId) {
    injectTestStatusService.deleteInjectTest(testId);
  }

  @Operation(
      description = "Bulk tests of injects",
      tags = {"Injects", "Tests"})
  @Transactional(rollbackFor = Exception.class)
  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects/test")
  @RBAC(
      resourceId = "#scenarioId",
      actionPerformed = Action.WRITE,
      resourceType = ResourceType.SCENARIO)
  @LogExecutionTime
  public List<InjectTestStatusOutput> bulkTestInject(
      @PathVariable @NotBlank final String scenarioId,
      @RequestBody @Valid final InjectBulkProcessingInput input) {

    // Control and format inputs
    if (!scenarioId.equals(input.getSimulationOrScenarioId())) {
      throw new BadRequestException("Provided scenario ID does not match the input scenario ID");
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
