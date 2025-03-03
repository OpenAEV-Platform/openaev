package io.openbas.rest.inject;

import static io.openbas.database.specification.InjectSpecification.fromScenario;
import static io.openbas.rest.scenario.ScenarioApi.SCENARIO_URI;
import static io.openbas.utils.pagination.PaginationUtils.buildPaginationCriteriaBuilder;

import io.openbas.database.model.Base;
import io.openbas.database.model.Inject;
import io.openbas.rest.helper.RestBehavior;
import io.openbas.rest.inject.output.InjectOutput;
import io.openbas.rest.inject.output.InjectTestStatusOutput;
import io.openbas.service.InjectSearchService;
import io.openbas.service.InjectTestStatusService;
import io.openbas.utils.pagination.SearchPaginationInput;
import jakarta.persistence.criteria.Join;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ScenarioInjectApi extends RestBehavior {

  private final InjectSearchService injectSearchService;
  private final InjectTestStatusService injectTestStatusService;

  @GetMapping(SCENARIO_URI + "/{scenarioId}/injects/simple")
  @PreAuthorize("isScenarioObserver(#scenarioId)")
  @Transactional(readOnly = true)
  public Iterable<InjectOutput> scenarioInjectsSimple(
      @PathVariable @NotBlank final String scenarioId) {
    return injectSearchService.injects(fromScenario(scenarioId));
  }

  @PostMapping(SCENARIO_URI + "/{scenarioId}/injects/simple")
  @PreAuthorize("isScenarioObserver(#scenarioId)")
  @Transactional(readOnly = true)
  public Iterable<InjectOutput> scenarioInjectsSimple(
      @PathVariable @NotBlank final String scenarioId,
      @RequestBody @Valid final SearchPaginationInput searchPaginationInput) {
    Map<String, Join<Base, Base>> joinMap = new HashMap<>();
    return buildPaginationCriteriaBuilder(
        (Specification<Inject> specification,
            Specification<Inject> specificationCount,
            Pageable pageable) ->
            this.injectSearchService.injects(
                fromScenario(scenarioId).and(specification),
                fromScenario(scenarioId).and(specificationCount),
                pageable,
                joinMap),
        searchPaginationInput,
        Inject.class,
        joinMap);
  }

  @PostMapping("/api/scenario/{scenarioId}/injects/test")
  public Page<InjectTestStatusOutput> findAllScenarioInjectTests(
      @PathVariable @NotBlank String scenarioId,
      @RequestBody @Valid SearchPaginationInput searchPaginationInput) {
    return injectTestStatusService.findAllInjectTestsByScenarioId(
        scenarioId, searchPaginationInput);
  }
}
