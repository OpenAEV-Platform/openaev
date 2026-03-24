package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordPolicyOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final PasswordPolicyOutputProcessor processor =
      new PasswordPolicyOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when key and value are present")
  void shouldReturnTrueWhenKeyAndValuePresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"key\": \"Minimum password length\", \"value\": \"8\", \"host\": \"dc01\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when key is missing")
  void shouldReturnFalseWhenKeyMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"value\": \"8\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when value is missing")
  void shouldReturnFalseWhenValueMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"key\": \"Minimum password length\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when key is null")
  void shouldReturnFalseWhenKeyNull() throws Exception {
    JsonNode node = objectMapper.readTree("{\"key\": null, \"value\": \"8\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when value is null")
  void shouldReturnFalseWhenValueNull() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"key\": \"Minimum password length\", \"value\": null}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return key: value as finding value")
  void shouldReturnKeyColonValueAsFindingValue() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"key\": \"Minimum password length\", \"value\": \"8\"}");
    String result = processor.toFindingValue(node);
    assertEquals("Minimum password length: 8", result);
  }

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"key\": \"Minimum password length\", \"value\": \"8\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is a string")
  void shouldReturnSingleAssetIdWhenAssetIdIsString() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"key\": \"Minimum password length\", \"value\": \"8\", \"asset_id\": \"asset42\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset42"), result);
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is an array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"key\": \"Minimum password length\", \"value\": \"8\", \"asset_id\": [\"a1\", \"a2\"]}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("a1", "a2"), result);
  }
}
