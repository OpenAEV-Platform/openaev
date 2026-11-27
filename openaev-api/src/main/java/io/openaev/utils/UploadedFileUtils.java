package io.openaev.utils;

import io.openaev.rest.exception.FileTooBigException;
import java.util.List;
import org.apache.commons.io.FilenameUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;

public final class UploadedFileUtils {

  private UploadedFileUtils() {}

  /**
   * Refuses a file whose extension is not accepted (case-insensitive) or whose size is above the
   * limit.
   *
   * @param maxSizeLabel the limit as the client reads it in the error message, e.g. {@code "25 Mb"}
   * @throws UnsupportedMediaTypeException when the extension is not accepted
   * @throws FileTooBigException when the file is larger than {@code maxSizeBytes}
   */
  public static void validate(
      MultipartFile file, List<String> acceptedExtensions, long maxSizeBytes, String maxSizeLabel) {
    String extension = FilenameUtils.getExtension(file.getOriginalFilename());
    if (acceptedExtensions.stream().noneMatch(accepted -> accepted.equalsIgnoreCase(extension))) {
      // The frontend translates these messages by their exact text.
      String subject = acceptedExtensions.size() == 1 ? "file type is" : "file types are";
      throw new UnsupportedMediaTypeException(
          "Only the following " + subject + " accepted : " + String.join(", ", acceptedExtensions));
    }
    if (file.getSize() > maxSizeBytes) {
      throw new FileTooBigException("File size cannot be greater than " + maxSizeLabel);
    }
  }
}
