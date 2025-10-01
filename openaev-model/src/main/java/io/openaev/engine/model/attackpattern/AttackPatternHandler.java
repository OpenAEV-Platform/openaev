package io.openaev.engine.model.attackpattern;

import static org.springframework.util.CollectionUtils.isEmpty;
import static org.springframework.util.StringUtils.hasText;

import io.openaev.database.raw.RawAttackPattern;
import io.openaev.database.repository.AttackPatternRepository;
import io.openaev.engine.Handler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttackPatternHandler implements Handler<EsAttackPattern> {

  private final AttackPatternRepository attackPatternRepository;

  @Override
  public List<EsAttackPattern> fetch(Instant from) {
    Instant queryFrom = from != null ? from : Instant.ofEpochMilli(0);
    List<RawAttackPattern> forIndexing = attackPatternRepository.findForIndexing(queryFrom);
    return forIndexing.stream()
        .map(
            attackPattern -> {
              EsAttackPattern esAttackPattern = new EsAttackPattern();
              // Base
              esAttackPattern.setBase_id(attackPattern.getAttack_pattern_id());
              esAttackPattern.setBase_representative(
                  attackPattern.getAttack_pattern_external_id()
                      + " - "
                      + attackPattern.getAttack_pattern_name());
              esAttackPattern.setBase_created_at(attackPattern.getAttack_pattern_created_at());
              esAttackPattern.setBase_updated_at(attackPattern.getAttack_pattern_updated_at());
              // Specific
              esAttackPattern.setStixId(attackPattern.getAttack_pattern_stix_id());
              esAttackPattern.setName(attackPattern.getAttack_pattern_name());
              esAttackPattern.setDescription(attackPattern.getAttack_pattern_description());
              esAttackPattern.setExternalId(attackPattern.getAttack_pattern_external_id());
              esAttackPattern.setPlatforms(attackPattern.getAttack_pattern_platforms());
              // Dependencies (see base_dependencies in EsBase)
              if (hasText(attackPattern.getAttack_pattern_parent())) {
                esAttackPattern.setBase_attack_pattern_side(
                    attackPattern.getAttack_pattern_parent());
              } else {
                esAttackPattern.setBase_attack_pattern_side(null);
              }
              if (!isEmpty(attackPattern.getAttack_pattern_kill_chain_phases())) {
                esAttackPattern.setBase_kill_chain_phases_side(
                    attackPattern.getAttack_pattern_kill_chain_phases());
              } else {
                esAttackPattern.setBase_kill_chain_phases_side(Set.of());
              }
              return esAttackPattern;
            })
        .toList();
  }
}
