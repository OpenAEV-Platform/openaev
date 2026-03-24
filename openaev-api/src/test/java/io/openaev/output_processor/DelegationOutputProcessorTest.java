package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DelegationOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final DelegationOutputProcessor processor = new DelegationOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when account is present")
  void shouldReturnTrueWhenAccountPresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"account\": \"svc_sql\", \"delegation_type\": \"Unconstrained\", \"rights_to\": \"CIFS/dc01\", \"host\": \"dc01\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return true when account is present without optional fields")
  void shouldReturnTrueWhenAccountPresentWithoutOptionalFields() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": \"svc_sql\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when account is missing")
  void shouldReturnFalseWhenAccountMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"delegation_type\": \"Unconstrained\", \"host\": \"dc01\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when account is null")
  void shouldReturnFalseWhenAccountNull() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": null}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return account with delegation type and rights_to when all present")
  void shouldReturnFullFindingValueWhenAllFieldsPresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"account\": \"svc_sql\", \"delegation_type\": \"Unconstrained\", \"rights_to\": \"CIFS/dc01\"}");
    String result = processor.toFindingValue(node);
    assertEquals("svc_sql [Unconstrained] -> CIFS/dc01", result);
  }

  @Test
  @DisplayName("should return account with delegation type when rights_to is absent")
  void shouldReturnAccountWithDelegationTypeWhenRightsToAbsent() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"account\": \"svc_sql\", \"delegation_type\": \"Constrained\"}");
    String result = processor.toFindingValue(node);
    assertEquals("svc_sql [Constrained]", result);
  }

  @Test
  @DisplayName("should return account with rights_to when delegation_type is absent")
  void shouldReturnAccountWithRightsToWhenDelegationTypeAbsent() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"account\": \"svc_sql\", \"rights_to\": \"CIFS/dc01\"}");
    String result = processor.toFindingValue(node);
    assertEquals("svc_sql -> CIFS/dc01", result);
  }

  @Test
  @DisplayName("should return account only when optional fields are absent")
  void shouldReturnAccountOnlyWhenOptionalFieldsAbsent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": \"svc_sql\"}");
    String result = processor.toFindingValue(node);
    assertEquals("svc_sql", result);
  }

  @Test
  @DisplayName("should return account only when optional fields are empty")
  void shouldReturnAccountOnlyWhenOptionalFieldsEmpty() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"account\": \"svc_sql\", \"delegation_type\": \"\", \"rights_to\": \"\"}");
    String result = processor.toFindingValue(node);
    assertEquals("svc_sql", result);
  }

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": \"svc_sql\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is a string")
  void shouldReturnSingleAssetIdWhenAssetIdIsString() throws Exception {
    JsonNode node = objectMapper.readTree("{\"account\": \"svc_sql\", \"asset_id\": \"asset42\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset42"), result);
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is an array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"account\": \"svc_sql\", \"asset_id\": [\"a1\", \"a2\"]}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("a1", "a2"), result);
  }
}
