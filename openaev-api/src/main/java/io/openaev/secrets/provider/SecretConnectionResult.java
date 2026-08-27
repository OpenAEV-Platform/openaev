package io.openaev.secrets.provider;

import io.openaev.database.model.SecretReference.SECRET_STATUS;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of a credential check. {@link #statusToPersist()} only writes ACTIVE/INACTIVE;
 * UNKNOWN/UNSUPPORTED preserve the existing status. {@code checked} tracks whether a validator
 * actually ran (independent of the outcome), so unreachable-but-probed secrets get re-timestamped
 * while never-probed ones aren't falsely marked verified. {@code detail} must stay non-sensitive.
 */
public record SecretConnectionResult(OUTCOME outcome, String detail, boolean checked) {

  public enum OUTCOME {
    ACTIVE,
    INACTIVE,
    UNKNOWN,
    UNSUPPORTED
  }

  public SecretConnectionResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
  }

  /** The credential answered and is usable. */
  public static SecretConnectionResult active() {
    return new SecretConnectionResult(OUTCOME.ACTIVE, null, true);
  }

  /** The provider explicitly rejected the credential (bad secret, revoked, unauthorized). */
  public static SecretConnectionResult inactive(String detail) {
    return new SecretConnectionResult(OUTCOME.INACTIVE, detail, true);
  }

  /**
   * A validator ran but could not conclude; the previously known status must be kept, and the
   * attempt is still recorded.
   */
  public static SecretConnectionResult unknown(String detail) {
    return new SecretConnectionResult(OUTCOME.UNKNOWN, detail, true);
  }

  /**
   * No validator ever ran (dangling secret, no handler): inconclusive AND not verified, so the
   * reference is left completely untouched.
   */
  public static SecretConnectionResult notChecked(String detail) {
    return new SecretConnectionResult(OUTCOME.UNKNOWN, detail, false);
  }

  /** No validator implemented for this secret type: the default for every handler. */
  public static SecretConnectionResult unsupported() {
    return new SecretConnectionResult(OUTCOME.UNSUPPORTED, null, false);
  }

  /**
   * The status to write, or empty when the previous status must be preserved.
   *
   * @return the status to persist, empty for {@code UNKNOWN} and {@code UNSUPPORTED}
   */
  public Optional<SECRET_STATUS> statusToPersist() {
    return switch (outcome) {
      case ACTIVE -> Optional.of(SECRET_STATUS.ACTIVE);
      case INACTIVE -> Optional.of(SECRET_STATUS.INACTIVE);
      case UNKNOWN, UNSUPPORTED -> Optional.empty();
    };
  }

  /**
   * Whether a validator actually ran. A secret that could not be loaded, an unsupported type or a
   * missing handler was never checked, so it must not be stamped as verified.
   *
   * @return true when the credential was actually probed
   */
  public boolean wasChecked() {
    return checked;
  }
}
