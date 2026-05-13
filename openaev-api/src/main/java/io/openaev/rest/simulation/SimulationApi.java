package io.openaev.rest.simulation;

import static io.openaev.config.TenantUriUtils.TENANT_PREFIX;

import io.openaev.aop.AccessControl;
import io.openaev.database.model.Action;
import io.openaev.database.model.ResourceType;
import io.openaev.rest.helper.RestBehavior;
import io.openaev.utils.FilterUtilsJpa.Option;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequestMapping({SimulationApi.SIMULATION_URI, SimulationApi.TENANT_SIMULATION_URI})
@RestController
@RequiredArgsConstructor
public class SimulationApi extends RestBehavior {

  public static final String SIMULATION_URI = "/api/simulations";
  public static final String TENANT_SIMULATION_URI = TENANT_PREFIX + "/simulations";

  private final SimulationService simulationService;

  // -- OPTION --

  @GetMapping("/options")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<Option> optionsByName(@RequestParam(required = false) final String searchText) {
    return this.simulationService.findAllAsOptions(searchText);
  }

  @PostMapping("/options")
  @AccessControl(actionPerformed = Action.SEARCH, resourceType = ResourceType.SIMULATION)
  public List<Option> optionsById(@RequestBody final List<String> ids) {
    return this.simulationService.findAllByIdsAsOptions(ids);
  }
}
