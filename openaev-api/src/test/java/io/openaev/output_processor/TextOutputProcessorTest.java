package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TextOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final TextOutputProcessor processor = new TextOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should return text value for simple node")
  void shouldReturnTextValueForSimpleNode() throws Exception {
    JsonNode node = objectMapper.readTree("\"hello world\"");
    String result = processor.toFindingValue(node);
    assertEquals("hello world", result);
  }

  @Test
  @DisplayName("Should return concatenated values for array node")
  void shouldReturnConcatenatedValuesForArrayNode() throws Exception {
    JsonNode node = objectMapper.readTree("[\"foo\", \"bar\", \"baz\"]");
    String result = processor.toFindingValue(node);
    assertEquals("foo bar baz", result);
  }

  @Test
  @DisplayName("Should return empty string for empty node")
  void shouldReturnEmptyStringForEmptyNode() throws Exception {
    JsonNode node = objectMapper.readTree("\"\"");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }
}
