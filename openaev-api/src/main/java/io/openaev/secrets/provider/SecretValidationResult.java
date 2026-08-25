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
 * <p>{@code checked} is deliberately NOT derived from the outcome: an {@code UNKNOWN} covers two
 * situations that must be persisted differently. A provider that answered badly (timeout, 5xx) WAS
 * checked, and stamping {@code lastVerifiedAt} is what stops a permanently unreachable provider
 * from pinning the same rows at the head of every run. A dangling secret or a missing handler, on
 * the other hand, never reached a validator at all — stamping those would mark as "verified" a row
 * nothing ever looked at. Use {@link #notChecked(String)} for the latter.
 *
 * <p>{@code detail} is a short, NON-SENSITIVE, normalized reason code (e.g. {@code AUTH_REJECTED},
 * {@code TIMEOUT}). It must never carry a client secret, a token, or a raw provider error payload:
 * those messages routinely embed tenant and application identifiers.
 */
public record SecretValidationResult(OUTCOME outcome, String detail, boolean checked) {

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
    return new SecretValidationResult(OUTCOME.ACTIVE, null, true);
  }

  /** The provider explicitly rejected the credential (bad secret, revoked, unauthorized). */
  public static SecretValidationResult inactive(String detail) {
    return new SecretValidationResult(OUTCOME.INACTIVE, detail, true);
  }

  /**
   * A validator ran but could not conclude; the previously known status must be kept, and the
   * attempt is still recorded.
   */
  public static SecretValidationResult unknown(String detail) {
    return new SecretValidationResult(OUTCOME.UNKNOWN, detail, true);
  }

  /**
   * No validator ever ran (dangling secret, no handler): inconclusive AND not verified, so the
   * reference is left completely untouched.
   */
  public static SecretValidationResult notChecked(String detail) {
    return new SecretValidationResult(OUTCOME.UNKNOWN, detail, false);
  }

  /** No validator implemented for this secret type: the default for every handler. */
  public static SecretValidationResult unsupported() {
    return new SecretValidationResult(OUTCOME.UNSUPPORTED, null, false);
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
