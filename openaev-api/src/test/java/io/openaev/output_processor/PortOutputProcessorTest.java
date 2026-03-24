package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final PortOutputProcessor processor = new PortOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should return port value for simple node")
  void shouldReturnPortValueForSimpleNode() throws Exception {
    JsonNode node = objectMapper.readTree("8080");
    String result = processor.toFindingValue(node);
    assertEquals("8080", result);
  }

  @Test
  @DisplayName("Should return concatenated values for array node")
  void shouldReturnConcatenatedValuesForArrayNode() throws Exception {
    JsonNode node = objectMapper.readTree("[80, 443, 22]");
    String result = processor.toFindingValue(node);
    assertEquals("80 443 22", result);
  }

  @Test
  @DisplayName("Should return empty string for empty node")
  void shouldReturnEmptyStringForEmptyNode() throws Exception {
    JsonNode node = objectMapper.readTree("\"\"");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }
}
