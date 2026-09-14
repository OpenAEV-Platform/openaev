package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

  @Test
  @DisplayName("Should preserve leading zero for port value")
  void shouldPreserveLeadingZeroForPortValue() throws Exception {
    JsonNode node = objectMapper.readTree("\"05\"");
    String result = processor.toFindingValue(node);
    assertEquals("05", result);
  }

  @Test
  @DisplayName("Should reject non numeric port value")
  void shouldRejectNonNumericPortValue() throws Exception {
    JsonNode node = objectMapper.readTree("\"abc\"");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("Should reject out of range port value")
  void shouldRejectOutOfRangePortValue() throws Exception {
    JsonNode tooLargeNode = objectMapper.readTree("\"99999\"");
    JsonNode negativeNode = objectMapper.readTree("\"-1\"");
    assertFalse(processor.validate(tooLargeNode));
    assertFalse(processor.validate(negativeNode));
  }

  @Test
  @DisplayName("Should accept port value with leading zero")
  void shouldAcceptPortValueWithLeadingZero() throws Exception {
    JsonNode node = objectMapper.readTree("\"05\"");
    assertTrue(processor.validate(node));
  }
}
