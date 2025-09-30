package io.openbas.engine.model.inject;

import static io.openbas.engine.EsUtils.buildRestrictions;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import io.openbas.database.model.ExecutionStatus;
import io.openbas.database.raw.RawInjectIndexing;
import io.openbas.database.repository.InjectRepository;
import io.openbas.engine.Handler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InjectHandler implements Handler<EsInject> {

  private InjectRepository injectRepository;

  @Autowired
  public void setInjectRepository(InjectRepository injectRepository) {
    this.injectRepository = injectRepository;
  }

  @Override
  public List<EsInject> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawInjectIndexing> forIndexing = injectRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            inject -> {
              EsInject esInject = new EsInject();
              // Base
              esInject.setBase_id(inject.getInject_id());
              esInject.setBase_representative(inject.getInject_title());
              esInject.setBase_created_at(inject.getInject_created_at());

              if (inject.getInjector_contract_updated_at() != null
                  && inject
                      .getInjector_contract_updated_at()
                      .isAfter(inject.getInject_updated_at())) {
                esInject.setBase_updated_at(inject.getInjector_contract_updated_at());
              } else {
                esInject.setBase_updated_at(inject.getInject_updated_at());
              }
              esInject.setBase_restrictions(
                  buildRestrictions(inject.getInject_scenario(), inject.getInject_Exercise()));
              // Specific
              esInject.setInject_title(inject.getInject_title());
              esInject.setInject_status(
                  inject.getInject_status_name() != null
                          && !inject.getInject_status_name().isBlank()
                      ? inject.getInject_status_name()
                      : ExecutionStatus.DRAFT.name());
              esInject.setBase_platforms_side_denormalized(inject.getInject_platforms());
              esInject.setExecution_date(inject.getTracking_sent_date());
              // Dependencies (see base_dependencies in EsBase)
              List<String> dependencies = new ArrayList<>();
              if (hasText(inject.getInject_scenario())) {
                dependencies.add(inject.getInject_scenario());
                esInject.setBase_scenario_side(inject.getInject_scenario());
              } else {
                esInject.setBase_scenario_side(null);
              }
              if (hasText(inject.getInject_Exercise())) {
                dependencies.add(inject.getInject_Exercise());
                esInject.setBase_simulation_side(inject.getInject_Exercise());
              } else {
                esInject.setBase_simulation_side(null);
              }
              if (!isEmpty(inject.getInject_attack_patterns())) {
                esInject.setBase_attack_patterns_side(inject.getInject_attack_patterns());
              } else {
                esInject.setBase_attack_patterns_side(Set.of());
              }
              if (!isEmpty(inject.getInject_children())) {
                esInject.setBase_inject_children_side(inject.getInject_children());
              } else {
                esInject.setBase_inject_children_side(Set.of());
              }
              if (!isEmpty(inject.getAttack_patterns_children())) {
                esInject.setBase_attack_patterns_children_side(
                    inject.getAttack_patterns_children());
              } else {
                esInject.setBase_attack_patterns_children_side(Set.of());
              }
              if (!isEmpty(inject.getInject_kill_chain_phases())) {
                esInject.setBase_kill_chain_phases_side(inject.getInject_kill_chain_phases());
              } else {
                esInject.setBase_kill_chain_phases_side(Set.of());
              }
              if (hasText(inject.getInject_injector_contract())) {
                esInject.setBase_inject_contract_side(inject.getInject_injector_contract());
              } else {
                esInject.setBase_inject_contract_side(null);
              }
              if (!isEmpty(inject.getInject_tags())) {
                esInject.setBase_tags_side(inject.getInject_tags());
              } else {
                esInject.setBase_tags_side(Set.of());
              }
              if (!isEmpty(inject.getInject_assets())) {
                esInject.setBase_assets_side(inject.getInject_assets());
              } else {
                esInject.setBase_assets_side(Set.of());
              }
              if (!isEmpty(inject.getInject_asset_groups())) {
                esInject.setBase_asset_groups_side(inject.getInject_asset_groups());
              } else {
                esInject.setBase_asset_groups_side(Set.of());
              }
              if (!isEmpty(inject.getInject_teams())) {
                esInject.setBase_teams_side(inject.getInject_teams());
              } else {
                esInject.setBase_teams_side(Set.of());
              }
              esInject.setBase_dependencies(dependencies);
              return esInject;
            })
        .toList();
  }
}
