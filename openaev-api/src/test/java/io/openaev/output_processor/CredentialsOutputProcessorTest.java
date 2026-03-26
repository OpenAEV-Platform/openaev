package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CredentialsOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final CredentialsOutputProcessor processor =
      new CredentialsOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should return true when both username and password are present")
  void shouldReturnTrueWhenBothUsernameAndPasswordPresent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"alice\", \"password\": \"pass1\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("Should return false when username is missing")
  void shouldReturnFalseWhenUsernameMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"password\": \"pass1\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("Should return false when password is missing")
  void shouldReturnFalseWhenPasswordMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"bob\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("Should return finding value as username:password")
  void shouldReturnFindingValueAsUsernamePassword() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"charles\", \"password\": \"pass1\"}");
    String result = processor.toFindingValue(node);
    assertEquals("charles:pass1", result);
  }

  @Test
  @DisplayName("Should return empty string when username and password are missing")
  void shouldReturnEmptyStringWhenUsernameAndPasswordMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{}");
    String result = processor.toFindingValue(node);
    assertEquals(":", result);
  }

  @Test
  @DisplayName("Should return empty string when username is empty")
  void shouldReturnEmptyStringWhenUsernameIsEmpty() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"\", \"password\": \"pass1\"}");
    String result = processor.toFindingValue(node);
    assertEquals(":pass1", result);
  }

  @Test
  @DisplayName("Should return empty string when password is empty")
  void shouldReturnEmptyStringWhenPasswordIsEmpty() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"charles\", \"password\": \"\"}");
    String result = processor.toFindingValue(node);
    assertEquals("charles:", result);
  }
}
