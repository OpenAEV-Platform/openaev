package io.openaev.utils;

import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.Base64;

/**
 * Utility class for image processing operations.
 *
 * <p>Provides methods for downloading and encoding images, primarily used for handling external
 * image resources that need to be stored or transmitted as Base64 strings.
 *
 * <p>This is a utility class and cannot be instantiated.
 */
public class ImageUtils {

  private ImageUtils() {}

  /** Connection timeout in milliseconds for image downloads (10 seconds). */
  private static final int CONNECTION_TIMEOUT_MS = 10000;

  /** Read timeout in milliseconds for image downloads (30 seconds). */
  private static final int READ_TIMEOUT_MS = 30000;

  /**
   * Downloads an image from a URL and encodes it as a Base64 string.
   *
   * <p>This method establishes a connection to the specified URL with configured timeouts,
   * downloads the image content, and returns it as a Base64-encoded string suitable for embedding
   * or storage.
   *
   * @param imageUrl the URL of the image to download (must not be blank)
   * @return the Base64-encoded string representation of the image
   * @throws RuntimeException if the image cannot be downloaded due to I/O errors
   * @throws RuntimeException if the URL is malformed or invalid
   */
  public static String downloadImageAndEncodeBase64(final @NotBlank String imageUrl) {
    try {
      URLConnection connection = new URI(imageUrl).toURL().openConnection();
      connection.setConnectTimeout(CONNECTION_TIMEOUT_MS);
      connection.setReadTimeout(READ_TIMEOUT_MS);

      if (connection instanceof HttpURLConnection httpConnection) {
        httpConnection.setRequestMethod("GET");
      }

      try (InputStream inputStream = connection.getInputStream()) {
        byte[] imageBytes = inputStream.readAllBytes();
        return Base64.getEncoder().encodeToString(imageBytes);
      }
    } catch (IOException e) {
      throw new RuntimeException("Error while downloading image from " + imageUrl, e);
    } catch (URISyntaxException e) {
      throw new RuntimeException("Invalid image URL: " + imageUrl, e);
    }
  }
}
