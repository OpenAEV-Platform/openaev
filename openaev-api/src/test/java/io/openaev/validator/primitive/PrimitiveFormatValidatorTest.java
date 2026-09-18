package io.openaev.validator.primitive;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.database.model.ConditionType;
import io.openaev.database.model.PrimitiveType;
import io.openaev.utils.SensitiveValueMaskingUtils;
import io.openaev.validator.primitive.PrimitiveTypePolicy.FormatEnforcement;
import io.openaev.validator.primitive.PrimitiveTypePolicy.TypePolicy;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Primitive format validation")
class PrimitiveFormatValidatorTest {

  private static final String VECTORS_FILE = "/primitive-format-vectors.json";

  private static JsonNode vectors() {
    try (InputStream stream =
        PrimitiveFormatValidatorTest.class.getResourceAsStream(VECTORS_FILE)) {
      assertThat(stream)
          .as("shared vectors file %s must be on the test classpath", VECTORS_FILE)
          .isNotNull();
      return new ObjectMapper().readTree(stream);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to read " + VECTORS_FILE, e);
    }
  }

  private static Stream<Arguments> vectorsOf(String section, boolean expectedAccepted) {
    JsonNode entries = vectors().get(section);
    List<Arguments> arguments = new ArrayList<>();
    entries
        .fieldNames()
        .forEachRemaining(
            name -> {
              if (name.startsWith("_")) {
                return;
              }
              JsonNode values = entries.get(name).get(expectedAccepted ? "valid" : "invalid");
              if (values != null) {
                values.forEach(value -> arguments.add(Arguments.of(name, value.asText())));
              }
            });
    return arguments.stream();
  }

  static Stream<Arguments> acceptedKindVectors() {
    return vectorsOf("kinds", true);
  }

  static Stream<Arguments> rejectedKindVectors() {
    return vectorsOf("kinds", false);
  }

  static Stream<Arguments> acceptedTypeVectors() {
    return vectorsOf("types", true);
  }

  static Stream<Arguments> rejectedTypeVectors() {
    return vectorsOf("types", false);
  }

  @Nested
  @DisplayName("Policy coverage")
  class PolicyCoverage {

    @ParameterizedTest(name = "{0}")
    @EnumSource(PrimitiveType.class)
    @DisplayName("should resolve a policy for every primitive type")
    void given_anyPrimitiveType_should_resolveAPolicy(PrimitiveType type) {
      // Act / Assert - the permissive default covers any type not narrowed explicitly
      assertThat(PrimitiveTypePolicy.of(type)).isNotNull();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PrimitiveType.class)
    @DisplayName("should only claim runtime enforcement on a type that actually has a rule")
    void given_anyPolicy_should_beInternallyConsistent(PrimitiveType type) {
      // Arrange
      TypePolicy policy = PrimitiveTypePolicy.of(type);

      // Assert - enforcement is meaningless without a rule, and must not read as a stricter policy
      if (!policy.hasRules()) {
        assertThat(policy.enforcement()).isEqualTo(FormatEnforcement.INPUT_ONLY);
      }
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PrimitiveType.class)
    @DisplayName("should never mark a non-numeric type as numeric-only comparable")
    void given_numericType_should_alsoCarryANumericRule(PrimitiveType type) {
      // Arrange
      TypePolicy policy = PrimitiveTypePolicy.of(type);

      // Assert - the UI offers >, <, >= and <= on a numeric type, and the backend parses the
      // operand with Double.parseDouble: a numeric type that accepts non-numeric values would
      // offer an operator that can only ever return false
      if (policy.isNumericValue()) {
        assertThat(policy.hasRules()).isTrue();
        assertThat(policy.isCaseSensitivity()).isFalse();
      }
    }

    @Test
    @DisplayName("should enforce at runtime only the types that were already validated at runtime")
    void given_policies_should_restrictRuntimeEnforcementToHistoricalTypes() {
      // Arrange - promoting a type here changes ingestion behaviour and must stay deliberate
      List<PrimitiveType> runtimeEnforced =
          PrimitiveTypePolicy.all().entrySet().stream()
              .filter(
                  entry -> entry.getValue().enforcement() == FormatEnforcement.RUNTIME_AND_INPUT)
              .map(Map.Entry::getKey)
              .toList();

      // Assert
      assertThat(runtimeEnforced)
          .containsExactlyInAnyOrder(
              PrimitiveType.IPv4,
              PrimitiveType.IPv6,
              PrimitiveType.IpSubnet,
              PrimitiveType.Domain,
              PrimitiveType.Port,
              PrimitiveType.Number);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PrimitiveType.class)
    @DisplayName("should never constrain the format of a value that is masked for display")
    void given_maskedType_should_carryNoFormatRule(PrimitiveType type) {
      // Assert - a masked value is echoed back by the client in its masked form ("d41***27e"), so
      // a rule here would reject a stored value that is perfectly valid. Hash belongs to this set:
      // it carries credential material (NTLM, LM:NT pairs, Kerberos roasting blobs), not a digest.
      if (SensitiveValueMaskingUtils.isSensitive(type)) {
        assertThat(PrimitiveTypePolicy.of(type).hasRules())
            .as("%s is masked for display and must not constrain its format", type)
            .isFalse();
      }
    }

