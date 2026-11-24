package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final GroupOutputProcessor processor = new GroupOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when group_name is present")
  void shouldReturnTrueWhenGroupNamePresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"group_name\": \"Domain Admins\", \"member_count\": \"5\", \"host\": \"dc01\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when group_name is missing")
  void shouldReturnFalseWhenGroupNameMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"member_count\": \"5\", \"host\": \"dc01\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when group_name is null")
  void shouldReturnFalseWhenGroupNameNull() throws Exception {
    JsonNode node = objectMapper.readTree("{\"group_name\": null}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return group name with member count when member_count is present")
  void shouldReturnGroupNameWithMemberCountWhenPresent() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"group_name\": \"Domain Admins\", \"member_count\": \"5\"}");
    String result = processor.toFindingValue(node);
    assertEquals("Domain Admins (5 members)", result);
  }

  @Test
  @DisplayName("should return group name only when member_count is absent")
  void shouldReturnGroupNameOnlyWhenMemberCountAbsent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"group_name\": \"Domain Admins\"}");
    String result = processor.toFindingValue(node);
    assertEquals("Domain Admins", result);
  }

  @Test
  @DisplayName("should return group name only when member_count is empty")
  void shouldReturnGroupNameOnlyWhenMemberCountEmpty() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"group_name\": \"Domain Admins\", \"member_count\": \"\"}");
    String result = processor.toFindingValue(node);
    assertEquals("Domain Admins", result);
  }
}
