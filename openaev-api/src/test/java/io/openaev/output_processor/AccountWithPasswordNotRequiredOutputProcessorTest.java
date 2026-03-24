package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
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

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": \"guest\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is a string")
  void shouldReturnSingleAssetIdWhenAssetIdIsString() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": \"guest\", \"asset_id\": \"asset42\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset42"), result);
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is an array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"account\": \"guest\", \"asset_id\": [\"a1\", \"a2\"]}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("a1", "a2"), result);
  }
}
