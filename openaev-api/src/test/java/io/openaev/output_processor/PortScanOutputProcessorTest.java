package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortScanOutputProcessorTest {

  private final PortScanOutputProcessor processor = new PortScanOutputProcessor();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return correct finding value for valid port scan output")
  void shouldReturnCorrectFindingValueForValidPortScanOutput() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{" + "\"host\": \"192.168.1.1\"," + "\"port\": \"22\"," + "\"service\": \"ssh\"}");
    String result = processor.toFindingValue(node);
    assertEquals("192.168.1.1:22 (ssh)", result);
  }

  @Test
  @DisplayName("should return finding value without service if service is empty")
  void shouldReturnFindingValueWithoutServiceIfServiceIsEmpty() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{" + "\"host\": \"192.168.1.1\"," + "\"port\": \"80\"," + "\"service\": \"\"}");
    String result = processor.toFindingValue(node);
    assertEquals("192.168.1.1:80", result);
  }

  @Test
  @DisplayName("should return single asset id when asset_id is present")
  void shouldReturnSingleAssetIdWhenAssetIdPresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{"
                + "\"asset_id\": \"asset123\","
                + "\"host\": \"192.168.1.1\","
                + "\"port\": \"22\","
                + "\"service\": \"ssh\"}");
    List<String> result = processor.toFindingAssets(node);
    assertEquals(List.of("asset123"), result);
  }

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{" + "\"host\": \"192.168.1.1\"," + "\"port\": \"22\"," + "\"service\": \"ssh\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }
}
