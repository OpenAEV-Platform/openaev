package io.openaev.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.service.ImportService.EntryKind;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ImportService kind of an archive entry, told from the entry itself")
class ImportServiceEntryTypeTest {

  private final ObjectMapper mapper = new ObjectMapper();

  private static byte[] bytes(String content) {
    return content.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] zipWith(String entryName) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(out)) {
      zip.putNextEntry(new ZipEntry(entryName));
      zip.write(bytes("{}"));
      zip.closeEntry();
    }
    return out.toByteArray();
  }

  @Test
  @DisplayName("An export JSON is the exported object")
  void exportJsonIsTheExport() {
    EntryKind kind =
        ImportService.inferEntryKind(
            "My scenario.json", bytes("{\"export_version\": 1, \"scenario_name\": \"x\"}"), mapper);

    assertThat(kind).isEqualTo(EntryKind.EXPORT);
  }

  @Test
  @DisplayName("The JSON extension is matched whatever its case")
  void jsonExtensionIsCaseInsensitive() {
    EntryKind kind =
        ImportService.inferEntryKind("injects.JSON", bytes("{\"export_version\": 1}"), mapper);

    assertThat(kind).isEqualTo(EntryKind.EXPORT);
  }

  @Test
  @DisplayName("A JSON document without export_version stays an attachment")
  void jsonWithoutExportVersionIsAnAttachment() {
    EntryKind kind =
        ImportService.inferEntryKind(
            "documents/config.json", bytes("{\"key\": \"value\"}"), mapper);

    assertThat(kind).isEqualTo(EntryKind.ATTACHMENT);
  }

  @Test
  @DisplayName("A JSON array or invalid JSON stays an attachment")
  void jsonArrayOrInvalidJsonIsAnAttachment() {
    assertThat(ImportService.inferEntryKind("list.json", bytes("[1, 2]"), mapper))
        .isEqualTo(EntryKind.ATTACHMENT);
    assertThat(ImportService.inferEntryKind("broken.json", bytes("{not json"), mapper))
        .isEqualTo(EntryKind.ATTACHMENT);
  }

  @Test
  @DisplayName("A non-JSON file is an attachment")
  void nonJsonIsAnAttachment() {
    EntryKind kind =
        ImportService.inferEntryKind("documents/report.pdf", bytes("%PDF-1.7"), mapper);

    assertThat(kind).isEqualTo(EntryKind.ATTACHMENT);
  }

  @Test
  @DisplayName("macOS archive metadata is never read as the exported object")
  void macOsMetadataIsAnAttachment() {
    EntryKind kind =
        ImportService.inferEntryKind(
            "__MACOSX/._My scenario.json", bytes("{\"export_version\": 1}"), mapper);

    assertThat(kind).isEqualTo(EntryKind.ATTACHMENT);
  }

  @Test
  @DisplayName("A nested zip holding a payload.json is a payload archive")
  void nestedPayloadZipIsAPayloadArchive() throws IOException {
    EntryKind kind =
        ImportService.inferEntryKind("My payload.zip", zipWith("payload.json"), mapper);

    assertThat(kind).isEqualTo(EntryKind.PAYLOAD_ARCHIVE);
  }

  @Test
  @DisplayName("A zip without payload.json, or an unreadable zip, stays an attachment")
  void otherZipIsAnAttachment() throws IOException {
    assertThat(ImportService.inferEntryKind("documents/logs.zip", zipWith("log.txt"), mapper))
        .isEqualTo(EntryKind.ATTACHMENT);
    assertThat(ImportService.inferEntryKind("documents/broken.zip", bytes("not a zip"), mapper))
        .isEqualTo(EntryKind.ATTACHMENT);
  }

  @Test
  @DisplayName("The documents an export references are listed by their document_target")
  void documentTargetsAreReadFromTheExport(@TempDir Path dir) throws IOException {
    String export =
        "{\"export_version\": 1, \"scenario_documents\": ["
            + "{\"document_target\": \"a1b2.json\"},"
            + " {\"document_target\": \"c3d4.zip\"}]}";
    Path archive = dir.resolve("export.zip");
    try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
      putEntry(zip, "My scenario.json", export);
      putEntry(zip, "a1b2.json", "{\"export_version\": 1}");
      putEntry(zip, "notes.json", "{\"document_target\": \"ignored.pdf\"}");
    }

    try (ZipFile zip = new ZipFile(archive.toFile())) {
      assertThat(ImportService.documentTargets(zip, mapper))
          .contains("a1b2.json", "c3d4.zip")
          .doesNotContain("ignored.pdf");
    }
  }

  private static void putEntry(ZipOutputStream zip, String name, String content)
      throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(bytes(content));
    zip.closeEntry();
  }
}
