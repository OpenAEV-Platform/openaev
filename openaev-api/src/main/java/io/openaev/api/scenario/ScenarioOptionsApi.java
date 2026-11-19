package io.openaev.api.scenario;

import static io.openaev.rest.scenario.ScenarioApi.TENANT_SCENARIO_URI;

import io.openaev.aop.AccessControl;
import io.openaev.context.TxCtx;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.service.scenario.ScenarioService;
import io.openaev.utils.FilterUtilsJpa;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ScenarioOptionsApi {

  private final ScenarioService scenarioService;

  // -- OPTIONS --

  @Operation(summary = "Authors of the scenarios the user can read, as filter options")
  @GetMapping(TENANT_SCENARIO_URI + "/authors/options")
  @Transactional(readOnly = true)
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SCENARIO)
  public List<FilterUtilsJpa.Option> authorOptions(TxCtx ctx) {
    return scenarioService.authorOptions(ctx);
  }
}
