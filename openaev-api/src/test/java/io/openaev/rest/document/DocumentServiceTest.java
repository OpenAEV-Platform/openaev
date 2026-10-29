package io.openaev.rest.document;

import static io.openaev.rest.document.DocumentService.DEFAULT_DOWNLOAD_FILE_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.openaev.rest.exception.BadRequestException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("DocumentService Tests")
class DocumentServiceTest {

  @Nested
  @DisplayName("Document file name sanitization (path traversal)")
  class DocumentFileNameSanitization {

    private static Stream<Arguments> namesCarryingAPath() {
      return Stream.of(
          Arguments.of("../../ba03_direct.txt", "ba03_direct.txt"),
          Arguments.of("..\\..\\evil.exe", "evil.exe"),
          Arguments.of("/etc/cron.d/evil", "evil"),
          Arguments.of("C:\\Windows\\System32\\evil.dll", "evil.dll"),
          // Stays literal: one percent-decoding on the agent side gives back "..%2F..%2F..."
          Arguments.of("..%2F..%2Fba03_encoded.txt", "..%252F..%252Fba03_encoded.txt"));
    }

    @ParameterizedTest(name = "\"{0}\" should encode to \"{1}\"")
    @MethodSource("namesCarryingAPath")
    @DisplayName("Given names carrying a path should encode only the last component")
    void given_names_carrying_a_path_should_encode_only_the_last_component(
        String name, String expected) {
      // Act
      String encoded = DocumentService.encodeFileName(name);

      // Assert
      assertEquals(expected, encoded);
    }

    @ParameterizedTest(name = "\"{0}\" should encode to the default name")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "folder/", "..\\"})
    @DisplayName("Given names with no last component should encode a default name")
    void given_names_with_no_last_component_should_encode_a_default_name(String name) {
      // Act
      String encoded = DocumentService.encodeFileName(name);

      // Assert
      assertEquals(DEFAULT_DOWNLOAD_FILE_NAME, encoded);
    }

    @Test
    @DisplayName("Given an uploaded file name with no last component should reject the upload")
    void given_uploaded_file_name_with_no_last_component_should_reject_the_upload() {
      // Act & Assert
      assertThrows(BadRequestException.class, () -> DocumentService.sanitizeFileName("folder/"));
    }
  }
}
