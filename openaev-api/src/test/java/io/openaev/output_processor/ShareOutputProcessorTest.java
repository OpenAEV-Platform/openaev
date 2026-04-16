package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ShareOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final ShareOutputProcessor processor = new ShareOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when share_name and permissions are present")
  void shouldReturnTrueWhenShareNameAndPermissionsPresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"share_name\": \"ADMIN$\", \"permissions\": \"READ\", \"host\": \"dc01\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when share_name is missing")
  void shouldReturnFalseWhenShareNameMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"permissions\": \"READ\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when permissions is missing")
  void shouldReturnFalseWhenPermissionsMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"share_name\": \"ADMIN$\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when both required fields are missing")
  void shouldReturnFalseWhenBothRequiredFieldsMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"dc01\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should return UNC path with host when host is present")
  void shouldReturnUncPathWithHostWhenHostPresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"share_name\": \"ADMIN$\", \"permissions\": \"READ,WRITE\", \"host\": \"dc01\"}");
    String result = processor.toFindingValue(node);
    assertEquals("\\\\dc01\\ADMIN$ (READ,WRITE)", result);
  }

  @Test
  @DisplayName("should return share name with permissions when host is absent")
  void shouldReturnShareNameWithPermissionsWhenHostAbsent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"share_name\": \"C$\", \"permissions\": \"READ\"}");
    String result = processor.toFindingValue(node);
    assertEquals("C$ (READ)", result);
  }

  @Test
  @DisplayName("should return share name with permissions when host is empty")
  void shouldReturnShareNameWithPermissionsWhenHostEmpty() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"share_name\": \"C$\", \"permissions\": \"READ\", \"host\": \"\"}");
    String result = processor.toFindingValue(node);
    assertEquals("C$ (READ)", result);
  }

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"share_name\": \"ADMIN$\", \"permissions\": \"READ\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is a string")
  void shouldReturnSingleAssetIdWhenAssetIdIsString() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"share_name\": \"ADMIN$\", \"permissions\": \"READ\", \"asset_id\": \"asset1\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset1"), result);
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is an array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"share_name\": \"ADMIN$\", \"permissions\": \"READ\", \"asset_id\": [\"a1\", \"a2\"]}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("a1", "a2"), result);
  }
}
