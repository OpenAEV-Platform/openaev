package io.openaev.utils.object;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ObjectDiffUtilsTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Mock private ObjectNormalizationUtils objectNormalizationUtils;

  private ObjectDiffUtils objectDiffUtils;

  @BeforeEach
  void setUp() {
    objectDiffUtils = new ObjectDiffUtils(objectMapper, objectNormalizationUtils);
    when(objectNormalizationUtils.normalize(any(JsonNode.class), anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void given_nullInputField_should_skipField() throws Exception {
    // Arrange
    JsonNode oldSnapshot = objectMapper.readTree("{\"name\":\"old\"}");
    JsonNode newInput = objectMapper.readTree("{\"name\":null}");

    // Act
    ObjectDiffUtils.DiffResult diff = objectDiffUtils.computeDiff(oldSnapshot, newInput, "default");

    // Assert
    assertNull(diff.newValues());
    assertNull(diff.oldValues());
  }

  @Test
  void given_metadataFieldType_should_skipField() throws Exception {
    // Arrange
    JsonNode oldSnapshot = objectMapper.readTree("{}");
    JsonNode newInput = objectMapper.readTree("{\"type\":\"inject\"}");

    // Act
    ObjectDiffUtils.DiffResult diff = objectDiffUtils.computeDiff(oldSnapshot, newInput, "default");

    // Assert
    assertNull(diff.newValues());
    assertNull(diff.oldValues());
  }

  @Test
  void given_relationObjectAndSameId_should_notCreateDiff() throws Exception {
    // Arrange
    JsonNode oldSnapshot =
        objectMapper.readTree("{\"inject_injector\":{\"injector_id\":\"abc\",\"name\":\"x\"}}");
    JsonNode newInput = objectMapper.readTree("{\"inject_injector\":\"abc\"}");

    // Act
    ObjectDiffUtils.DiffResult diff = objectDiffUtils.computeDiff(oldSnapshot, newInput, "default");

    // Assert
    assertNull(diff.newValues());
    assertNull(diff.oldValues());
  }

  @Test
  void given_relationObjectAndDifferentId_should_flattenOldIdAndCreateDiff() throws Exception {
    // Arrange
    JsonNode oldSnapshot =
        objectMapper.readTree("{\"inject_injector\":{\"injector_id\":\"abc\",\"name\":\"x\"}}");
    JsonNode newInput = objectMapper.readTree("{\"inject_injector\":\"xyz\"}");

    // Act
    ObjectDiffUtils.DiffResult diff = objectDiffUtils.computeDiff(oldSnapshot, newInput, "default");

    // Assert
    assertNotNull(diff.newValues());
    assertNotNull(diff.oldValues());
    assertEquals("xyz", diff.newValues().get("inject_injector").asText());
    assertEquals("abc", diff.oldValues().get("inject_injector").asText());
  }

  @Test
  void given_nestedObjectChange_should_returnNestedDiffOnly() throws Exception {
    // Arrange
    JsonNode oldSnapshot = objectMapper.readTree("{\"spec\":{\"name\":\"old\",\"enabled\":true}}");
    JsonNode newInput = objectMapper.readTree("{\"spec\":{\"name\":\"new\",\"enabled\":true}}");

    // Act
    ObjectDiffUtils.DiffResult diff = objectDiffUtils.computeDiff(oldSnapshot, newInput, "default");

    // Assert
    assertNotNull(diff.newValues());
    assertEquals("new", diff.newValues().get("spec").get("name").asText());
    assertEquals("old", diff.oldValues().get("spec").get("name").asText());
    assertNull(diff.newValues().get("spec").get("enabled"));
  }

  @Test
  void given_numericEquivalentValues_should_notCreateDiff() throws Exception {
    // Arrange
    JsonNode oldSnapshot = objectMapper.readTree("{\"score\":100}");
    JsonNode newInput = objectMapper.readTree("{\"score\":100.0}");

    // Act
    ObjectDiffUtils.DiffResult diff = objectDiffUtils.computeDiff(oldSnapshot, newInput, "default");

    // Assert
    assertNull(diff.newValues());
    assertNull(diff.oldValues());
  }

  @Test
  void given_customEntityType_should_forwardToNormalizationLayer() throws Exception {
    // Arrange
    JsonNode oldSnapshot = objectMapper.readTree("{\"name\":\"old\"}");
    JsonNode newInput = objectMapper.readTree("{\"name\":\"new\"}");

    // Act
    ObjectDiffUtils.DiffResult diff =
        objectDiffUtils.computeDiff(oldSnapshot, newInput, "audit_event");

    // Assert
    assertNotNull(diff.newValues());
    assertTrue(diff.newValues().has("name"));
    verify(objectNormalizationUtils).normalize(oldSnapshot, "audit_event");
    verify(objectNormalizationUtils).normalize(newInput, "audit_event");
  }
}
