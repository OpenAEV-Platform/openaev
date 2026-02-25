package io.openaev.utilstest;

import static io.openaev.service.InjectImportService.BASE_DIR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.openaev.rest.exception.BadRequestException;
import io.openaev.utils.SecurityUtils;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("File Security Utils tests")
class SecurityUtilsTest {

  @DisplayName("Test getSanitizedExtension with valid xlsx file")
  @Test
  void getSanitizedExtension_whenValidXlsxFile_thenReturnsXlsxExtension() {
    // -- PREPARE --
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("report.xlsx");

    // -- EXECUTE --
    String extension = SecurityUtils.getSanitizedExtension(file);

    // -- ASSERT --
    assertEquals("xlsx", extension);
  }

  @DisplayName("Test getSanitizedExtension with valid xls file")
  @Test
  void getSanitizedExtension_whenValidXlsFile_thenReturnsXlsExtension() {
    // -- PREPARE --
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("report.xls");

    // -- EXECUTE --
    String extension = SecurityUtils.getSanitizedExtension(file);

    // -- ASSERT --
    assertEquals("xls", extension);
  }

  @DisplayName("Test getSanitizedExtension with uppercase extension")
  @Test
  void getSanitizedExtension_whenUppercaseExtension_thenReturnsLowercaseExtension() {
    // -- PREPARE --
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("REPORT.XLSX");

    // -- EXECUTE --
    String extension = SecurityUtils.getSanitizedExtension(file);

    // -- ASSERT --
    assertEquals("xlsx", extension);
  }

  @DisplayName("Test getSanitizedExtension with path traversal attempt in filename")
  @Test
  void getSanitizedExtension_whenPathTraversalAttempt_thenReturnsExtensionSafely() {
    // -- PREPARE --
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("../../etc/passwd.xlsx");

    // -- EXECUTE --
    String extension = SecurityUtils.getSanitizedExtension(file);

    // -- ASSERT --
    assertEquals("xlsx", extension);
  }

  @DisplayName("Test getSanitizedExtension with invalid file type throws exception")
  @Test
  void getSanitizedExtension_whenInvalidFileType_thenThrowsBadRequestException() {
    // -- PREPARE --
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("malicious.exe");

    // -- EXECUTE & ASSERT --
    assertThrows(BadRequestException.class, () -> SecurityUtils.getSanitizedExtension(file));
  }

  @DisplayName("Test getSanitizedExtension with null filename throws exception")
  @Test
  void getSanitizedExtension_whenNullFilename_thenThrowsBadRequestException() {
    // -- PREPARE --
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(null);

    // -- EXECUTE & ASSERT --
    assertThrows(BadRequestException.class, () -> SecurityUtils.getSanitizedExtension(file));
  }

  @DisplayName("Test getSanitizedExtension with empty filename throws exception")
  @Test
  void getSanitizedExtension_whenEmptyFilename_thenThrowsBadRequestException() {
    // -- PREPARE --
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("");

    // -- EXECUTE & ASSERT --
    assertThrows(BadRequestException.class, () -> SecurityUtils.getSanitizedExtension(file));
  }

  @DisplayName("Test getSanitizedExtension with no extension throws exception")
  @Test
  void getSanitizedExtension_whenNoExtension_thenThrowsBadRequestException() {
    // -- PREPARE --
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("filename");

    // -- EXECUTE & ASSERT --
    assertThrows(BadRequestException.class, () -> SecurityUtils.getSanitizedExtension(file));
  }

  @DisplayName("Test validatePathTraversal accepts valid sub-path")
  @Test
  void validatePathTraversal_whenValidSubPath_thenReturnsResolvedPath() {
    // -- PREPARE --
    String validSubPath = "subdir";

    // -- EXECUTE --
    Path result = SecurityUtils.validatePathTraversal(BASE_DIR, validSubPath);

    // -- ASSERT --
    assertNotNull(result);
    assertTrue(result.startsWith(Path.of(BASE_DIR).normalize()));
  }

  @DisplayName("Test validatePathTraversal rejects path traversal with ..")
  @Test
  void validatePathTraversal_whenPathTraversalAttempt_thenThrowsBadRequestException() {
    // -- PREPARE --
    String pathTraversal = "../../../etc/passwd";

    // -- EXECUTE & ASSERT --
    assertThrows(
        BadRequestException.class,
        () -> SecurityUtils.validatePathTraversal(BASE_DIR, pathTraversal));
  }

  @DisplayName("Test validatePathTraversal rejects escaping path")
  @Test
  void validatePathTraversal_whenEscapingPath_thenThrowsBadRequestException() {
    // -- PREPARE --
    String escapingPath = "valid/../../../etc/passwd";

    // -- EXECUTE & ASSERT --
    assertThrows(
        BadRequestException.class,
        () -> SecurityUtils.validatePathTraversal(BASE_DIR, escapingPath));
  }

  @DisplayName("Test validatePathTraversal accepts nested valid path")
  @Test
  void validatePathTraversal_whenNestedValidPath_thenReturnsResolvedPath() {
    // -- PREPARE --
    String nestedPath = "dir1/dir2/file.txt";

    // -- EXECUTE --
    Path result = SecurityUtils.validatePathTraversal(BASE_DIR, nestedPath);

    // -- ASSERT --
    assertNotNull(result);
    assertTrue(result.startsWith(Path.of(BASE_DIR).normalize()));
  }

  @Test
  @DisplayName("Test to get the JFrog URL")
  void testGetJFrogUrl() throws MalformedURLException {
    URI uri = SecurityUtils.validateJFrogUri("/path/to/", "jfrog.agent");

    assertNotNull(uri);
    assertEquals("https://filigran.jfrog.io/artifactory/path/to/jfrog.agent", uri.toString());
  }

  @ParameterizedTest
  @CsvSource({
    "'', '..'",
    "'/not/a/../path/to/', 'jfrog.agent'",
    "'/not/a/../../path/to/', 'jfrog.agent'",
    "'/not/a/path/to/', '..'",
    "'/not/a/path/to/', 'jfrog..agent'",
    "'/not/a/..%2F../path/to/', 'jfrog.agent'",
    "'/not/a/path/to/', 'jfrog..agent'",
    "'/not/a/../../path/to/', 'jfrog.agent'",
    ", 'jfrog.agent'",
    "'/not/a/path/to/',",
    "'/not/a/\\/path/to/', 'jfrog.agent'",
    "'/not/a/%\2e%\2e/path/to/', 'jfrog.agent'",
  })
  void shouldThrowSecurityExceptionForTraversalPathAttempt(String path, String filename) {
    assertThrows(SecurityException.class, () -> SecurityUtils.validateJFrogUri(path, filename));
  }
}
