package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NumberOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final NumberOutputProcessor processor = new NumberOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should return number value for simple node")
  void shouldReturnNumberValueForSimpleNode() throws Exception {
    JsonNode node = objectMapper.readTree("42");
    String result = processor.toFindingValue(node);
    assertEquals("42", result);
  }

  @Test
  @DisplayName("Should return concatenated values for array node")
  void shouldReturnConcatenatedValuesForArrayNode() throws Exception {
    JsonNode node = objectMapper.readTree("[1, 2, 3]");
    String result = processor.toFindingValue(node);
    assertEquals("1 2 3", result);
  }

  @Test
  @DisplayName("Should return empty string for empty node")
  void shouldReturnEmptyStringForEmptyNode() throws Exception {
    JsonNode node = objectMapper.readTree("\"\"");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }
}
