package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CredentialsOutputProcessorTest {

  private final CredentialsOutputProcessor processor = new CredentialsOutputProcessor();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when both username and password are present")
  void shouldReturnTrueWhenBothUsernameAndPasswordPresent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"alice\", \"password\": \"pass1\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when username is missing")
  void shouldReturnFalseWhenUsernameMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"password\": \"pass1\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when password is missing")
  void shouldReturnFalseWhenPasswordMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"bob\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return finding value as username:password")
  void shouldReturnFindingValueAsUsernamePassword() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"charles\", \"password\": \"pass1\"}");
    String result = processor.toFindingValue(node);
    assertEquals("charles:pass1", result);
  }
}
