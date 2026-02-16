package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IPv4OutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final IPv4OutputProcessor processor = new IPv4OutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should return IPv4 value for simple node")
  void shouldReturnIPv4ValueForSimpleNode() throws Exception {
    JsonNode node = objectMapper.readTree("\"192.168.1.1\"");
    String result = processor.toFindingValue(node);
    assertEquals("192.168.1.1", result);
  }

  @Test
  @DisplayName("Should return concatenated values for array node")
  void shouldReturnConcatenatedValuesForArrayNode() throws Exception {
    JsonNode node = objectMapper.readTree("[\"192.168.1.1\", \"10.0.0.1\"]");
    String result = processor.toFindingValue(node);
    assertEquals("192.168.1.1 10.0.0.1", result);
  }

  @Test
  @DisplayName("Should return empty string for empty node")
  void shouldReturnEmptyStringForEmptyNode() throws Exception {
    JsonNode node = objectMapper.readTree("\"\"");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }
}
