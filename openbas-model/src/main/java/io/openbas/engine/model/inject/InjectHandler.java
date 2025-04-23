package io.openbas.engine.model.inject;

import static io.openbas.engine.EsUtils.buildRestrictions;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import io.openbas.database.raw.RawInjectIndexing;
import io.openbas.database.repository.InjectRepository;
import io.openbas.engine.Handler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
              esInject.setBase_updated_at(inject.getInject_updated_at());
              esInject.setBase_restrictions(
                  buildRestrictions(inject.getInject_scenario(), inject.getInject_Exercise()));
              // Specific
              esInject.setInject_title(inject.getInject_title());
              esInject.setInject_status(inject.getInject_status_name());
              // Dependencies
              List<String> dependencies = new ArrayList<>();
              if (hasText(inject.getInject_scenario())) {
                dependencies.add(inject.getInject_scenario());
                esInject.setBase_scenario_side(inject.getInject_scenario());
              }
              if (hasText(inject.getInject_Exercise())) {
                dependencies.add(inject.getInject_Exercise());
                esInject.setBase_simulation_side(inject.getInject_Exercise());
              }
              if (!isEmpty(inject.getInject_attack_patterns())) {
                dependencies.addAll(inject.getInject_attack_patterns());
                esInject.setBase_attack_patterns_side(inject.getInject_attack_patterns());
              }
              if (!isEmpty(inject.getInject_kill_chain_phases())) {
                dependencies.addAll(inject.getInject_kill_chain_phases());
                esInject.setBase_kill_chain_phases_side(inject.getInject_kill_chain_phases());
              }
              if (hasText(inject.getInject_injector_contract())) {
                dependencies.add(inject.getInject_injector_contract());
                esInject.setBase_inject_contract_side(inject.getInject_injector_contract());
              }
              esInject.setBase_dependencies(dependencies);
              return esInject;
            })
        .toList();
  }
}
