package io.openaev.utils.object;

import static io.openaev.utils.object.ObjectNormalizationUtils.isEffectivelyEmpty;
import static io.openaev.utils.object.ObjectNormalizationUtils.isInsignificantValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openaev.utils.SystemLoadGuardUtils;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ObjectNormalizationUtils")
class ObjectNormalizationUtilsTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int MAX_EVENT_SIZE = 32_768;
  private static final int MAX_STRING_BYTES = 1_024;
  private static final int TRUNCATION_PREVIEW_BYTES = 2_048;
  private static final String TRUNCATED_SUFFIX = "...[TRUNCATED]";
  private static final String REDACTED_VALUE = "[REDACTED]";

  @Mock private SystemLoadGuardUtils systemLoadGuardUtils;

  @Mock private ObjectNormalizationPolicy objectNormalizationPolicy;

  private ObjectNormalizationUtils normalizationUtils;

  @BeforeEach
  void setUp() {
    normalizationUtils =
        new ObjectNormalizationUtils(
            OBJECT_MAPPER, systemLoadGuardUtils, objectNormalizationPolicy);
    lenient().when(objectNormalizationPolicy.skipAllNormalization()).thenReturn(false);
    lenient().when(objectNormalizationPolicy.skipOnHighLoad()).thenReturn(false);
    lenient().when(objectNormalizationPolicy.maxEventSizeBytes()).thenReturn(MAX_EVENT_SIZE);
    lenient().when(objectNormalizationPolicy.maxStringBytes()).thenReturn(MAX_STRING_BYTES);
    lenient()
        .when(objectNormalizationPolicy.truncationPreviewBytes())
        .thenReturn(TRUNCATION_PREVIEW_BYTES);
    lenient().when(objectNormalizationPolicy.truncatedSuffix()).thenReturn(TRUNCATED_SUFFIX);
    lenient().when(objectNormalizationPolicy.redactedValue()).thenReturn(REDACTED_VALUE);
    lenient().when(objectNormalizationPolicy.allowlistForEntity(anyString())).thenReturn(null);
    lenient().when(objectNormalizationPolicy.denylistForEntity(anyString())).thenReturn(Set.of());
    lenient().when(objectNormalizationPolicy.isGloballyDeniedField(anyString())).thenReturn(false);
    lenient()
        .when(objectNormalizationPolicy.normalizeEntityType(anyString()))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  // -- isEffectivelyEmpty --

  @Nested
  @DisplayName("isEffectivelyEmpty")
  class IsEffectivelyEmpty {

    @Test
    void given_null_should_returnTrue() {
      // Act & Assert
      assertThat(isEffectivelyEmpty(null)).isTrue();
    }

    @Test
    void given_nullNode_should_returnTrue() {
      // Act & Assert
      assertThat(isEffectivelyEmpty(NullNode.getInstance())).isTrue();
    }

    @Test
    void given_emptyArray_should_returnTrue() {
      // Act & Assert
      assertThat(isEffectivelyEmpty(OBJECT_MAPPER.createArrayNode())).isTrue();
    }

    @Test
    void given_emptyObject_should_returnTrue() {
      // Act & Assert
      assertThat(isEffectivelyEmpty(OBJECT_MAPPER.createObjectNode())).isTrue();
    }

    @Test
    void given_nonEmptyTextNode_should_returnFalse() {
      // Act & Assert
      assertThat(isEffectivelyEmpty(JsonNodeFactory.instance.textNode("hello"))).isFalse();
    }

    @Test
    void given_nonEmptyArray_should_returnFalse() {
      // Arrange
      var arr = OBJECT_MAPPER.createArrayNode();
      arr.add("item");

      // Act & Assert
      assertThat(isEffectivelyEmpty(arr)).isFalse();
    }

    @Test
    void given_nonEmptyObject_should_returnFalse() {
      // Arrange
      var obj = OBJECT_MAPPER.createObjectNode();
      obj.put("key", "value");

      // Act & Assert
      assertThat(isEffectivelyEmpty(obj)).isFalse();
    }
  }

  // -- isInsignificantValue --

  @Nested
  @DisplayName("isInsignificantValue")
  class IsInsignificantValue {

    @Test
    void given_nullNode_should_returnTrue() {
      // Act & Assert
      assertThat(isInsignificantValue(NullNode.getInstance())).isTrue();
    }

    @Test
    void given_emptyString_should_returnTrue() {
      // Act & Assert
      assertThat(isInsignificantValue(JsonNodeFactory.instance.textNode(""))).isTrue();
    }

    @Test
    void given_booleanFalse_should_returnTrue() {
      // Act & Assert
      assertThat(isInsignificantValue(JsonNodeFactory.instance.booleanNode(false))).isTrue();
    }

    @Test
    void given_booleanTrue_should_returnFalse() {
      // Act & Assert
      assertThat(isInsignificantValue(JsonNodeFactory.instance.booleanNode(true))).isFalse();
    }

    @Test
    void given_nonBlankString_should_returnFalse() {
      // Act & Assert
      assertThat(isInsignificantValue(JsonNodeFactory.instance.textNode("hello"))).isFalse();
    }

    @Test
    void given_number_should_returnFalse() {
      // Act & Assert
      assertThat(isInsignificantValue(JsonNodeFactory.instance.numberNode(42))).isFalse();
    }

    @Test
    void given_emptyObject_should_returnTrue() {
      // Act & Assert
      assertThat(isInsignificantValue(OBJECT_MAPPER.createObjectNode())).isTrue();
    }

    @Test
    void given_emptyArray_should_returnTrue() {
      // Act & Assert
      assertThat(isInsignificantValue(OBJECT_MAPPER.createArrayNode())).isTrue();
    }
  }

  // -- shouldSkipAllNormalization --

  @Nested
  @DisplayName("shouldSkipAllNormalization")
  class ShouldSkipAllNormalization {

    @Test
    void given_policySkipAll_should_returnTrue() {
      // Arrange
      when(objectNormalizationPolicy.skipAllNormalization()).thenReturn(true);

      // Act & Assert
      assertThat(normalizationUtils.shouldSkipAllNormalization()).isTrue();
    }

    @Test
    void given_policyNoSkip_should_returnFalse() {
      // Arrange
      when(objectNormalizationPolicy.skipAllNormalization()).thenReturn(false);

      // Act & Assert
      assertThat(normalizationUtils.shouldSkipAllNormalization()).isFalse();
    }
  }

  // -- shouldSkipFullNormalization --

  @Nested
  @DisplayName("shouldSkipFullNormalization")
  class ShouldSkipFullNormalization {

    @Test
    void given_skipOnHighLoadDisabled_should_returnFalse() {
      // Arrange
      when(objectNormalizationPolicy.skipOnHighLoad()).thenReturn(false);

      // Act & Assert
      assertThat(normalizationUtils.shouldSkipFullNormalization()).isFalse();
    }

    @Test
    void given_skipOnHighLoad_and_heapHigh_should_returnTrue() {
      // Arrange
      when(objectNormalizationPolicy.skipOnHighLoad()).thenReturn(true);
      when(objectNormalizationPolicy.maxHeapUsageRatio()).thenReturn(0.9);
      when(systemLoadGuardUtils.isHeapUsageHigh(0.9)).thenReturn(true);

      // Act & Assert
      assertThat(normalizationUtils.shouldSkipFullNormalization()).isTrue();
    }

    @Test
    void given_skipOnHighLoad_and_cpuHigh_should_returnTrue() {
      // Arrange
      when(objectNormalizationPolicy.skipOnHighLoad()).thenReturn(true);
      when(objectNormalizationPolicy.maxHeapUsageRatio()).thenReturn(0.9);
      when(objectNormalizationPolicy.maxProcessCpuLoad()).thenReturn(0.9);
      when(systemLoadGuardUtils.isHeapUsageHigh(0.9)).thenReturn(false);
      when(systemLoadGuardUtils.isProcessCpuLoadHigh(0.9)).thenReturn(true);

      // Act & Assert
      assertThat(normalizationUtils.shouldSkipFullNormalization()).isTrue();
    }

    @Test
    void given_skipOnHighLoad_and_loadNormal_should_returnFalse() {
      // Arrange
      when(objectNormalizationPolicy.skipOnHighLoad()).thenReturn(true);
      when(objectNormalizationPolicy.maxHeapUsageRatio()).thenReturn(0.9);
      when(objectNormalizationPolicy.maxProcessCpuLoad()).thenReturn(0.9);
      when(systemLoadGuardUtils.isHeapUsageHigh(0.9)).thenReturn(false);
      when(systemLoadGuardUtils.isProcessCpuLoadHigh(0.9)).thenReturn(false);

      // Act & Assert
      assertThat(normalizationUtils.shouldSkipFullNormalization()).isFalse();
    }
  }

  // -- normalize --

  @Nested
  @DisplayName("normalize")
  class Normalize {

    @Test
    void given_skipAllNormalization_should_returnOriginalNode() {
      // Arrange
      when(objectNormalizationPolicy.skipAllNormalization()).thenReturn(true);
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "test");

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result).isSameAs(node);
    }

    @Test
    void given_nullInput_should_returnNullNode() {
      // Act
      JsonNode result = normalizationUtils.normalize(null);

      // Assert
      assertThat(result.isNull()).isTrue();
    }

    @Test
    void given_blankTextField_should_normalizeToNull() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "hello");
      node.put("description", "   ");

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert — blank text is normalised to null then stripped as insignificant
      assertThat(result.path("name").asText()).isEqualTo("hello");
      assertThat(result.has("description")).isFalse();
    }

    @Test
    void given_falseBooleanField_should_stripFromOutput() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "hello");
      node.put("active", false);

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result.path("name").asText()).isEqualTo("hello");
      assertThat(result.has("active")).isFalse();
    }

    @Test
    void given_trueBooleanField_should_keepInOutput() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("active", true);

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result.path("active").asBoolean()).isTrue();
    }

    @Test
    void given_diffSkipField_should_stripFromOutput() {
      // Arrange — "type" is in DIFF_SKIP_FIELDS
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("type", "inject");
      node.put("name", "test");

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result.has("type")).isFalse();
      assertThat(result.path("name").asText()).isEqualTo("test");
    }

    @Test
    void given_numberWithTrailingDecimalZeros_should_stripTrailingZeros() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("value", new BigDecimal("1.10"));

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result.path("value").decimalValue().toPlainString()).isEqualTo("1.1");
    }

    @Test
    void given_wholeNumberWithDecimalZero_should_returnIntegerRepresentation() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("value", new BigDecimal("2.0"));

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result.path("value").isIntegralNumber()).isTrue();
      assertThat(result.path("value").asInt()).isEqualTo(2);
    }

    @Test
    void given_emptyArrayField_should_normalizeToNull() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "hello");
      node.putArray("tags");

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert — empty array normalised to null then stripped as insignificant
      assertThat(result.path("name").asText()).isEqualTo("hello");
      assertThat(result.has("tags")).isFalse();
    }

    @Test
    void given_allInsignificantFields_should_returnNullNode() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("active", false);
      node.put("description", "");

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result.isNull()).isTrue();
    }

    @Test
    void given_globallyDeniedField_should_redactValue() {
      // Arrange
      when(objectNormalizationPolicy.isGloballyDeniedField("password")).thenReturn(true);
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("password", "secret123");
      node.put("name", "test");

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result.path("password").asText()).isEqualTo(REDACTED_VALUE);
      assertThat(result.path("name").asText()).isEqualTo("test");
    }

    @Test
    void given_entityDenylistField_should_redactValue() {
      // Arrange
      when(objectNormalizationPolicy.denylistForEntity("user")).thenReturn(Set.of("access_token"));
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("access_token", "my-token");
      node.put("user_name", "alice");

      // Act
      JsonNode result = normalizationUtils.normalize(node, "user");

      // Assert
      assertThat(result.path("access_token").asText()).isEqualTo(REDACTED_VALUE);
      assertThat(result.path("user_name").asText()).isEqualTo("alice");
    }

    @Test
    void given_entityAllowlist_should_keepOnlyAllowlistedFields() {
      // Arrange
      when(objectNormalizationPolicy.allowlistForEntity("audit_event"))
          .thenReturn(Set.of("event_type", "entity_id"));
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("event_type", "CREATE");
      node.put("entity_id", "abc-123");
      node.put("extra_field", "should-be-removed");

      // Act
      JsonNode result = normalizationUtils.normalize(node, "audit_event");

      // Assert
      assertThat(result.path("event_type").asText()).isEqualTo("CREATE");
      assertThat(result.path("entity_id").asText()).isEqualTo("abc-123");
      assertThat(result.has("extra_field")).isFalse();
    }

    @Test
    void given_nestedObject_should_normalizeRecursively() {
      // Arrange
      ObjectNode inner = OBJECT_MAPPER.createObjectNode();
      inner.put("label", "nested");
      inner.put("flag", false);
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.set("inner", inner);

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result.path("inner").path("label").asText()).isEqualTo("nested");
      assertThat(result.path("inner").has("flag")).isFalse();
    }
  }

  // -- normalize - size enforcement --

  @Nested
  @DisplayName("normalize - size enforcement")
  class NormalizeSizeEnforcement {

    @Test
    void given_nodeBelowMaxSize_should_returnNormalizedNodeUnchanged() {
      // Arrange
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "small");

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result.path("name").asText()).isEqualTo("small");
      assertThat(result.has("truncated")).isFalse();
    }

    @Test
    void given_oversizedNodeWithLongStrings_should_truncateStringValues() {
      // Arrange — small max size forces string truncation
      when(objectNormalizationPolicy.maxEventSizeBytes()).thenReturn(60);
      when(objectNormalizationPolicy.maxStringBytes()).thenReturn(10);
      when(objectNormalizationPolicy.truncatedSuffix()).thenReturn("...");
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "a".repeat(200));

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert — original 200-char string is not present
      assertThat(result.toString()).doesNotContain("a".repeat(200));
    }

    @Test
    void given_extremelyOversizedNode_should_returnTruncatedEnvelope() {
      // Arrange — tiny max size forces the envelope fallback
      when(objectNormalizationPolicy.maxEventSizeBytes()).thenReturn(10);
      when(objectNormalizationPolicy.maxStringBytes()).thenReturn(5);
      when(objectNormalizationPolicy.truncatedSuffix()).thenReturn("...");
      when(objectNormalizationPolicy.truncationPreviewBytes()).thenReturn(5);
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("field1", "x".repeat(500));
      node.put("field2", "y".repeat(500));

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert
      assertThat(result.path("truncated").asBoolean()).isTrue();
      assertThat(result.has("original_size_bytes")).isTrue();
      assertThat(result.has("max_size_bytes")).isTrue();
      assertThat(result.path("max_size_bytes").asInt()).isEqualTo(10);
    }

    @Test
    void given_sizeEnforcementDisabled_should_skipSizeCheck() {
      // Arrange — maxEventSizeBytes <= 0 disables enforcement
      when(objectNormalizationPolicy.maxEventSizeBytes()).thenReturn(0);
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "x".repeat(100_000));

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert — no truncation envelope
      assertThat(result.has("truncated")).isFalse();
    }
  }

  // -- normalize - high-load mode --

  @Nested
  @DisplayName("normalize - high-load mode")
  class NormalizeHighLoad {

    @Test
    void given_highHeapLoad_should_skipInsignificantValueStripping() {
      // Arrange — high load: schema rules + size enforcement only, no strip step
      when(objectNormalizationPolicy.skipOnHighLoad()).thenReturn(true);
      when(objectNormalizationPolicy.maxHeapUsageRatio()).thenReturn(0.9);
      when(systemLoadGuardUtils.isHeapUsageHigh(0.9)).thenReturn(true);
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "test");
      node.put("active", false); // would normally be stripped

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert — stripInsignificantValues is skipped → false boolean is preserved
      assertThat(result.has("active")).isTrue();
      assertThat(result.path("active").asBoolean()).isFalse();
    }

    @Test
    void given_highCpuLoad_should_applySchemaRulesOnly() {
      // Arrange
      when(objectNormalizationPolicy.skipOnHighLoad()).thenReturn(true);
      when(objectNormalizationPolicy.maxHeapUsageRatio()).thenReturn(0.9);
      when(objectNormalizationPolicy.maxProcessCpuLoad()).thenReturn(0.9);
      when(systemLoadGuardUtils.isHeapUsageHigh(0.9)).thenReturn(false);
      when(systemLoadGuardUtils.isProcessCpuLoadHigh(0.9)).thenReturn(true);
      ObjectNode node = OBJECT_MAPPER.createObjectNode();
      node.put("name", "test");
      node.put("active", false);

      // Act
      JsonNode result = normalizationUtils.normalize(node);

      // Assert — value normalization / stripping skipped
      assertThat(result.has("active")).isTrue();
    }
  }
}
