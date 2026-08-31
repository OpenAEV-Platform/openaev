package io.openaev.secrets.provider;

import io.openaev.database.model.SecretReference.SECRET_STATUS;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of a credential check.
 *
 * <p>{@code status} is the canonical status model persisted on {@code SecretReference}. {@code
 * checked} tracks whether a validator actually ran, so dangling or unresolvable references stay
 * untouched.
 */
public record SecretConnectionResult(SECRET_STATUS status, boolean checked) {

  public SecretConnectionResult {
    Objects.requireNonNull(status, "status must not be null");
  }

  /** The credential answered and is usable. */
  public static SecretConnectionResult active() {
    return new SecretConnectionResult(SECRET_STATUS.ACTIVE, true);
  }

  /** The provider explicitly rejected the credential (bad secret, expired or revoked). */
  public static SecretConnectionResult authFailed() {
    return new SecretConnectionResult(SECRET_STATUS.AUTH_FAILED, true);
  }

  /** The credential authenticated but lacks permissions for the probe. */
  public static SecretConnectionResult permissionDenied() {
    return new SecretConnectionResult(SECRET_STATUS.PERMISSION_DENIED, true);
  }

  /** The validator could not conclude within the timeout budget. */
  public static SecretConnectionResult timeout() {
    return new SecretConnectionResult(SECRET_STATUS.TIMEOUT, true);
  }

  /** The validator could not conclude due to transient network issues. */
  public static SecretConnectionResult networkError() {
    return new SecretConnectionResult(SECRET_STATUS.NETWORK_ERROR, true);
  }

  /** Stored secret configuration is malformed or incomplete. */
  public static SecretConnectionResult formatError() {
    return new SecretConnectionResult(SECRET_STATUS.FORMAT_ERROR, true);
  }

  /** A validator ran but no definitive diagnosis could be established. */
  public static SecretConnectionResult unknown() {
    return new SecretConnectionResult(SECRET_STATUS.UNKNOWN, true);
  }

  /**
   * No validator ever ran (dangling secret, no handler): inconclusive AND not verified, so the
   * reference is left completely untouched.
   */
  public static SecretConnectionResult notChecked() {
    return new SecretConnectionResult(SECRET_STATUS.UNSET, false);
  }

  /** No validator implemented for this secret type: the default for every handler. */
  public static SecretConnectionResult unsupported() {
    return new SecretConnectionResult(SECRET_STATUS.UNSUPPORTED, true);
  }

  /**
   * The status to write, or empty when no check actually ran.
   *
   * @return the status to persist, empty when {@link #wasChecked()} is false
   */
  public Optional<SECRET_STATUS> statusToPersist() {
    return checked ? Optional.of(status) : Optional.empty();
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
