package io.openaev.helper;

import java.security.MessageDigest;

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
}
