package io.openaev.utils;

import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.Base64;

public class ImageUtils {

  private ImageUtils() {}

  private static final int CONNECTION_TIMEOUT_MS = 10000;
  private static final int READ_TIMEOUT_MS = 30000;

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
