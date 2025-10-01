package io.openaev.engine.model.attackpattern;

import io.openaev.annotation.Indexable;
import io.openaev.annotation.Queryable;
import io.openaev.engine.model.EsBase;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Indexable(index = "attack-pattern", label = "Attack pattern")
public class EsAttackPattern extends EsBase {
  /* Every attribute must be uniq, so prefixed with the entity type! */
  /* Except relationships, they should have same name on every model! */

  @Queryable(label = "stix id")
  private String stixId;

  @Queryable(label = "attack pattern name")
  private String name;

  @Queryable(label = "attack pattern description")
  private String description;

  @Queryable(label = "attack pattern external id")
  private String externalId;

  @Queryable(label = "platforms")
  private List<String> platforms;

  // -- SIDE --

  @Queryable(label = "attack pattern parent")
  private String base_attack_pattern_side; // Must finish by _side

  @Queryable(label = "kill chain phases")
  private Set<String> base_kill_chain_phases_side; // Must finish by _side
}
