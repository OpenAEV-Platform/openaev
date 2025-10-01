package io.openaev.engine.model.scenario;

import static io.openaev.engine.EsUtils.buildRestrictions;
import static org.springframework.util.CollectionUtils.isEmpty;

import io.openaev.database.model.Scenario;
import io.openaev.database.raw.RawScenario;
import io.openaev.database.repository.ScenarioRepository;
import io.openaev.engine.Handler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
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
              esScenario.setName(scenario.getScenario_name());
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
              // Dependencies (see base_dependencies in EsBase)
              if (!isEmpty(scenario.getScenario_tags())) {
                esScenario.setBase_tags_side(scenario.getScenario_tags());
              } else {
                esScenario.setBase_tags_side(Set.of());
              }
              if (!isEmpty(scenario.getScenario_assets())) {
                esScenario.setBase_assets_side(scenario.getScenario_assets());
              } else {
                esScenario.setBase_assets_side(Set.of());
              }
              if (!isEmpty(scenario.getScenario_asset_groups())) {
                esScenario.setBase_asset_groups_side(scenario.getScenario_asset_groups());
              } else {
                esScenario.setBase_asset_groups_side(Set.of());
              }
              if (!isEmpty(scenario.getScenario_teams())) {
                esScenario.setBase_teams_side(scenario.getScenario_teams());
              } else {
                esScenario.setBase_teams_side(Set.of());
              }
              return esScenario;
            })
        .toList();
  }
}
