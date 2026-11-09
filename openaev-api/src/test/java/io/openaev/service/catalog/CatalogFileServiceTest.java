package io.openaev.service.catalog;

import static io.openaev.utils.fixtures.CatalogReleaseFixture.PAYLOAD_ID;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.archive;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.fileEntry;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.manifest;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFile;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.payloadVersion;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioFileName;
import static io.openaev.utils.fixtures.CatalogReleaseFixture.scenarioItem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.openaev.api.catalog.dto.CatalogManifest;
import io.openaev.rest.exception.BadRequestException;
import io.openaev.service.FileService;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("Catalogue release files")
class CatalogFileServiceTest {

  private static final String VERSION = "1.0.0";
  private static final String PAYLOAD_VERSION = "2.0.0";

  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
  @Mock private FileService fileService;
  private CatalogFileService service;

  @BeforeEach
  void setUp() {
    service = new CatalogFileService(fileService, mapper);
  }

  private CatalogManifest manifestOf(
      List<Map<String, Object>> items, List<Map<String, Object>> payloads) {
    return mapper.convertValue(manifest("1.261105.0", items, payloads), CatalogManifest.class);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> entry(Map<String, Object> parent, String key) {
    return (Map<String, Object>) parent.get(key);
  }

  private static Arguments refusal(
      String label, BiConsumer<Map<String, Object>, Map<String, byte[]>> breakIt, String reason) {
    return Arguments.of(label, breakIt, reason);
  }

  @Nested
  @DisplayName("Valid release")
  class Valid {

    @Test
    @DisplayName("Every file is stored once and the payload files are read")
    void given_validRelease_should_storeFilesAndReadPayloads() throws Exception {
      // Arrange
      byte[] archive = archive("{}");
      byte[] file = payloadFile(PAYLOAD_ID);
      CatalogManifest manifest =
          manifestOf(
              List.of(scenarioItem(VERSION, "VERIFIED", archive)),
              List.of(payloadVersion(PAYLOAD_VERSION, file)));

      // Act
      CheckedRelease checked =
          service.verifyAndStore(
              manifest,
              Map.of(scenarioFileName(VERSION), archive, payloadFileName(PAYLOAD_VERSION), file));

      // Assert
      assertThat(checked.refusals()).isEmpty();
      assertThat(checked.payloadFile(manifest.payloadVersions().getFirst())).isNotNull();
      verify(fileService, times(2)).uploadInPlatform(anyString(), any(InputStream.class));
    }

    @Test
    @DisplayName("A file already stored is not uploaded again")
    void given_storedFile_should_notUploadIt() throws Exception {
      // Arrange
      byte[] archive = archive("{}");
      when(fileService.existsInPlatform(anyString())).thenReturn(true);

      // Act
      service.verifyAndStore(
          manifestOf(List.of(scenarioItem(VERSION, "VERIFIED", archive)), List.of()),
          Map.of(scenarioFileName(VERSION), archive));

      // Assert
      verify(fileService, never()).uploadInPlatform(anyString(), any(InputStream.class));
    }

    @Test
    @DisplayName("A file that cannot be stored stops the whole release, to be sent again")
    void given_storageFailure_should_stopTheRelease() throws Exception {
      // Arrange
      byte[] archive = archive("{}");
      CatalogManifest manifest =
          manifestOf(List.of(scenarioItem(VERSION, "VERIFIED", archive)), List.of());
      doThrow(new IllegalStateException("storage down"))
          .when(fileService)
          .uploadInPlatform(anyString(), any(InputStream.class));

      // Act & Assert
      assertThatThrownBy(
              () -> service.verifyAndStore(manifest, Map.of(scenarioFileName(VERSION), archive)))
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("An entry without an ID refuses the whole release")
    void given_entryWithoutId_should_refuseRelease() {
      // Arrange
      Map<String, Object> version = payloadVersion(PAYLOAD_VERSION, payloadFile(PAYLOAD_ID));
      version.remove("openaev_id");
      CatalogManifest manifest = manifestOf(List.of(), List.of(version));

      // Act & Assert
      assertThatThrownBy(() -> service.verifyAndStore(manifest, Map.of()))
          .isInstanceOf(BadRequestException.class)
          .hasMessage("Manifest entry without an ID or a version");
    }

    @Test
    @DisplayName("A file the manifest does not list refuses the whole release")
    void given_unlistedFile_should_refuseRelease() {
      // Arrange
      CatalogManifest manifest = manifestOf(List.of(), List.of());

      // Act & Assert
      assertThatThrownBy(
              () ->
                  service.read(
                      manifest,
                      List.of(new MockMultipartFile("archives", "extra.zip", null, archive("{}")))))
          .isInstanceOf(BadRequestException.class);
    }
  }

  @Nested
  @DisplayName("Scenario refused alone")
  class ScenarioRefused {

    static Stream<Arguments> cases() {
      return Stream.of(
          refusal(
              "no archive",
              (item, files) -> {
                item.remove("archive");
                files.clear();
              },
              "Incomplete manifest entry"),
          refusal(
              "payload in the items",
              (item, files) -> item.put("type", "payload"),
              "Payloads are sent in the payloads list"),
          refusal(
              "unknown type",
              (item, files) -> item.put("type", "workflow"),
              "Unknown type or version format"),
          refusal(
              "version not semantic",
              (item, files) -> item.put("version", "latest"),
              "Unknown type or version format"),
          refusal("unknown status", (item, files) -> item.put("status", "BETA"), "Unknown status"),
          refusal(
              "archive not sent",
              (item, files) -> files.clear(),
              "Archive missing from the release"),
          refusal(
              "wrong size",
              (item, files) -> entry(item, "archive").put("size", 1L),
              "Archive size does not match the manifest"),
          refusal(
              "wrong hash",
              (item, files) -> entry(item, "archive").put("sha256", "0".repeat(64)),
              "Archive hash does not match the manifest"),
          refusal(
              "unsafe archive",
              (item, files) -> {
                byte[] unsafe = archive("../escape.json", "{}".getBytes(StandardCharsets.UTF_8));
                item.put("archive", fileEntry(scenarioFileName(VERSION), unsafe));
                files.put(scenarioFileName(VERSION), unsafe);
              },
              "Archive entry outside its folder: ../escape.json"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("A scenario failing a check is refused with its reason")
    void given_failedCheck_should_refuseTheScenario(
        String label, BiConsumer<Map<String, Object>, Map<String, byte[]>> breakIt, String reason) {
      // Arrange
      byte[] archive = archive("{}");
      Map<String, Object> item = scenarioItem(VERSION, "VERIFIED", archive);
      Map<String, byte[]> files = new LinkedHashMap<>(Map.of(scenarioFileName(VERSION), archive));
      breakIt.accept(item, files);

      // Act
      CheckedRelease checked = service.verifyAndStore(manifestOf(List.of(item), List.of()), files);

      // Assert
      assertThat(checked.refusals()).hasSize(1).containsValue(reason);
    }
  }

  @Nested
  @DisplayName("Payload version refused alone")
  class PayloadRefused {

    static Stream<Arguments> cases() {
      return Stream.of(
          refusal(
              "no file hash",
              (version, files) -> entry(version, "file").remove("sha256"),
              "Incomplete manifest entry"),
          refusal(
              "version not semantic",
              (version, files) -> version.put("version", "latest"),
              "Unknown version format"),
          refusal(
              "unknown status",
              (version, files) -> version.put("status", "BETA"),
              "Unknown status"),
          refusal(
              "file not sent",
              (version, files) -> files.clear(),
              "Payload file missing from the release"),
          refusal(
              "wrong size",
              (version, files) -> entry(version, "file").put("size", 1L),
              "Payload file size does not match the manifest"),
          refusal(
              "wrong hash",
              (version, files) -> entry(version, "file").put("sha256", "0".repeat(64)),
              "Payload file hash does not match the manifest"),
          refusal(
              "another ID in the file",
              (version, files) -> replaceFile(version, files, payloadFile("another-payload")),
              "Payload file does not hold the listed ID"),
          refusal(
              "file not JSON",
              (version, files) ->
                  replaceFile(version, files, "not json".getBytes(StandardCharsets.UTF_8)),
              "Unreadable payload file"));
    }

    private static void replaceFile(
        Map<String, Object> version, Map<String, byte[]> files, byte[] content) {
      version.put("file", fileEntry(payloadFileName(PAYLOAD_VERSION), content));
      files.put(payloadFileName(PAYLOAD_VERSION), content);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    @DisplayName("A payload version failing a check is refused with its reason")
    void given_failedCheck_should_refuseThePayloadVersion(
        String label, BiConsumer<Map<String, Object>, Map<String, byte[]>> breakIt, String reason) {
      // Arrange
      byte[] file = payloadFile(PAYLOAD_ID);
      Map<String, Object> version = payloadVersion(PAYLOAD_VERSION, file);
      Map<String, byte[]> files =
          new LinkedHashMap<>(Map.of(payloadFileName(PAYLOAD_VERSION), file));
      breakIt.accept(version, files);

      // Act
      CheckedRelease checked =
          service.verifyAndStore(manifestOf(List.of(), List.of(version)), files);

      // Assert
      assertThat(checked.refusals()).hasSize(1).containsValue(reason);
    }
  }
}
