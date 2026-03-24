package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComputerOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final ComputerOutputProcessor processor = new ComputerOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when computer_name is present")
  void shouldReturnTrueWhenComputerNamePresent() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"computer_name\": \"WORKSTATION01\", \"host\": \"192.168.1.10\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when computer_name is missing")
  void shouldReturnFalseWhenComputerNameMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"192.168.1.10\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when computer_name is null")
  void shouldReturnFalseWhenComputerNameNull() throws Exception {
    JsonNode node = objectMapper.readTree("{\"computer_name\": null}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return computer name as finding value")
  void shouldReturnComputerNameAsFindingValue() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"computer_name\": \"WORKSTATION01\", \"host\": \"192.168.1.10\"}");
    String result = processor.toFindingValue(node);
    assertEquals("WORKSTATION01", result);
  }

  @Test
  @DisplayName("should return empty string when computer_name is missing for finding value")
  void shouldReturnEmptyStringWhenComputerNameMissingForFindingValue() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"192.168.1.10\"}");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"computer_name\": \"WORKSTATION01\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is a string")
  void shouldReturnSingleAssetIdWhenAssetIdIsString() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"computer_name\": \"WORKSTATION01\", \"asset_id\": \"asset42\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset42"), result);
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is an array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"computer_name\": \"WORKSTATION01\", \"asset_id\": [\"a1\", \"a2\"]}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("a1", "a2"), result);
  }
}
