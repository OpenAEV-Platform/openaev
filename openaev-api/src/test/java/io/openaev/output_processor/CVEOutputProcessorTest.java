package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import io.openaev.service.InjectExpectationService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CVEOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final InjectExpectationService injectExpectationService =
      mock(InjectExpectationService.class);
  private final CVEOutputProcessor processor =
      new CVEOutputProcessor(findingService, injectExpectationService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"id\": \"CVE-123\", \"host\": \"host1\", \"severity\": \"high\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Should return single asset id when asset_id is present as string")
  void shouldReturnSingleAssetIdWhenAssetIdPresentAsString() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"asset_id\": \"asset42\", \"id\": \"CVE-123\", \"host\": \"host1\", \"severity\": \"high\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset42"), result);
  }

  @Test
  @DisplayName("Should return multiple asset ids when asset_id is array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"asset_id\": [\"asset1\", \"asset2\"], \"id\": \"CVE-123\", \"host\": \"host1\", \"severity\": \"high\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset1", "asset2"), result);
  }

  @Test
  @DisplayName("Should return finding value as CVE id")
  void shouldReturnFindingValueAsCveId() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"id\": \"CVE-2026-1234\", \"host\": \"host1\", \"severity\": \"high\"}");
    String result = processor.toFindingValue(node);
    assertEquals("CVE-2026-1234", result);
  }

  @Test
  @DisplayName("Should return empty string when id is missing")
  void shouldReturnEmptyStringWhenIdMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"host1\", \"severity\": \"high\"}");
    String result = processor.toFindingValue(node);
    assertEquals("", result);
  }

  @Test
  @DisplayName("Should return true for valid node in validate")
  void shouldReturnTrueForValidNodeInValidate() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"id\": \"CVE-2026-1234\", \"host\": \"host1\", \"severity\": \"high\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("Should return false for invalid node in validate (missing id)")
  void shouldReturnFalseForInvalidNodeInValidateMissingId() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"host1\", \"severity\": \"high\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("Should return false for invalid node in validate (missing host)")
  void shouldReturnFalseForInvalidNodeInValidateMissingHost() throws Exception {
    JsonNode node = objectMapper.readTree("{\"id\": \"CVE-2026-1234\", \"severity\": \"high\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("Should return false for invalid node in validate (missing severity)")
  void shouldReturnFalseForInvalidNodeInValidateMissingSeverity() throws Exception {
    JsonNode node = objectMapper.readTree("{\"id\": \"CVE-2026-1234\", \"host\": \"host1\"}");
    assertFalse(processor.validate(node));
  }
}
