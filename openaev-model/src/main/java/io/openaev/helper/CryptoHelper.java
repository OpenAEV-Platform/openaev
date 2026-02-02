package io.openaev.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for cryptographic operations.
 *
 * <p>This class provides helper methods for generating hashes and other cryptographic operations
 * used throughout the application, such as generating Gravatar URLs from email addresses.
 *
 * <p>Note: MD5 is used only for non-security-critical purposes (e.g., Gravatar). For
 * security-sensitive hashing, use stronger algorithms.
 */
public class CryptoHelper {

  private CryptoHelper() {
    // Utility class - prevent instantiation
  }

  /**
   * Converts a byte array to its hexadecimal string representation.
   *
   * @param array the byte array to convert
   * @return the lowercase hexadecimal string
   */
  private static String hex(byte[] array) {
    StringBuilder sb = new StringBuilder();
    for (byte b : array) {
      sb.append(Integer.toHexString((b & 0xFF) | 0x100), 1, 3);
    }
    return sb.toString();
  }

  /**
   * Computes the MD5 hash of a string and returns it as a lowercase hexadecimal string.
   *
   * <p>This method is primarily used for generating Gravatar URLs from email addresses. UTF-8
   * encoding is used to ensure consistent hashing across all Unicode characters.
   *
   * @param message the string to hash
   * @return the MD5 hash as a lowercase hexadecimal string
   * @throws RuntimeException if the MD5 algorithm is not available
   */
  public static String md5Hex(String message) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return hex(md.digest(message.getBytes(StandardCharsets.UTF_8))).toLowerCase();
    } catch (Exception e) {
      throw new RuntimeException("Failed to compute MD5 hash", e);
    }
  }

  public static String hashWithSHA256(String text) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));

      // Convert byte array to hex string
      StringBuilder hexString = new StringBuilder(2 * hash.length);
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) {
          hexString.append('0');
        }
        hexString.append(hex);
      }
      return hexString.toString();

    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
