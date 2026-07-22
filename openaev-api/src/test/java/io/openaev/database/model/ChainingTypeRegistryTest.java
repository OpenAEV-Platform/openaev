package io.openaev.database.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Chaining type registry")
class ChainingTypeRegistryTest {

  @Test
  @DisplayName("Should classify contract output types")
  void given_contractOutputTypes_should_classifyOutputKinds() {
    assertThat(
            ChainingTypeRegistry.getMappedTypeForContractOutputType(ContractOutputType.IPv4).kind())
        .isEqualTo(ChainingTypeKind.PRIMITIVE);
    assertThat(
            ChainingTypeRegistry.getMappedTypeForContractOutputType(ContractOutputType.Credentials)
                .kind())
        .isEqualTo(ChainingTypeKind.COMPLEX);
    assertThat(
            ChainingTypeRegistry.getMappedTypeForContractOutputType(ContractOutputType.Asset)
                .kind())
        .isEqualTo(ChainingTypeKind.COMPLEX);
  }

  @Test
  @DisplayName("Should expose primitive catalog used by arguments and conditions")
  void given_primitiveCatalog_should_includeConfiguredPrimitiveTypes() {
    var primitiveTypes = ChainingTypeRegistry.getPrimitiveTypes();
    assertThat(primitiveTypes)
        .contains(
            PrimitiveType.Host,
            PrimitiveType.Domain,
            PrimitiveType.IPv4,
            PrimitiveType.IPv6,
            PrimitiveType.IpSubnet,
            PrimitiveType.TargetedAsset,
            PrimitiveType.Document,
            PrimitiveType.AssetId,
            PrimitiveType.AssetGroupId);
  }

  @Test
  @DisplayName("Should parse primitive labels for import/export compatibility")
  void given_primitiveLabels_should_mapToPrimitiveTypes() {
    assertThat(PrimitiveType.fromLabel("ipv4")).isEqualTo(PrimitiveType.IPv4);
    assertThat(PrimitiveType.fromLabel("document")).isEqualTo(PrimitiveType.Document);
    assertThat(PrimitiveType.fromLabel("targeted-asset")).isEqualTo(PrimitiveType.TargetedAsset);
    assertThat(PrimitiveType.fromLabel("ip_subnet")).isEqualTo(PrimitiveType.IpSubnet);
    assertThat(PrimitiveType.fromLabel("asset_id")).isEqualTo(PrimitiveType.AssetId);
    assertThat(PrimitiveType.fromLabel("asset_group_id")).isEqualTo(PrimitiveType.AssetGroupId);
  }

  @Test
  @DisplayName("Should map scope value types to primitive chaining types")
  void given_scopeValueTypes_should_mapToPrimitiveTypes() {
    assertThat(
            ChainingTypeRegistry.getMappedTypeForScopeRuleValueType(ScopeRuleValueType.IP)
                .primitiveTypes())
        .containsExactly(PrimitiveType.IPv4, PrimitiveType.IPv6);
    assertThat(
            ChainingTypeRegistry.getMappedTypeForScopeRuleValueType(ScopeRuleValueType.IP_SUBNET)
                .primitiveTypes())
        .containsExactly(PrimitiveType.IpSubnet);
    assertThat(
            ChainingTypeRegistry.getMappedTypeForScopeRuleValueType(ScopeRuleValueType.DOMAIN)
                .primitiveTypes())
        .containsExactly(PrimitiveType.Domain);
    assertThat(
            ChainingTypeRegistry.getMappedTypeForScopeRuleValueType(ScopeRuleValueType.ASSET_ID)
                .primitiveTypes())
        .containsExactly(PrimitiveType.AssetId);
    assertThat(
            ChainingTypeRegistry.getMappedTypeForScopeRuleValueType(
                    ScopeRuleValueType.ASSET_GROUP_ID)
                .primitiveTypes())
        .containsExactly(PrimitiveType.AssetGroupId);
  }

  @Test
  @DisplayName("Should keep only expectation signature non-chainable")
  void given_contractOutputTypes_should_keepOnlyExpectationSignatureNonChainable() {
    for (ContractOutputType type : ContractOutputType.values()) {
      ChainingTypeKind kind = ChainingTypeRegistry.getMappedTypeForContractOutputType(type).kind();
      if (type == ContractOutputType.ExpectationSignature) {
        assertThat(kind).isEqualTo(ChainingTypeKind.NOT_CHAINABLE);
      } else {
        assertThat(kind).isNotEqualTo(ChainingTypeKind.NOT_CHAINABLE);
      }
    }
  }

  @Test
  @DisplayName("Complex type should retain its origin ContractOutputType")
  void given_credentials_should_retainOriginAndBeComplex() {
    ChainingMappedType mapped =
        ChainingTypeRegistry.getMappedTypeForContractOutputType(ContractOutputType.Credentials);
    assertThat(mapped.kind()).isEqualTo(ChainingTypeKind.COMPLEX);
    assertThat(mapped.origin()).isEqualTo(ContractOutputType.Credentials);
  }

  @Test
  @DisplayName("Primitive type should have empty recipe")
  void given_primitiveType_should_haveEmptyRecipe() {
    ChainingMappedType mapped =
        ChainingTypeRegistry.getMappedTypeForContractOutputType(ContractOutputType.Text);
    assertThat(mapped.kind()).isEqualTo(ChainingTypeKind.PRIMITIVE);
    assertThat(mapped.primitiveTypes()).containsExactly(PrimitiveType.Text);
    assertThat(mapped.origin()).isNull();
  }

  @Test
  @DisplayName("fromLabelOptional should resolve known labels and return empty for unknown")
  void given_labels_should_resolveOptionally() {
    assertThat(PrimitiveType.fromLabelOptional("username"))
        .isEqualTo(Optional.of(PrimitiveType.Username));
    assertThat(PrimitiveType.fromLabelOptional("password"))
        .isEqualTo(Optional.of(PrimitiveType.Password));
    assertThat(PrimitiveType.fromLabelOptional("nonexistent")).isEmpty();
  }
}
