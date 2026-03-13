package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbstractOutputProcessorTest {

  private static class TestOutputProcessor extends AbstractOutputProcessor {

    TestOutputProcessor() {
      super(null, null, Collections.emptyList(), false);
    }
  }

  private TestOutputProcessor processor;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    processor = new TestOutputProcessor();
    objectMapper = new ObjectMapper();
  }

  @Test
  @DisplayName(
      "should join array elements and trim quotes when buildString is called with an array node")
  void shouldJoinArrayElementsAndTrimQuotesWhenBuildStringCalledWithArrayNode() throws Exception {
    JsonNode node = objectMapper.readTree("[\"foo\", \"bar\"]");
    String result = processor.buildString(node);
    assertEquals("foo bar", result);
  }

  @Test
  @DisplayName("should trim quotes when buildString is called with a string node")
  void shouldTrimQuotesWhenBuildStringCalledWithStringNode() throws Exception {
    JsonNode node = objectMapper.readTree("\"baz\"");
    String result = processor.buildString(node);
    assertEquals("baz", result);
  }

  @Test
  @DisplayName("should extract and process value when buildString is called with a key")
  void shouldExtractAndProcessValueWhenBuildStringCalledWithKey() throws Exception {
    JsonNode node = objectMapper.readTree("{\"key\": [\"a\", \"b\"]}");
    String result = processor.buildString(node, "key");
    assertEquals("a b", result);
  }

  @Test
  @DisplayName("should return empty string when buildString is called with a missing or null key")
  void shouldReturnEmptyStringWhenBuildStringCalledWithMissingOrNullKey() throws Exception {
    JsonNode node = objectMapper.readTree("{}");
    assertEquals("", processor.buildString(node, "missing"));
    JsonNode node2 = objectMapper.readTree("{\"key\": null}");
    assertEquals("", processor.buildString(node2, "key"));
  }

  @Test
  @DisplayName("should remove leading and trailing quotes when trimQuotes is called")
  void shouldRemoveLeadingAndTrailingQuotesWhenTrimQuotesCalled() {
    assertEquals("foo", processor.trimQuotes("\"foo\""));
    assertEquals("bar", processor.trimQuotes("bar"));
    assertEquals("foo\"bar", processor.trimQuotes("\"foo\"bar"));
    assertEquals("foo\"bar", processor.trimQuotes("foo\"bar"));
  }
}
