package io.openaev.secrets.provider;

import io.openaev.database.model.SecretReference.SECRET_STATUS;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of a credential liveness check, as returned by {@code
 * SecretHandler#validateConnection(Secret)}.
 *
 * <p>The platform's {@link SECRET_STATUS} enum only knows {@code ACTIVE}, {@code INACTIVE} and
 * {@code UNSET}, which is deliberately narrower than what a validation run can conclude. This
 * record therefore carries its own {@link OUTCOME} and exposes {@link #statusToPersist()} as the
 * single place deciding what — if anything — reaches the database:
 *
 * <ul>
 *   <li>{@code ACTIVE} / {@code INACTIVE}: a definitive answer from the provider, persisted.
 *   <li>{@code UNKNOWN}: the check could not conclude (timeout, throttling, network, 5xx). Nothing
 *       is persisted, so a transient outage never flips a valid credential to {@code INACTIVE}.
 *   <li>{@code UNSUPPORTED}: no validator exists for this secret type. Nothing is persisted, and
 *       the reference is not even considered "verified".
 * </ul>
 *
 * <p>{@code detail} is a short, NON-SENSITIVE, normalized reason code (e.g. {@code AUTH_REJECTED},
 * {@code TIMEOUT}). It must never carry a client secret, a token, or a raw provider error payload:
 * those messages routinely embed tenant and application identifiers.
 */
public record SecretValidationResult(OUTCOME outcome, String detail) {

  public enum OUTCOME {
    ACTIVE,
    INACTIVE,
    UNKNOWN,
    UNSUPPORTED
  }

  public SecretValidationResult {
    Objects.requireNonNull(outcome, "outcome must not be null");
  }

  /** The credential answered and is usable. */
  public static SecretValidationResult active() {
    return new SecretValidationResult(OUTCOME.ACTIVE, null);
  }

  /** The provider explicitly rejected the credential (bad secret, revoked, unauthorized). */
  public static SecretValidationResult inactive(String detail) {
    return new SecretValidationResult(OUTCOME.INACTIVE, detail);
  }

  /** The check could not conclude; the previously known status must be kept. */
  public static SecretValidationResult unknown(String detail) {
    return new SecretValidationResult(OUTCOME.UNKNOWN, detail);
  }

  /** No validator implemented for this secret type: the default for every handler. */
  public static SecretValidationResult unsupported() {
    return new SecretValidationResult(OUTCOME.UNSUPPORTED, null);
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
   * Whether a validator actually ran. An unsupported secret type was never checked, so it must not
   * be stamped as verified.
   *
   * @return true unless the outcome is {@code UNSUPPORTED}
   */
  public boolean wasChecked() {
    return outcome != OUTCOME.UNSUPPORTED;
  }
}
