package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortScanOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final PortScanOutputProcessor processor = new PortScanOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("Should return correct finding value for valid port scan output")
  void shouldReturnCorrectFindingValueForValidPortScanOutput() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{" + "\"host\": \"192.168.1.1\"," + "\"port\": \"22\"," + "\"service\": \"ssh\"}");
    String result = processor.toFindingValue(node);
    assertEquals("192.168.1.1:22 (ssh)", result);
  }

  @Test
  @DisplayName("Should return finding value without service if service is empty")
  void shouldReturnFindingValueWithoutServiceIfServiceIsEmpty() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{" + "\"host\": \"192.168.1.1\"," + "\"port\": \"80\"," + "\"service\": \"\"}");
    String result = processor.toFindingValue(node);
    assertEquals("192.168.1.1:80", result);
  }

  @Test
  @DisplayName("Should return single asset id when asset_id is present")
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
  @DisplayName("Should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{" + "\"host\": \"192.168.1.1\"," + "\"port\": \"22\"," + "\"service\": \"ssh\"}");
    List<String> result = processor.toFindingAssets(node);
    assertTrue(result.isEmpty());
  }

  @Test
  @DisplayName("Should return empty string when host is missing")
  void shouldReturnEmptyStringWhenHostIsMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"port\": \"22\", \"service\": \"ssh\"}");
    String result = processor.toFindingValue(node);
    assertEquals(":22 (ssh)", result);
  }

  @Test
  @DisplayName("Should return empty string when port is missing")
  void shouldReturnEmptyStringWhenPortIsMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"192.168.1.1\", \"service\": \"ssh\"}");
    String result = processor.toFindingValue(node);
    assertEquals("192.168.1.1: (ssh)", result);
  }

  @Test
  @DisplayName("Should return empty string when service is missing")
  void shouldReturnEmptyStringWhenServiceIsMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"192.168.1.1\", \"port\": \"22\"}");
    String result = processor.toFindingValue(node);
    assertEquals("192.168.1.1:22", result);
  }

  @Test
  @DisplayName("Should return false for invalid node in validate (missing host)")
  void shouldReturnFalseForInvalidNodeInValidateMissingHost() throws Exception {
    JsonNode node = objectMapper.readTree("{\"port\": \"22\", \"service\": \"ssh\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("Should return false for invalid node in validate (missing port)")
  void shouldReturnFalseForInvalidNodeInValidateMissingPort() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"192.168.1.1\", \"service\": \"ssh\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("Should return false for invalid node in validate (missing service)")
  void shouldReturnFalseForInvalidNodeInValidateMissingService() throws Exception {
    JsonNode node = objectMapper.readTree("{\"host\": \"192.168.1.1\", \"port\": \"22\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("Should return true for valid node in validate")
  void shouldReturnTrueForValidNodeInValidate() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"host\": \"192.168.1.1\", \"port\": \"22\", \"service\": \"ssh\"}");
    assertTrue(processor.validate(node));
  }
}
