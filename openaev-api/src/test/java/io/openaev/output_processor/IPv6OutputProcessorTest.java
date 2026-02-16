package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IPv6OutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final IPv6OutputProcessor processor = new IPv6OutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should return IPv6 value for simple node")
  void shouldReturnIPv6ValueForSimpleNode() throws Exception {
    JsonNode node = objectMapper.readTree("\"2001:0db8:85a3:0000:0000:8a2e:0370:7334\"");
    String result = processor.toFindingValue(node);
    assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334", result);
  }

  @Test
  @DisplayName("Should return concatenated values for array node")
  void shouldReturnConcatenatedValuesForArrayNode() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "[\"2001:0db8:85a3:0000:0000:8a2e:0370:7334\", \"fe80::1ff:fe23:4567:890a\"]");
    String result = processor.toFindingValue(node);
    assertEquals("2001:0db8:85a3:0000:0000:8a2e:0370:7334 fe80::1ff:fe23:4567:890a", result);
  }

  @Test
  @DisplayName("Should return empty string for empty node")
  void shouldReturnEmptyStringForEmptyNode() throws Exception {
    JsonNode node = objectMapper.readTree("\"\"");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }
}
