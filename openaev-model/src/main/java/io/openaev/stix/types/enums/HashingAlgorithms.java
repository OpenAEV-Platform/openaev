package io.openaev.stix.types.enums;

import java.util.Set;

public enum HashingAlgorithms {
  MD5(Set.of("md5")),
  SHA1(Set.of("sha-1", "sha1")),
  SHA256(Set.of("sha-256", "sha256")),
  SHA512(Set.of("sha-512", "sha512")),
  SHA3256(Set.of("sha3-256", "sha3256")),
  SHA3512(Set.of("sha3-512", "sha3512")),
  SSDEEP(Set.of("ssdeep")),
  TLSH(Set.of("tlsh")),
  ;

  public final Set<String> values;

  HashingAlgorithms(Set<String> values) {
    this.values = values;
  }

  public static HashingAlgorithms fromValue(String value) {
    for (HashingAlgorithms algo : HashingAlgorithms.values()) {
      if (algo.values.contains(value.toLowerCase())) {
        return algo;
      }
    }
    throw new IllegalArgumentException("Unknown HashingAlgorithms value: " + value);
  }
}
