package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AsreproastableAccountOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final AsreproastableAccountOutputProcessor processor =
      new AsreproastableAccountOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when username is present")
  void shouldReturnTrueWhenUsernamePresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"username\": \"svc_backup\", \"hash\": \"$krb5asrep$23$svc_backup@CORP\", \"host\": \"dc01\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return true when username is present without optional fields")
  void shouldReturnTrueWhenUsernamePresentWithoutOptionalFields() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"svc_backup\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when username is missing")
  void shouldReturnFalseWhenUsernameMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"hash\": \"$krb5asrep$23$svc_backup@CORP\", \"host\": \"dc01\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when username is null")
  void shouldReturnFalseWhenUsernameNull() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": null}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return username as finding value")
  void shouldReturnUsernameAsFindingValue() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"username\": \"svc_backup\", \"hash\": \"$krb5asrep$23$svc_backup@CORP\"}");
    String result = processor.toFindingValue(node);
    assertEquals("svc_backup", result);
  }

  @Test
  @DisplayName("should return empty string when username is missing for finding value")
  void shouldReturnEmptyStringWhenUsernameMissingForFindingValue() throws Exception {
    JsonNode node = objectMapper.readTree("{\"hash\": \"somehash\"}");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }
}
