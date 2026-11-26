package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final EmailOutputProcessor processor = new EmailOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when email is present")
  void shouldReturnTrueWhenEmailPresent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"email\": \"alice@corp.test\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when email is missing")
  void shouldReturnFalseWhenEmailMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"asset_id\": \"asset42\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when email is null")
  void shouldReturnFalseWhenEmailNull() throws Exception {
    JsonNode node = objectMapper.readTree("{\"email\": null}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return the email address as the finding value")
  void shouldReturnEmailAsFindingValue() throws Exception {
    JsonNode node = objectMapper.readTree("{\"email\": \"bob@corp.test\"}");
    assertEquals("bob@corp.test", processor.toFindingValue(node));
  }
}
