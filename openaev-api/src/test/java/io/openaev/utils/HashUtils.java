package io.openaev.utils;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {
  /**
   * Gets a sha256 hex digest from a byte array
   *
   * @param bytes the array to hash
   * @return sha256 hex digest string
   * @throws NoSuchAlgorithmException
   */
  public static String getSha256HexDigest(byte[] bytes) throws NoSuchAlgorithmException {
    return bytesToHex(MessageDigest.getInstance("SHA-256").digest(bytes));
  }

  /**
   * Gets a sha256 hex digest from an arbitrary resource file path
   *
   * @param path path to the file to hash
   * @return sha256 hex digest string
   * @throws Exception
   */
  public static String getSha256HexDigest(String path) throws Exception {
    try (InputStream inputStream = HashUtils.class.getResourceAsStream(path)) {
      return HashUtils.getSha256HexDigest(inputStream.readAllBytes());
    }
  }

  private static String bytesToHex(byte[] hash) {
    StringBuilder hexString = new StringBuilder(2 * hash.length);
    for (byte b : hash) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }
}
