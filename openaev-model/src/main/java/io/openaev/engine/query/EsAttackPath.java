package io.openaev.engine.query;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EsAttackPath {

  @Getter
  @Setter
  public static class KillChainPhaseObject {
    private String name;
    @NotBlank private String id;
    private Long order;

    public KillChainPhaseObject(@NotBlank String id, String name, Long order) {
      this.id = id;
      this.name = name;
      this.order = order;
    }
  }

  private List<KillChainPhaseObject> killChainPhases;
  @NotBlank private String attackPatternId;
  @NotBlank private String attackPatternName;
  @NotBlank private String attackPatternExternalId;
  private Long value;
  private Set<String> attackPatternChildrenIds;
  private Set<String> injectIds;

  public EsAttackPath(
      @NotBlank String attackPatternId,
      @NotBlank String attackPatternName,
      @NotBlank String attackPatternExternalId,
      List<KillChainPhaseObject> killChainPhases,
      Set<String> attackPatternChildrenIds,
      Set<String> injectIds,
      Long value) {
    this.attackPatternId = attackPatternId;
    this.attackPatternName = attackPatternName;
    this.attackPatternExternalId = attackPatternExternalId;
    this.killChainPhases = killChainPhases;
    this.attackPatternChildrenIds = attackPatternChildrenIds;
    this.injectIds = injectIds;
    this.value = value;
  }
}
