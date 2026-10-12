package io.openaev.api.scenario;

import static io.openaev.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.aop.LogExecutionTime;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.pagination.SearchPaginationInput;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScenarioFacetApi {

  private final ScenarioService scenarioService;

  // -- SEARCH --

  @LogExecutionTime
  @Operation(summary = "Scenario authors with their counts under the filters, for the author facet")
  @PostMapping({SCENARIO_URI + "/author-counts", TENANT_SCENARIO_URI + "/author-counts"})
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<ScenarioAuthorCountOutput> authorCounts(
      TxCtx ctx, @RequestBody @Valid final SearchPaginationInput input) {
    return scenarioService.getAuthorCounts(input);
  }
}
