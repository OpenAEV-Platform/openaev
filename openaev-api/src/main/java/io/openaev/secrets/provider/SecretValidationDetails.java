package io.openaev.secrets.provider;

/**
 * Normalized, non-sensitive reason codes carried by {@link SecretValidationResult#detail()}.
 *
 * <p>Provider SDK messages are NOT usable as-is: an Azure {@code AADSTS} payload or an AWS error
 * body routinely embeds tenant, application and principal identifiers. Validators map their
 * failures onto one of these codes instead, so the value is safe to log and to expose.
 */
public final class SecretValidationDetails {

  /** The secret referenced by {@code secret_reference_location} could not be loaded. */
  public static final String SECRET_NOT_FOUND = "SECRET_NOT_FOUND";

  /** No {@code SecretHandler} claims this secret type. */
  public static final String HANDLER_NOT_FOUND = "HANDLER_NOT_FOUND";

  /** The validator threw unexpectedly; treated as inconclusive, never as a rejection. */
  public static final String VALIDATOR_ERROR = "VALIDATOR_ERROR";

  /** The provider explicitly refused the credential (bad, expired or revoked). */
  public static final String AUTH_REJECTED = "AUTH_REJECTED";

  /** The credential is valid but lacks the permission needed to complete the probe. */
  public static final String AUTH_FORBIDDEN = "AUTH_FORBIDDEN";

  /** The provider did not answer within the configured budget. */
  public static final String TIMEOUT = "TIMEOUT";

  /** The provider rate-limited the probe (e.g. HTTP 429). */
  public static final String THROTTLED = "THROTTLED";

  /** The provider endpoint could not be reached (network error, IMDS unavailable, 5xx). */
  public static final String UNREACHABLE = "UNREACHABLE";

  /** The stored configuration is invalid (unknown cloud, missing required field). */
  public static final String INVALID_CONFIGURATION = "INVALID_CONFIGURATION";

  private SecretValidationDetails() {}
}
