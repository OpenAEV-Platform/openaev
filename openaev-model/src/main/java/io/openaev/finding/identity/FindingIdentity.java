package io.openaev.finding.identity;

import static io.openaev.helper.CryptoHelper.hashWithSHA256;

import io.openaev.database.model.ContractOutputType;
import java.util.Objects;

/**
 * What makes two detections the same finding, with the location: the type and the hash of the
 * normalized value. The hash, not the value, goes into the unique key of {@code findings}: fixed
 * size, and no cleartext secret in an index (ADR-017).
 *
 * @param type the contract output type
 * @param normalizedValue the cleartext normalized value, never persisted nor logged
 * @param hash the SHA-256 of the normalized value, lower-case hex
 */
public record FindingIdentity(ContractOutputType type, String normalizedValue, String hash) {

  public FindingIdentity {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(normalizedValue, "normalizedValue");
    Objects.requireNonNull(hash, "hash");
  }

  /**
   * Computes the identity of a cleartext finding value detected at a location.
   *
   * @param type the contract output type of the finding
   * @param value the cleartext value, as built by the output processor
   * @param location where the finding was detected
   * @return the identity, whose hash is the {@code finding_identity_hash} column
   */
  public static FindingIdentity of(
      ContractOutputType type, String value, FindingLocation location) {
    String normalized = FindingValueNormalizer.normalize(type, value, location);
    return new FindingIdentity(type, normalized, hashWithSHA256(normalized));
  }

  @Override
  public String toString() {
    return "FindingIdentity[type=" + type + ", hash=" + hash + "]";
  }
}
