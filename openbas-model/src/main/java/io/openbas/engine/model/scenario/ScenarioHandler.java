package io.openbas.engine.model.scenario;

import static io.openbas.engine.EsUtils.buildRestrictions;
import static org.springframework.util.CollectionUtils.isEmpty;

import io.openbas.database.model.Scenario;
import io.openbas.database.raw.RawScenario;
import io.openbas.database.repository.ScenarioRepository;
import io.openbas.engine.Handler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScenarioHandler implements Handler<EsScenario> {

  private ScenarioRepository scenarioRepository;

  @Autowired
  public void setScenarioRepository(ScenarioRepository scenarioRepository) {
    this.scenarioRepository = scenarioRepository;
  }

  @Override
  public List<EsScenario> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawScenario> forIndexing = scenarioRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            scenario -> {
              EsScenario esScenario = new EsScenario();
              // Base
              esScenario.setBase_id(scenario.getScenario_id());
              esScenario.setStatus(
                  scenario.getScenario_recurrence() != null
                      ? Scenario.RECURRENCE_STATUS.SCHEDULED.name()
                      : Scenario.RECURRENCE_STATUS.NOT_PLANNED.name());
              esScenario.setBase_created_at(scenario.getScenario_created_at());
              esScenario.setBase_updated_at(scenario.getScenario_injects_updated_at());

              esScenario.setBase_representative(scenario.getScenario_name());
              esScenario.setBase_restrictions(buildRestrictions(scenario.getScenario_id()));
              // Specific
              esScenario.setBase_platforms_side_denormalized(scenario.getScenario_platforms());
              // Dependencies
              List<String> dependencies = new ArrayList<>();
              if (!isEmpty(scenario.getScenario_tags())) {
                dependencies.addAll(scenario.getScenario_tags());
                esScenario.setBase_tags_side(scenario.getScenario_tags());
              }
              if (!isEmpty(scenario.getScenario_assets())) {
                dependencies.addAll(scenario.getScenario_assets());
                esScenario.setBase_assets_side(scenario.getScenario_assets());
              }
              if (!isEmpty(scenario.getScenario_asset_groups())) {
                dependencies.addAll(scenario.getScenario_asset_groups());
                esScenario.setBase_asset_groups_side(scenario.getScenario_asset_groups());
              }
              if (!isEmpty(scenario.getScenario_teams())) {
                dependencies.addAll(scenario.getScenario_teams());
                esScenario.setBase_teams_side(scenario.getScenario_teams());
              }
              esScenario.setBase_dependencies(dependencies);
              return esScenario;
            })
        .toList();
  }
}
