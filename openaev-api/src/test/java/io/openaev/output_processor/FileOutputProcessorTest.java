package io.openaev.output_processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.rest.finding.FindingService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FileOutputProcessorTest {

  private final FindingService findingService = mock(FindingService.class);
  private final FileOutputProcessor processor = new FileOutputProcessor(findingService);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  @DisplayName("should return true when file_name is present")
  void shouldReturnTrueWhenFileNamePresent() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"file_name\": \"secret.ps1\", \"share\": \"SYSVOL\", \"host\": \"WINTERFELL\"}");
    assertTrue(processor.validate(node));
  }

  @Test
  @DisplayName("should return false when file_name is missing")
  void shouldReturnFalseWhenFileNameMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"share\": \"SYSVOL\", \"host\": \"WINTERFELL\"}");
    assertFalse(processor.validate(node));
  }

  @Test
  @DisplayName("should build a UNC path for a file discovered on an SMB share")
  void shouldBuildUncPathForShareFile() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"file_name\": \"secret.ps1\", \"path\": \"north.sevenkingdoms.local/scripts\","
                + " \"share\": \"SYSVOL\", \"host\": \"WINTERFELL\"}");
    assertEquals(
        "\\\\WINTERFELL\\SYSVOL\\north.sevenkingdoms.local\\scripts\\secret.ps1",
        processor.toFindingValue(node));
  }

  @Test
  @DisplayName("should build a UNC path for a top-level share file (empty path)")
  void shouldBuildUncPathForTopLevelShareFile() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"file_name\": \"script.ps1\", \"share\": \"NETLOGON\", \"host\": \"WINTERFELL\"}");
    assertEquals("\\\\WINTERFELL\\NETLOGON\\script.ps1", processor.toFindingValue(node));
  }

  @Test
  @DisplayName("should keep same-named files on different shares distinct by value")
  void shouldKeepSameNamedFilesDistinctByValue() throws Exception {
    JsonNode netlogon =
        objectMapper.readTree(
            "{\"file_name\": \"secret.ps1\", \"share\": \"NETLOGON\", \"host\": \"WINTERFELL\"}");
    JsonNode sysvol =
        objectMapper.readTree(
            "{\"file_name\": \"secret.ps1\", \"path\": \"scripts\", \"share\": \"SYSVOL\","
                + " \"host\": \"WINTERFELL\"}");
    assertNotEquals(processor.toFindingValue(netlogon), processor.toFindingValue(sysvol));
  }

  @Test
  @DisplayName("should build a host-prefixed path for a local file (no share)")
  void shouldBuildHostPathForLocalFile() throws Exception {
    JsonNode node =
        objectMapper.readTree(
            "{\"file_name\": \"config.ini\", \"path\": \"/home/user\", \"host\": \"ftp01\"}");
    assertEquals("ftp01:/home/user/config.ini", processor.toFindingValue(node));
  }

  @Test
  @DisplayName("should return the relative location when host is absent")
  void shouldReturnRelativeLocationWhenHostAbsent() throws Exception {
    JsonNode node = objectMapper.readTree("{\"file_name\": \"secret.ps1\", \"share\": \"SYSVOL\"}");
    assertEquals("SYSVOL/secret.ps1", processor.toFindingValue(node));
  }

  @Test
  @DisplayName("should return empty list when asset_id is missing")
  void shouldReturnEmptyListWhenAssetIdMissing() throws Exception {
    JsonNode node = objectMapper.readTree("{\"file_name\": \"secret.ps1\"}");
    assertTrue(processor.toFindingAssets(node).isEmpty());
  }

  @Test
  @DisplayName("should return single asset id when asset_id is a string")
  void shouldReturnSingleAssetIdWhenAssetIdIsString() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"file_name\": \"secret.ps1\", \"asset_id\": \"asset1\"}");
    assertEquals(List.of("asset1"), processor.toFindingAssets(node));
  }

  @Test
  @DisplayName("should return multiple asset ids when asset_id is an array")
  void shouldReturnMultipleAssetIdsWhenAssetIdIsArray() throws Exception {
    JsonNode node =
        objectMapper.readTree("{\"file_name\": \"secret.ps1\", \"asset_id\": [\"a1\", \"a2\"]}");
    assertEquals(List.of("a1", "a2"), processor.toFindingAssets(node));
  }
}
