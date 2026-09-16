package io.openaev.api.chaining;

import static org.assertj.core.api.Assertions.assertThat;

import io.openaev.api.chaining.dto.PrimitiveTypeCapabilitiesOutput;
import io.openaev.api.chaining.dto.PrimitiveTypeDescriptorOutput;
import io.openaev.api.chaining.dto.PrimitiveTypeFormatRuleOutput;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import io.openaev.validator.primitive.FormatRuleKind;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("PrimitiveTypeDescriptorMapper Tests")
class PrimitiveTypeDescriptorMapperTest {

  private static Map<PrimitiveType, PrimitiveTypeDescriptorOutput> descriptorsByType() {
    return PrimitiveTypeDescriptorMapper.toOutputs().stream()
        .collect(
            java.util.stream.Collectors.toMap(
                PrimitiveTypeDescriptorOutput::getPrimitiveType, Function.identity()));
  }

  @Nested
  @DisplayName("Catalogue completeness")
  class CatalogueCompleteness {

    @Test
    @DisplayName("should expose one descriptor per primitive type")
    void given_catalogue_should_coverEveryPrimitiveType() {
      // Act
      List<PrimitiveTypeDescriptorOutput> descriptors = PrimitiveTypeDescriptorMapper.toOutputs();

      // Assert
      assertThat(descriptors).hasSize(PrimitiveType.values().length);
      assertThat(descriptorsByType().keySet()).containsExactlyInAnyOrder(PrimitiveType.values());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PrimitiveType.class)
    @DisplayName("should always expose capabilities and a validation")
    void given_anyType_should_exposeACompleteDescriptor(PrimitiveType type) {
      // Act
      PrimitiveTypeDescriptorOutput descriptor = PrimitiveTypeDescriptorMapper.toOutput(type);

      // Assert
      assertThat(descriptor.getPrimitiveType()).isEqualTo(type);
      assertThat(descriptor.getCapabilities()).isNotNull();
      assertThat(descriptor.getValidation()).isNotNull();
      assertThat(descriptor.getValidation().getRules()).isNotNull();
    }

    @Test
    @DisplayName("should order descriptors by label so the UI can render them as received")
    void given_catalogue_should_beOrderedByLabel() {
      // Act
      List<String> labels =
          PrimitiveTypeDescriptorMapper.toOutputs().stream()
              .map(descriptor -> descriptor.getPrimitiveType().label)
              .toList();

      // Assert
      assertThat(labels).isSorted();
    }
  }

  @Nested
  @DisplayName("Capabilities")
  class Capabilities {

    @Test
    @DisplayName("should mark only the numeric types as numeric")
    void given_catalogue_should_restrictNumericToNumericTypes() {
      // Act
      Map<PrimitiveType, PrimitiveTypeDescriptorOutput> descriptors = descriptorsByType();
      List<PrimitiveType> numeric =
          descriptors.entrySet().stream()
              .filter(entry -> entry.getValue().getCapabilities().isNumericValue())
              .map(Map.Entry::getKey)
              .toList();

      // Assert
      assertThat(numeric).containsExactlyInAnyOrder(PrimitiveType.Number, PrimitiveType.Port);
    }

    @Test
    @DisplayName("should not offer comparison on Severity, whose values are free labels")
    void given_severity_should_notSupportComparison() {
      // Act
      PrimitiveTypeDescriptorOutput descriptor =
          PrimitiveTypeDescriptorMapper.toOutput(PrimitiveType.Severity);

      // Assert - the backend compares with Double.parseDouble, so "> medium" could never match
      assertThat(descriptor.getCapabilities().isNumericValue()).isFalse();
    }

    @Test
    @DisplayName("should not offer case sensitivity on the types whose values carry no case")
    void given_caselessType_should_notOfferCaseSensitivity() {
      // Act
      Map<PrimitiveType, PrimitiveTypeDescriptorOutput> descriptors = descriptorsByType();
      List<PrimitiveType> caseless =
          descriptors.entrySet().stream()
              .filter(entry -> !entry.getValue().getCapabilities().isCaseSensitivity())
              .map(Map.Entry::getKey)
              .toList();

      // Assert - frozen on purpose: adding a type here removes the Aa toggle from its field
      assertThat(caseless)
          .containsExactlyInAnyOrder(
              PrimitiveType.Number,
              PrimitiveType.Port,
              PrimitiveType.IPv4,
              PrimitiveType.IPv6,
              PrimitiveType.IpSubnet,
              PrimitiveType.Hash,
              PrimitiveType.AssetId,
              PrimitiveType.AssetGroupId,
              PrimitiveType.CVE,
              PrimitiveType.SID);
    }

