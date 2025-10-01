package io.openaev.stix.types.enums;

public enum HashingAlgorithms {
  MD5("MD5"),
  SHA1("SHA-1"),
  SHA256("SHA-256"),
  SHA512("SHA-512"),
  SHA3256("SHA3-256"),
  SHA3512("SHA3-512"),
  SSDEEP("SSDEEP"),
  TLSH("TLSH"),
  ;

  public final String value;

  HashingAlgorithms(String value) {
    this.value = value;
  }

  public static HashingAlgorithms fromValue(String value) {
    return switch (value) {
      case "MD5" -> MD5;
      case "SHA-1" -> SHA1;
      case "SHA-256" -> SHA256;
      case "SHA-512" -> SHA512;
      case "SHA3-256" -> SHA3256;
      case "SHA3-512" -> SHA3512;
      case "SSDEEP" -> SSDEEP;
      case "TLSH" -> TLSH;
      default -> throw new IllegalArgumentException("Unknown HashingAlgorithms value: " + value);
    };
  }
}
