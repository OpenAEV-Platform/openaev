package io.openaev.utils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.rest.exception.FileTooBigException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

@DisplayName("UploadedFileUtils: the extension and the size of an uploaded file")
class UploadedFileUtilsTest {

  private static final List<String> CSV = List.of("csv");
  private static final long LIMIT = 1024;

  private static MultipartFile file(String name, int size) {
    return new MockMultipartFile("file", name, "application/octet-stream", new byte[size]);
  }

  @Test
  @DisplayName("given_anAcceptedExtensionInAnyCaseUpToTheLimit_should_accept")
  void given_anAcceptedExtensionInAnyCaseUpToTheLimit_should_accept() {
    // Act & Assert
    assertDoesNotThrow(
        () -> UploadedFileUtils.validate(file("Markings.CSV", (int) LIMIT), CSV, LIMIT, "1 Kb"));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"markings.txt", "markings", "csv", "markings.csv.exe"})
  @DisplayName("given_anotherOrNoExtension_should_refuseAsUnsupportedMediaType")
  void given_anotherOrNoExtension_should_refuseAsUnsupportedMediaType(String name) {
    // Act & Assert
    assertThrows(
        UnsupportedMediaTypeException.class,
        () -> UploadedFileUtils.validate(file(name, 1), CSV, LIMIT, "1 Kb"));
  }

  @Test
  @DisplayName("given_aFileLargerThanTheLimit_should_refuseAsTooBigWithTheLabelOfTheLimit")
  void given_aFileLargerThanTheLimit_should_refuseAsTooBigWithTheLabelOfTheLimit() {
    // Act
    FileTooBigException thrown =
        assertThrows(
            FileTooBigException.class,
            () ->
                UploadedFileUtils.validate(
                    file("markings.csv", (int) LIMIT + 1), CSV, LIMIT, "1 Kb"));

    // Assert
    assertEquals("File size cannot be greater than 1 Kb", thrown.getMessage());
  }

  @Test
  @DisplayName("given_oneOrSeveralExtensions_should_phraseTheRefusalAsTheFrontendTranslatesIt")
  void given_oneOrSeveralExtensions_should_phraseTheRefusalAsTheFrontendTranslatesIt() {
    // Act
    UnsupportedMediaTypeException singular =
        assertThrows(
            UnsupportedMediaTypeException.class,
            () -> UploadedFileUtils.validate(file("a.txt", 1), CSV, LIMIT, "1 Kb"));
    UnsupportedMediaTypeException plural =
        assertThrows(
            UnsupportedMediaTypeException.class,
            () ->
                UploadedFileUtils.validate(
                    file("a.txt", 1), List.of("xls", "xlsx"), LIMIT, "1 Kb"));

    // Assert - the language files key on these exact texts
    assertEquals("Only the following file type is accepted : csv", singular.getMessage());
    assertEquals("Only the following file types are accepted : xls, xlsx", plural.getMessage());
  }
}
