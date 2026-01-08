package io.openaev.helper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CryptoHelper {

  private static String hex(byte[] array) {
    StringBuilder sb = new StringBuilder();
    for (byte b : array) {
      sb.append(Integer.toHexString((b & 0xFF) | 0x100), 1, 3);
    }
    return sb.toString();
  }

  public static String md5Hex(String message) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      return hex(md.digest(message.getBytes("CP1252"))).toLowerCase();
    } catch (Exception e) {
      // Nothing to do
    }
    return null;
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
