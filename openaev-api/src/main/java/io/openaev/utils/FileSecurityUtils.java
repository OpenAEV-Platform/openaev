package io.openaev.utils;

import io.openaev.rest.exception.BadRequestException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.multipart.MultipartFile;

public class FileSecurityUtils {

  private FileSecurityUtils() {
    // Utility class
  }

  /**
   * Extracts and validates the file extension from a MultipartFile. This method sanitizes the
   * filename by removing any path components to prevent path traversal attacks, then validates that
   * the extension is an allowed type.
   *
   * @param file the MultipartFile to extract the extension from
   * @return the validated lowercase file extension (e.g., "xls" or "xlsx")
   * @throws BadRequestException if the filename is null/empty, has no extension, or the extension
   *     is not in the allowed list (xls, xlsx)
   */
  public static String getSanitizedExtension(MultipartFile file) {
    String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("");
    String baseName = FilenameUtils.getName(originalFilename); // Removes any path components
    String extension = FilenameUtils.getExtension(baseName).toLowerCase();

    if (!List.of("xls", "xlsx").contains(extension.toLowerCase())) {
      throw new BadRequestException("Invalid file type");
    }
    return extension;
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
