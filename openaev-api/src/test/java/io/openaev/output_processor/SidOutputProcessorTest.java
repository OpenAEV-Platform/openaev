package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
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
}
