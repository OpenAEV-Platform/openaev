package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CVEOutputProcessorTest {

  private final CVEOutputProcessor processor = new CVEOutputProcessor();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"id\": \"CVE-123\", \"host\": \"host1\", \"severity\": \"high\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is present as string")
  void shouldReturnSingleAssetIdWhenAssetIdPresentAsString() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"asset_id\": \"asset42\", \"id\": \"CVE-123\", \"host\": \"host1\", \"severity\": \"high\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset42"), result);
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"asset_id\": [\"asset1\", \"asset2\"], \"id\": \"CVE-123\", \"host\": \"host1\", \"severity\": \"high\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset1", "asset2"), result);
  }
}
