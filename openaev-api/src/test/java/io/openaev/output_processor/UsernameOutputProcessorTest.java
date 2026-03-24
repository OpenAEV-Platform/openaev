package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsernameOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final UsernameOutputProcessor processor = new UsernameOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when username is present")
  void shouldReturnTrueWhenUsernamePresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"username\": \"alice\", \"domain\": \"CORP\", \"host\": \"dc01\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return true when username is present without optional fields")
  void shouldReturnTrueWhenUsernamePresentWithoutOptionalFields() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"alice\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when username is missing")
  void shouldReturnFalseWhenUsernameMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"domain\": \"CORP\", \"host\": \"dc01\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when username is null")
  void shouldReturnFalseWhenUsernameNull() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": null}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return domain\\username when domain is present")
  void shouldReturnDomainBackslashUsernameWhenDomainPresent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"alice\", \"domain\": \"CORP\"}");
    String result = processor.toFindingValue(node);
    assertEquals("CORP\\alice", result);
  }

  @Test
  @DisplayName("should return username only when domain is absent")
  void shouldReturnUsernameOnlyWhenDomainAbsent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"bob\"}");
    String result = processor.toFindingValue(node);
    assertEquals("bob", result);
  }

  @Test
  @DisplayName("should return username only when domain is empty")
  void shouldReturnUsernameOnlyWhenDomainEmpty() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"bob\", \"domain\": \"\"}");
    String result = processor.toFindingValue(node);
    assertEquals("bob", result);
  }

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"alice\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is a string")
  void shouldReturnSingleAssetIdWhenAssetIdIsString() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"alice\", \"asset_id\": \"asset42\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset42"), result);
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is an array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"username\": \"alice\", \"asset_id\": [\"a1\", \"a2\"]}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("a1", "a2"), result);
  }
}
