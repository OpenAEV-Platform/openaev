package io.openaev.utils;

import io.openaev.rest.exception.BadRequestException;
import java.nio.file.Path;

public class PathValidationUtils {

  private PathValidationUtils() {
    // Utility class
  }

  /**
   * Validates that the resolved path does not escape the base directory.
   *
   * @param baseDir the base directory path
   * @param subPath the sub-path to validate
   * @return the validated and normalized path
   * @throws BadRequestException if the path escapes the base directory
   */
  public static Path validatePathTraversal(String baseDir, String subPath) {
    Path basePath = Path.of(baseDir).toAbsolutePath().normalize();
    Path resolvedPath = basePath.resolve(subPath).normalize();

    if (!resolvedPath.startsWith(basePath)) {
      throw new BadRequestException("Invalid import ID. It could contain illegal path sequences.");
    }

    return resolvedPath;
  }
}
