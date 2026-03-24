package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SidOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final SidOutputProcessor processor = new SidOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when sid is present")
  void shouldReturnTrueWhenSidPresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"sid\": \"S-1-5-21-3623811015-3361044348-30300820-1013\", \"host\": \"dc01\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when sid is missing")
  void shouldReturnFalseWhenSidMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"dc01\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when sid is null")
  void shouldReturnFalseWhenSidNull() throws Exception {
    JsonNode node = objectMapper.readTree("{\"sid\": null}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return sid as finding value")
  void shouldReturnSidAsFindingValue() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"sid\": \"S-1-5-21-3623811015-3361044348-30300820-1013\", \"host\": \"dc01\"}");
    String result = processor.toFindingValue(node);
    assertEquals("S-1-5-21-3623811015-3361044348-30300820-1013", result);
  }

  @Test
  @DisplayName("should return empty string when sid field is missing for finding value")
  void shouldReturnEmptyStringWhenSidMissingForFindingValue() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"dc01\"}");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"sid\": \"S-1-5-21-3623811015-3361044348-30300820-1013\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is a string")
  void shouldReturnSingleAssetIdWhenAssetIdIsString() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"sid\": \"S-1-5-21-3623811015-3361044348-30300820-1013\", \"asset_id\": \"asset42\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset42"), result);
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is an array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"sid\": \"S-1-5-21-3623811015-3361044348-30300820-1013\", \"asset_id\": [\"a1\", \"a2\"]}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("a1", "a2"), result);
  }
}
