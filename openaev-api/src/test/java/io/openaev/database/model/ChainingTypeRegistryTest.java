package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Chaining type registry")
class ChainingTypeRegistryTest {

  @Test
  @DisplayName("Should classify contract output types")
  void given_contractOutputTypes_should_classifyOutputKinds() {
    assertThat(ChainingTypeRegistry.isPrimitiveType(ContractOutputType.IPv4)).isTrue();
    assertThat(ChainingTypeRegistry.isComplexType(ContractOutputType.Credentials)).isTrue();
    assertThat(ChainingTypeRegistry.isChainableType(ContractOutputType.Asset)).isFalse();
  }

  @Test
  @DisplayName("Should expose primitive catalog used by arguments and conditions")
  void given_primitiveCatalog_should_includeConfiguredPrimitiveTypes() {
    assertThat(ChainingTypeRegistry.getPrimitiveTypes())
        .contains(
            PrimitiveType.Host,
            PrimitiveType.Domain,
            PrimitiveType.IPv4,
            PrimitiveType.IPv6,
            PrimitiveType.TargetedAsset,
            PrimitiveType.Document);
  }

  @Test
  @DisplayName("Should parse primitive labels for import/export compatibility")
  void given_primitiveLabels_should_mapToPrimitiveTypes() {
    assertThat(PrimitiveType.fromLabel("ipv4")).isEqualTo(PrimitiveType.IPv4);
    assertThat(PrimitiveType.fromLabel("document")).isEqualTo(PrimitiveType.Document);
    assertThat(PrimitiveType.fromLabel("targeted-asset")).isEqualTo(PrimitiveType.TargetedAsset);
  }
}
