package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountWithPasswordNotRequiredOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final AccountWithPasswordNotRequiredOutputProcessor processor =
      new AccountWithPasswordNotRequiredOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when account is present")
  void shouldReturnTrueWhenAccountPresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"account\": \"guest\", \"status\": \"enabled\", \"host\": \"dc01\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return true when account is present without optional fields")
  void shouldReturnTrueWhenAccountPresentWithoutOptionalFields() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": \"guest\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when account is missing")
  void shouldReturnFalseWhenAccountMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"status\": \"enabled\", \"host\": \"dc01\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when account is null")
  void shouldReturnFalseWhenAccountNull() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": null}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return account with status when status is present")
  void shouldReturnAccountWithStatusWhenStatusPresent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": \"guest\", \"status\": \"enabled\"}");
    String result = processor.toFindingValue(node);
    assertEquals("guest (enabled)", result);
  }

  @Test
  @DisplayName("should return account only when status is absent")
  void shouldReturnAccountOnlyWhenStatusAbsent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": \"guest\"}");
    String result = processor.toFindingValue(node);
    assertEquals("guest", result);
  }

  @Test
  @DisplayName("should return account only when status is empty")
  void shouldReturnAccountOnlyWhenStatusEmpty() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": \"guest\", \"status\": \"\"}");
    String result = processor.toFindingValue(node);
    assertEquals("guest", result);
  }
}
