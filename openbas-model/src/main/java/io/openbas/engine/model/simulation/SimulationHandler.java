package io.openbas.engine.model.simulation;

import static io.openbas.engine.EsUtils.buildRestrictions;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import io.openbas.database.raw.RawSimulation;
import io.openbas.database.repository.ExerciseRepository;
import io.openbas.engine.Handler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class SimulationHandler implements Handler<EsSimulation> {

  private final ExerciseRepository simulationRepository;

  @Override
  public List<EsSimulation> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawSimulation> forIndexing = simulationRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            simulation -> {
              EsSimulation esSimulation = new EsSimulation();
              // Base
              esSimulation.setBase_id(simulation.getExercise_id());
              esSimulation.setStatus(simulation.getExercise_status());
              esSimulation.setBase_created_at(simulation.getExercise_created_at());
              esSimulation.setBase_updated_at(simulation.getExercise_injects_updated_at());
              esSimulation.setName(simulation.getExercise_name());

              esSimulation.setBase_representative(simulation.getExercise_name());
              esSimulation.setBase_restrictions(
                  buildRestrictions(simulation.getExercise_id(), simulation.getScenario_id()));
              // Specific
              esSimulation.setBase_platforms_side_denormalized(simulation.getExercise_platforms());
              // Dependencies (see base_dependencies in EsBase)
              if (!isEmpty(simulation.getExercise_tags())) {
                esSimulation.setBase_tags_side(simulation.getExercise_tags());
              } else {
                esSimulation.setBase_tags_side(Set.of());
              }
              if (!isEmpty(simulation.getExercise_assets())) {
                esSimulation.setBase_assets_side(simulation.getExercise_assets());
              } else {
                esSimulation.setBase_assets_side(Set.of());
              }
              if (!isEmpty(simulation.getExercise_asset_groups())) {
                esSimulation.setBase_asset_groups_side(simulation.getExercise_asset_groups());
              } else {
                esSimulation.setBase_asset_groups_side(Set.of());
              }
              if (!isEmpty(simulation.getExercise_teams())) {
                esSimulation.setBase_teams_side(simulation.getExercise_teams());
              } else {
                esSimulation.setBase_teams_side(Set.of());
              }
              if (hasText(simulation.getScenario_id())) {
                esSimulation.setBase_scenario_side(simulation.getScenario_id());
              } else {
                esSimulation.setBase_scenario_side(null);
              }
              return esSimulation;
            })
        .toList();
  }
}
