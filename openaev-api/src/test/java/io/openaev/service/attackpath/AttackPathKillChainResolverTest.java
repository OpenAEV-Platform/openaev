package io.openaev.service.attackpath;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.database.model.Condition;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import io.openaev.service.attackpath.AttackPathKillChainResolver.KillChainMeta;
import io.openaev.service.attackpath.dto.ConsumedFindingKeyDTO;
import io.openaev.utils.ConditionUtils;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure resolver logic: no DB, real POJOs, no mocks. */
class AttackPathKillChainResolverTest {

  private final AttackPathKillChainResolver resolver =
      new AttackPathKillChainResolver(new ConditionUtils());

  @Test
  @DisplayName("a DEPEND_ON condition yields the prerequisite step template id")
  void dependOnYieldsPrerequisite() {
    Condition dependOn =
        Condition.builder().type(ConditionType.DEPEND_ON).value("step-nmap").build();

    KillChainMeta meta = resolver.resolve(List.of(dependOn));

    assertThat(meta.dependsOn()).containsExactly("step-nmap");
    assertThat(meta.consumedFindingKeys()).isEmpty();
  }

  @Test
  @DisplayName("a leaf filter surfaces the consumed key with the PrimitiveType label, not name()")
  void leafFilterSurfacesLabel() {
    Condition portLeaf =
        Condition.builder().type(ConditionType.EQ).keyType(PrimitiveType.Port).value("445").build();

    KillChainMeta meta = resolver.resolve(List.of(portLeaf));

    assertThat(meta.consumedFindingKeys())
        .containsExactly(new ConsumedFindingKeyDTO("port", "EQ", "445"));
    assertThat(meta.dependsOn()).isEmpty();
  }

  @Test
  @DisplayName("a composite AND filter surfaces ALL its leaf keys (the tree is flattened)")
  void compositeFilterFlattens() {
    Condition portLeaf =
        Condition.builder().type(ConditionType.EQ).keyType(PrimitiveType.Port).value("445").build();
    Condition serviceLeaf =
        Condition.builder()
            .type(ConditionType.EQ)
            .keyType(PrimitiveType.Service)
            .value("smb")
            .build();
    Condition andRoot =
        Condition.builder()
            .type(ConditionType.AND)
            .conditionChildren(List.of(portLeaf, serviceLeaf))
            .build();

    KillChainMeta meta = resolver.resolve(List.of(andRoot));

    assertThat(meta.consumedFindingKeys())
        .containsExactlyInAnyOrder(
            new ConsumedFindingKeyDTO("port", "EQ", "445"),
            new ConsumedFindingKeyDTO("service", "EQ", "smb"));
  }

  @Test
  @DisplayName("no conditions yields empty metadata")
  void noConditionsIsEmpty() {
    KillChainMeta meta = resolver.resolve(List.of());

    assertThat(meta.dependsOn()).isEmpty();
    assertThat(meta.consumedFindingKeys()).isEmpty();
  }
}