    @Test
    @DisplayName("should keep case sensitivity on free-form text")
    void given_freeFormType_should_offerCaseSensitivity() {
      // Assert
      assertThat(
              PrimitiveTypeDescriptorMapper.toOutput(PrimitiveType.Text)
                  .getCapabilities()
                  .isCaseSensitivity())
          .isTrue();
      assertThat(
              PrimitiveTypeDescriptorMapper.toOutput(PrimitiveType.Username)
                  .getCapabilities()
                  .isCaseSensitivity())
          .isTrue();
    }

    @Test
    @DisplayName("should keep case sensitivity independent from the numeric capability")
    void given_catalogue_should_notDeriveOneCapabilityFromAnother() {
      // Act
      Map<PrimitiveType, PrimitiveTypeDescriptorOutput> descriptors = descriptorsByType();

      // Assert - a caseless, non-numeric type proves caseSensitivity is declared per type rather
      // than computed as !numericValue
      PrimitiveTypeCapabilitiesOutput hash = descriptors.get(PrimitiveType.Hash).getCapabilities();
      assertThat(hash.isNumericValue()).isFalse();
      assertThat(hash.isCaseSensitivity()).isFalse();
      // ... and it carries no format rule at all, being credential material rather than a digest
      assertThat(descriptors.get(PrimitiveType.Hash).getValidation().getRules()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Validation metadata")
  class ValidationMetadata {

    @Test
    @DisplayName("should apply format rules to equality operators only")
    void given_anyDescriptor_should_applyRulesToEqualityOperatorsOnly() {
      // Act
      PrimitiveTypeDescriptorOutput descriptor =
          PrimitiveTypeDescriptorMapper.toOutput(PrimitiveType.IPv4);

      // Assert - IN / NIN match on substrings and IS_NULL carries no value
      assertThat(descriptor.getValidation().getAppliesTo())
          .containsExactlyInAnyOrder(ConditionType.EQ, ConditionType.NEQ);
    }

    @Test
    @DisplayName("should expose no rule at all when a type constrains no format")
    void given_typeWithoutRule_should_exposeNoRule() {
      // Act
      PrimitiveTypeDescriptorOutput descriptor =
          PrimitiveTypeDescriptorMapper.toOutput(PrimitiveType.Document);

      // Assert
      assertThat(descriptor.getValidation().getRules()).isEmpty();
    }

    @Test
    @DisplayName("should expose every alternative rule of a multi-rule type")
    void given_multiRuleType_should_exposeEveryAlternative() {
      // Act
      PrimitiveTypeDescriptorOutput descriptor =
          PrimitiveTypeDescriptorMapper.toOutput(PrimitiveType.Host);

      // Assert - a host is an IP literal or a name, and any of the three is acceptable
      assertThat(descriptor.getValidation().getRules())
          .extracting(PrimitiveTypeFormatRuleOutput::getKind)
          .containsExactly(
              FormatRuleKind.IPV4.name(), FormatRuleKind.IPV6.name(), FormatRuleKind.DOMAIN.name());
    }

    @Test
    @DisplayName("should expose a pattern for serializable rules and none for named ones")
    void given_rules_should_exposePatternOnlyWhenPortable() {
      // Assert - a regex approximating InetAddress would make the front accept what the back
      // rejects
      assertThat(
              PrimitiveTypeDescriptorMapper.toOutput(PrimitiveType.IPv4)
                  .getValidation()
                  .getRules()
                  .getFirst()
                  .getPattern())
          .isNull();
      assertThat(
              PrimitiveTypeDescriptorMapper.toOutput(PrimitiveType.Port)
                  .getValidation()
                  .getRules()
                  .getFirst()
                  .getPattern())
          .isEqualTo(FormatRuleKind.PORT.pattern());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PrimitiveType.class)
    @DisplayName("should expose an error message key on every rule")
    void given_anyRule_should_exposeAnErrorMessageKey(PrimitiveType type) {
      // Assert
      assertThat(PrimitiveTypeDescriptorMapper.toOutput(type).getValidation().getRules())
          .allSatisfy(rule -> assertThat(rule.getErrorMessageKey()).isNotBlank());
    }
  }
}