    @Test
    @DisplayName("should expose an error message key for every rule")
    void given_anyRule_should_exposeAnErrorMessageKey() {
      // Assert
      assertThat(Arrays.asList(FormatRuleKind.values()))
          .allSatisfy(
              kind ->
                  assertThat(kind.errorMessageKey())
                      .as("rule %s must carry a key the frontend can translate", kind)
                      .isNotBlank());
    }
  }

  @Nested
  @DisplayName("Format rule kinds")
  class FormatRuleKinds {

    @ParameterizedTest(name = "{0} accepts {1}")
    @MethodSource("io.openaev.validator.primitive.PrimitiveFormatValidatorTest#acceptedKindVectors")
    @DisplayName("should accept the shared valid vectors")
    void given_validVector_should_accept(String kind, String value) {
      // Act / Assert
      assertThat(FormatRuleKind.valueOf(kind).matches(value)).isTrue();
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("io.openaev.validator.primitive.PrimitiveFormatValidatorTest#rejectedKindVectors")
    @DisplayName("should reject the shared invalid vectors")
    void given_invalidVector_should_reject(String kind, String value) {
      // Act / Assert
      assertThat(FormatRuleKind.valueOf(kind).matches(value)).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(FormatRuleKind.class)
    @DisplayName("should match the pattern frozen in the shared vectors")
    void given_aSerializableKind_should_matchTheSharedPattern(FormatRuleKind kind) {
      // Arrange - the frontend compiles this very string, so a Java-only construct slipping into
      // the pattern must fail here rather than silently diverge at runtime.
      JsonNode entry = vectors().get("kinds").get(kind.name());

      // Assert
      assertThat(entry).as("every kind must have shared vectors").isNotNull();
      assertThat(entry.hasNonNull("pattern")).isEqualTo(kind.isSerializable());
      if (kind.isSerializable()) {
        assertThat(kind.pattern()).isEqualTo(entry.get("pattern").asText());
      }
    }

    @Test
    @DisplayName("should expose a pattern only for the serializable kinds")
    void given_anyKind_should_exposePatternOnlyWhenSerializable() {
      // Assert
      assertThat(
              Arrays.stream(FormatRuleKind.values())
                  .filter(kind -> !kind.isSerializable())
                  .toList())
          .as("named kinds must be implemented separately by the frontend")
          .containsExactlyInAnyOrder(
              FormatRuleKind.IPV4,
              FormatRuleKind.IPV6,
              FormatRuleKind.IPV4_CIDR,
              FormatRuleKind.IPV6_CIDR,
              FormatRuleKind.EMAIL);
    }

    @Test
    @DisplayName("should reject null and blank values whatever the rule")
    void given_nullOrBlankValue_should_reject() {
      // Assert
      assertThat(Arrays.asList(FormatRuleKind.values()))
          .allSatisfy(
              kind -> {
                assertThat(kind.matches(null)).isFalse();
                assertThat(kind.matches("")).isFalse();
                assertThat(kind.matches("   ")).isFalse();
              });
    }
  }

  @Nested
  @DisplayName("Type level validation")
  class TypeLevelValidation {

    @ParameterizedTest(name = "{0} accepts {1}")
    @MethodSource("io.openaev.validator.primitive.PrimitiveFormatValidatorTest#acceptedTypeVectors")
    @DisplayName("should accept a value matching any of the type rules")
    void given_valueMatchingOneRule_should_accept(String type, String value) {
      // Act / Assert
      assertThat(
              PrimitiveFormatValidator.isAccepted(
                  PrimitiveType.valueOf(type), ConditionType.EQ, value))
          .isTrue();
    }

    @ParameterizedTest(name = "{0} rejects {1}")
    @MethodSource("io.openaev.validator.primitive.PrimitiveFormatValidatorTest#rejectedTypeVectors")
    @DisplayName("should reject a value matching none of the type rules")
    void given_valueMatchingNoRule_should_reject(String type, String value) {
      // Act / Assert
      assertThat(
              PrimitiveFormatValidator.isAccepted(
                  PrimitiveType.valueOf(type), ConditionType.EQ, value))
          .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = PrimitiveType.class,
        names = {"Text", "Username", "Password", "FilePath", "AssetId", "Severity", "Document"})
    @DisplayName("should accept anything for a type without format rule")
    void given_typeWithoutRule_should_acceptAnything(PrimitiveType type) {
      // Act / Assert
      assertThat(
              PrimitiveFormatValidator.isAccepted(type, ConditionType.EQ, "literally anything ###"))
          .isTrue();
    }

    @Test
    @DisplayName("should accept any value when the type is unknown")
    void given_nullType_should_accept() {
      // Act / Assert
      assertThat(PrimitiveFormatValidator.isAccepted(null, ConditionType.EQ, "anything")).isTrue();
    }
  }

  @Nested
  @DisplayName("Operator awareness")
  class OperatorAwareness {

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = ConditionType.class,
        names = {"IS_NULL", "IS_NOT_NULL"})
    @DisplayName("should not validate a format when the operator carries no value")
    void given_unaryOperator_should_skipFormat(ConditionType operator) {
      // Act / Assert
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.IPv4, operator, null)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = ConditionType.class,
        names = {"IN", "NIN"})
    @DisplayName("should accept a partial value for list operators matching on substrings")
    void given_listOperator_should_acceptPartialValue(ConditionType operator) {
      // Act / Assert - ConditionUtils splits on commas then matches with contains
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.IPv4, operator, "10.0."))
          .isTrue();
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.Domain, operator, ".corp"))
          .isTrue();
    }

    @Test
    @DisplayName("should keep the default text/IN condition valid")
    void given_defaultTextInCondition_should_accept() {
      // Act / Assert - createEmptyCondition() initialises every new condition this way
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.Text, ConditionType.IN, ""))
          .isTrue();
      assertThat(
              PrimitiveFormatValidator.isAccepted(PrimitiveType.Text, ConditionType.IN, "foo,bar"))
          .isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = ConditionType.class,
        names = {"GT", "GTE", "LT", "LTE"})
    @DisplayName("should require a number for comparison operators whatever the type")
    void given_comparisonOperator_should_requireANumber(ConditionType operator) {
      // Act / Assert
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.Text, operator, "42")).isTrue();
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.Text, operator, "high"))
          .isFalse();
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.Severity, operator, "medium"))
          .isFalse();
    }

    @Test
    @DisplayName("should let comparison operators override the type own format")
    void given_comparisonOperatorOnFormattedType_should_useNumericRule() {
      // Act / Assert - a Domain compared with GT is nonsense, but the rule applied is the numeric
      // one, not the domain one
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.Domain, ConditionType.GT, "42"))
          .isTrue();
      assertThat(
              PrimitiveFormatValidator.isAccepted(
                  PrimitiveType.Domain, ConditionType.GT, "example.com"))
          .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = ConditionType.class,
        names = {"EQ", "NEQ"})
    @DisplayName("should apply the type format for equality operators")
    void given_equalityOperator_should_applyTypeFormat(ConditionType operator) {
      // Act / Assert
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.IPv4, operator, "10.0.0.1"))
          .isTrue();
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.IPv4, operator, "10.0."))
          .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(
        value = ConditionType.class,
        names = {"AND", "OR", "MAPPER", "DEPEND_ON"})
    @DisplayName("should not validate a format for operators that are not value comparisons")
    void given_nonComparisonOperator_should_skipFormat(ConditionType operator) {
      // Act / Assert
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.IPv4, operator, "not an ip"))
          .isTrue();
    }
  }

  @Nested
  @DisplayName("Runtime enforcement")
  class RuntimeEnforcement {

    @Test
    @DisplayName("should enforce the format of the historically validated types")
    void given_runtimeEnforcedType_should_applyFormat() {
      // Act / Assert
      assertThat(PrimitiveFormatValidator.isAcceptedAtRuntime(PrimitiveType.IPv4, "10.0.0.1"))
          .isTrue();
      assertThat(PrimitiveFormatValidator.isAcceptedAtRuntime(PrimitiveType.IPv4, "not an ip"))
          .isFalse();
      assertThat(PrimitiveFormatValidator.isAcceptedAtRuntime(PrimitiveType.Port, "65536"))
          .isFalse();
    }

    @Test
    @DisplayName("should not reject values emitted by injectors for the newly ruled types")
    void given_inputOnlyType_should_acceptAnyRuntimeValue() {
      // Act / Assert - tightening ingestion would silently discard third party injector data
      assertThat(PrimitiveFormatValidator.isAcceptedAtRuntime(PrimitiveType.CVE, "not-a-cve"))
          .isTrue();
      assertThat(PrimitiveFormatValidator.isAcceptedAtRuntime(PrimitiveType.Email, "not-an-email"))
          .isTrue();
      assertThat(PrimitiveFormatValidator.isAcceptedAtRuntime(PrimitiveType.SID, "not-a-sid"))
          .isTrue();
      // ... while the very same values are rejected when a user types them in a condition
      assertThat(PrimitiveFormatValidator.isAccepted(PrimitiveType.CVE, ConditionType.EQ, "nope"))
          .isFalse();
    }
  }
}
