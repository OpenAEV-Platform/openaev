package io.openaev.utilstest;

import static io.openaev.service.InjectImportService.BASE_DIR;
import static org.junit.jupiter.api.Assertions.*;

import io.openaev.rest.exception.BadRequestException;
import io.openaev.utils.PathValidationUtils;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Path Validation Utils tests")
class PathValidationUtilsTest {

  @DisplayName("Test validatePathTraversal accepts valid sub-path")
  @Test
  void testvalidatePathTraversalAcceptsValidSubPath() {
    // -- PREPARE --
    String validSubPath = "subdir";

    // -- EXECUTE --
    Path result = PathValidationUtils.validatePathTraversal(BASE_DIR, validSubPath);

    // -- ASSERT --
    assertNotNull(result);
    assertTrue(result.startsWith(Path.of(BASE_DIR).normalize()));
  }

  @DisplayName("Test validatePathTraversal rejects path traversal with ..")
  @Test
  void testvalidatePathTraversalRejectsPathTraversal() {
    // -- PREPARE --
    String pathTraversal = "../../../etc/passwd";

    // -- EXECUTE & ASSERT --
    assertThrows(
        BadRequestException.class,
        () -> PathValidationUtils.validatePathTraversal(BASE_DIR, pathTraversal));
  }

  @DisplayName("Test validatePathTraversal rejects escaping path")
  @Test
  void testvalidatePathTraversalRejectsEscapingPath() {
    // -- PREPARE --
    String escapingPath = "valid/../../../etc/passwd";

    // -- EXECUTE & ASSERT --
    assertThrows(
        BadRequestException.class,
        () -> PathValidationUtils.validatePathTraversal(BASE_DIR, escapingPath));
  }

  @DisplayName("Test validatePathTraversal accepts nested valid path")
  @Test
  void testvalidatePathTraversalAcceptsNestedPath() {
    // -- PREPARE --
    String nestedPath = "dir1/dir2/file.txt";

    // -- EXECUTE --
    Path result = PathValidationUtils.validatePathTraversal(BASE_DIR, nestedPath);

    // -- ASSERT --
    assertNotNull(result);
    assertTrue(result.startsWith(Path.of(BASE_DIR).normalize()));
  }
}
