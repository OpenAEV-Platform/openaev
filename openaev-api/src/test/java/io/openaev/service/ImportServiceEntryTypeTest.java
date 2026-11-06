package io.openaev.service;

import static io.openaev.service.ImportService.EXPORT_ENTRY_ATTACHMENT;
import static io.openaev.service.ImportService.EXPORT_ENTRY_SCENARIO;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ImportService entry type of an archive entry without comment (#3686)")
class ImportServiceEntryTypeTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static byte[] bytes(String content) {
    return content.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("A re-zipped export JSON is read as the exported object")
  void exportJsonIsTheExportedObject() {
    String type =
        ImportService.inferEntryType(
            "My scenario.json", bytes("{\"export_version\": 1, \"scenario_name\": \"x\"}"), mapper);

    assertThat(type).isEqualTo(EXPORT_ENTRY_SCENARIO);
  }

  @Test
  @DisplayName("The JSON extension is matched whatever its case")
  void jsonExtensionIsCaseInsensitive() {
    String type =
        ImportService.inferEntryType("injects.JSON", bytes("{\"export_version\": 1}"), mapper);

    assertThat(type).isEqualTo(EXPORT_ENTRY_SCENARIO);
  }

  @Test
  @DisplayName("A JSON document without export_version stays an attachment")
  void jsonWithoutExportVersionIsAnAttachment() {
    String type =
        ImportService.inferEntryType(
            "documents/config.json", bytes("{\"key\": \"value\"}"), mapper);

    assertThat(type).isEqualTo(EXPORT_ENTRY_ATTACHMENT);
  }

  @Test
  @DisplayName("A JSON array or invalid JSON stays an attachment")
  void jsonArrayOrInvalidJsonIsAnAttachment() {
    assertThat(ImportService.inferEntryType("list.json", bytes("[1, 2]"), mapper))
        .isEqualTo(EXPORT_ENTRY_ATTACHMENT);
    assertThat(ImportService.inferEntryType("broken.json", bytes("{not json"), mapper))
        .isEqualTo(EXPORT_ENTRY_ATTACHMENT);
  }

  @Test
  @DisplayName("A non-JSON file is an attachment")
  void nonJsonIsAnAttachment() {
    String type = ImportService.inferEntryType("documents/report.pdf", bytes("%PDF-1.7"), mapper);

    assertThat(type).isEqualTo(EXPORT_ENTRY_ATTACHMENT);
  }

  @Test
  @DisplayName("macOS archive metadata is never read as the exported object")
  void macOsMetadataIsAnAttachment() {
    String type =
        ImportService.inferEntryType(
            "__MACOSX/._My scenario.json", bytes("{\"export_version\": 1}"), mapper);

    assertThat(type).isEqualTo(EXPORT_ENTRY_ATTACHMENT);
  }
}
