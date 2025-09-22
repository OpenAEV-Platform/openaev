package io.openbas.engine.model.injectexpectation;

import static io.openbas.engine.EsUtils.buildRestrictions;
import static io.openbas.helper.InjectExpectationHelper.computeStatus;
import static java.lang.String.valueOf;
import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import io.openbas.database.raw.RawInjectExpectation;
import io.openbas.database.repository.InjectExpectationRepository;
import io.openbas.engine.Handler;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InjectExpectationHandler implements Handler<EsInjectExpectation> {

  private final InjectExpectationRepository injectExpectationRepository;

  @Override
  public List<EsInjectExpectation> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawInjectExpectation> forIndexing =
        this.injectExpectationRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            injectExpectation -> {
              EsInjectExpectation esInjectExpectation = new EsInjectExpectation();
              // Base
              esInjectExpectation.setBase_id(injectExpectation.getInject_expectation_id());
              esInjectExpectation.setBase_representative(
                  injectExpectation.getInject_expectation_name());
              esInjectExpectation.setBase_created_at(
                  injectExpectation.getInject_expectation_created_at());
              esInjectExpectation.setBase_updated_at(
                  injectExpectation.getInject_expectation_updated_at());
              esInjectExpectation.setBase_restrictions(
                  buildRestrictions(
                      injectExpectation.getExercise_id(), injectExpectation.getInject_id()));
              // Specific
              esInjectExpectation.setInject_expectation_name(
                  injectExpectation.getInject_expectation_name());
              esInjectExpectation.setInject_expectation_description(
                  injectExpectation.getInject_expectation_description());
              esInjectExpectation.setInject_expectation_type(
                  injectExpectation.getInject_expectation_type());
              esInjectExpectation.setInject_expectation_results(
                  injectExpectation.getInject_expectation_results());
              esInjectExpectation.setInject_title(injectExpectation.getInject_title());

              esInjectExpectation.setInject_expectation_score(
                  injectExpectation.getInject_expectation_score());
              esInjectExpectation.setInject_expectation_expected_score(
                  injectExpectation.getInject_expectation_expected_score());
              esInjectExpectation.setInject_expectation_expiration_time(
                  injectExpectation.getInject_expiration_time());
              esInjectExpectation.setInject_expectation_group(
                  injectExpectation.getInject_expectation_group());
              // Dependencies (see base_dependencies in EsBase)
              List<String> dependencies = new ArrayList<>();
              if (hasText(injectExpectation.getExercise_id())) {
                dependencies.add(injectExpectation.getExercise_id());
                esInjectExpectation.setBase_simulation_side(injectExpectation.getExercise_id());
              } else {
                esInjectExpectation.setBase_simulation_side(null);
              }
              if (hasText(injectExpectation.getScenario_id())) {
                dependencies.add(injectExpectation.getScenario_id());
                esInjectExpectation.setBase_scenario_side(injectExpectation.getScenario_id());
              } else {
                esInjectExpectation.setBase_scenario_side(null);
              }
              if (hasText(injectExpectation.getInject_id())) {
                dependencies.add(injectExpectation.getInject_id());
                esInjectExpectation.setBase_inject_side(injectExpectation.getInject_id());
              } else {
                esInjectExpectation.setBase_inject_side(null);
              }
              if (hasText(injectExpectation.getUser_id())) {
                dependencies.add(injectExpectation.getUser_id());
                esInjectExpectation.setBase_user_side(injectExpectation.getUser_id());
              } else {
                esInjectExpectation.setBase_user_side(null);
              }
              if (hasText(injectExpectation.getTeam_id())) {
                dependencies.add(injectExpectation.getTeam_id());
                esInjectExpectation.setBase_team_side(injectExpectation.getTeam_id());
              } else {
                esInjectExpectation.setBase_team_side(null);
              }
              if (hasText(injectExpectation.getAgent_id())) {
                dependencies.add(injectExpectation.getAgent_id());
                esInjectExpectation.setBase_agent_side(injectExpectation.getAgent_id());
              } else {
                esInjectExpectation.setBase_agent_side(null);
              }
              if (hasText(injectExpectation.getAsset_id())) {
                dependencies.add(injectExpectation.getAsset_id());
                esInjectExpectation.setBase_asset_side(injectExpectation.getAsset_id());
              } else {
                esInjectExpectation.setBase_asset_side(null);
              }
              if (hasText(injectExpectation.getAsset_group_id())) {
                dependencies.add(injectExpectation.getAsset_group_id());
                esInjectExpectation.setBase_asset_group_side(injectExpectation.getAsset_group_id());
              } else {
                esInjectExpectation.setBase_asset_group_side(null);
              }
              if (!isEmpty(injectExpectation.getAttack_pattern_ids())) {
                esInjectExpectation.setBase_attack_patterns_side(
                    injectExpectation.getAttack_pattern_ids());
              } else {
                esInjectExpectation.setBase_attack_patterns_side(Set.of());
              }
              if (!isEmpty(injectExpectation.getSecurity_platform_ids())) {
                esInjectExpectation.setBase_security_platforms_side(
                    injectExpectation.getSecurity_platform_ids());
              } else {
                esInjectExpectation.setBase_security_platforms_side(Set.of());
              }
              esInjectExpectation.setInject_expectation_status(
                  valueOf(
                      computeStatus(
                          injectExpectation.getInject_expectation_score(),
                          injectExpectation.getInject_expectation_expected_score())));
              esInjectExpectation.setBase_dependencies(dependencies);
              return esInjectExpectation;
            })
        .toList();
  }
}
