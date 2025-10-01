package io.openaev.engine.model;

import io.openaev.annotation.EsQueryable;
import io.openaev.annotation.Indexable;
import io.openaev.annotation.Queryable;
import io.openaev.engine.model.assetgroup.EsAssetGroup;
import io.openaev.engine.model.attackpattern.EsAttackPattern;
import io.openaev.engine.model.endpoint.EsEndpoint;
import io.openaev.engine.model.finding.EsFinding;
import io.openaev.engine.model.inject.EsInject;
import io.openaev.engine.model.injectexpectation.EsInjectExpectation;
import io.openaev.engine.model.scenario.EsScenario;
import io.openaev.engine.model.securityplatform.EsSecurityPlatform;
import io.openaev.engine.model.simulation.EsSimulation;
import io.openaev.engine.model.tag.EsTag;
import io.openaev.engine.model.team.EsTeam;
import io.openaev.engine.model.vulnerableendpoint.EsVulnerableEndpoint;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(
    discriminatorProperty = "base_entity",
    oneOf = {
      EsAttackPattern.class,
      EsEndpoint.class,
      EsFinding.class,
      EsInject.class,
      EsInjectExpectation.class,
      EsScenario.class,
      EsSimulation.class,
      EsTag.class,
      EsVulnerableEndpoint.class,
      EsTeam.class,
      EsAssetGroup.class,
      EsSecurityPlatform.class,
    },
    discriminatorMapping = {
      @DiscriminatorMapping(value = "attack-pattern", schema = EsAttackPattern.class),
      @DiscriminatorMapping(value = "endpoint", schema = EsEndpoint.class),
      @DiscriminatorMapping(value = "finding", schema = EsFinding.class),
      @DiscriminatorMapping(value = "inject", schema = EsInject.class),
      @DiscriminatorMapping(value = "expectation-inject", schema = EsInjectExpectation.class),
      @DiscriminatorMapping(value = "simulation", schema = EsSimulation.class),
      @DiscriminatorMapping(value = "scenario", schema = EsScenario.class),
      @DiscriminatorMapping(value = "tag", schema = EsTag.class),
      @DiscriminatorMapping(value = "vulnerable-endpoint", schema = EsVulnerableEndpoint.class),
      @DiscriminatorMapping(value = "team", schema = EsTeam.class),
      @DiscriminatorMapping(value = "security-platform", schema = EsSecurityPlatform.class),
      @DiscriminatorMapping(value = "asset-group", schema = EsAssetGroup.class),
    })
public class EsBase {

  @Queryable(label = "id", filterable = true, sortable = true)
  @EsQueryable(keyword = true)
  private String base_id;

  @Queryable(label = "entity", filterable = true, sortable = true)
  @EsQueryable(keyword = true)
  private String base_entity;

  private String base_representative;

  @Queryable(label = "created at", filterable = true, sortable = true)
  private Instant base_created_at;

  @Queryable(label = "updated at", filterable = true, sortable = true)
  private Instant base_updated_at;

  // -- Base for ACL --
  private List<String> base_restrictions;

  // To support logical side deletions, means "DELETE CASCADE", to set only if you want to delete
  // the linked object itself
  // Example : I delete the inject Id-A, all objects which contain in their base_dependencies the
  // Id-A will be entirely deleted
  // https://github.com/rieske/postgres-cdc could be an alternative.
  private List<String> base_dependencies = new ArrayList<>();

  public EsBase() {
    try {
      base_entity = this.getClass().getAnnotation(Indexable.class).index();
    } catch (Exception e) {
      // Need for json deserialize
    }
  }
}
