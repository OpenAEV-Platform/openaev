package io.openaev.utils;

import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;

public class ImageUtils {
  public static String downloadImageAndEncodeBase64(final @NotBlank String imageUrl) {
    try (InputStream inputStream = new URI(imageUrl).toURL().openStream()) {
      byte[] imageBytes = inputStream.readAllBytes();
      return Base64.getEncoder().encodeToString(imageBytes);
    } catch (IOException e) {
      throw new RuntimeException("error while downloading image from " + imageUrl, e);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }
}
