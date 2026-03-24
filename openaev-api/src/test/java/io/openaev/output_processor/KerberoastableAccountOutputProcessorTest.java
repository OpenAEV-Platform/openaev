package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KerberoastableAccountOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final KerberoastableAccountOutputProcessor processor =
      new KerberoastableAccountOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when username is present")
  void shouldReturnTrueWhenUsernamePresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"username\": \"svc_mssql\", \"hash\": \"$krb5tgs$23$*svc_mssql$CORP\", \"host\": \"dc01\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return true when username is present without optional fields")
  void shouldReturnTrueWhenUsernamePresentWithoutOptionalFields() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"svc_mssql\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when username is missing")
  void shouldReturnFalseWhenUsernameMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"hash\": \"$krb5tgs$23$*svc_mssql$CORP\", \"host\": \"dc01\"}");
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
            "{\"username\": \"svc_mssql\", \"hash\": \"$krb5tgs$23$*svc_mssql$CORP\"}");
    String result = processor.toFindingValue(node);
    assertEquals("svc_mssql", result);
  }

  @Test
  @DisplayName("should return empty string when username is missing for finding value")
  void shouldReturnEmptyStringWhenUsernameMissingForFindingValue() throws Exception {
    JsonNode node = objectMapper.readTree("{\"hash\": \"somehash\"}");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"username\": \"svc_mssql\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is a string")
  void shouldReturnSingleAssetIdWhenAssetIdIsString() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"username\": \"svc_mssql\", \"asset_id\": \"asset42\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset42"), result);
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is an array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"username\": \"svc_mssql\", \"asset_id\": [\"a1\", \"a2\"]}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("a1", "a2"), result);
  }
}
