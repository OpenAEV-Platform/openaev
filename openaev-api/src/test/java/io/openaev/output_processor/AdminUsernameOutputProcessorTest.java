package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminUsernameOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final AdminUsernameOutputProcessor processor =
      new AdminUsernameOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when username is present")
  void shouldReturnTrueWhenUsernamePresent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"Administrator\", \"host\": \"dc01\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when username is missing")
  void shouldReturnFalseWhenUsernameMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"dc01\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when username is null")
  void shouldReturnFalseWhenUsernameNull() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": null}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return username as finding value")
  void shouldReturnUsernameAsFindingValue() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"Administrator\", \"host\": \"dc01\"}");
    String result = processor.toFindingValue(node);
    assertEquals("Administrator", result);
  }

  @Test
  @DisplayName("should return empty string when username field is missing for finding value")
  void shouldReturnEmptyStringWhenUsernameMissingForFindingValue() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"dc01\"}");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"Administrator\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is a string")
  void shouldReturnSingleAssetIdWhenAssetIdIsString() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"username\": \"Administrator\", \"asset_id\": \"asset42\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset42"), result);
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is an array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"username\": \"Administrator\", \"asset_id\": [\"a1\", \"a2\"]}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("a1", "a2"), result);
  }
}
