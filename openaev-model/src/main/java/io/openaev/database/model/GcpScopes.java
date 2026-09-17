package io.openaev.database.model;

/**
 * GCP OAuth scopes shared by every GCP credential subtype.
 *
 * <p>Both {@link GcpServiceAccountSecret} and {@link GcpOAuth2Secret} carry a scope, and the form
 * contract offers the same default for both, so the value lives here rather than being duplicated
 * in each handler.
 */
public final class GcpScopes {

  /**
   * Google's catch-all scope: full access to the Cloud Platform APIs the identity is entitled to.
   * It is what the console suggests first and what the form pre-fills.
   */
  public static final String DEFAULT_CLOUD_PLATFORM =
      "https://www.googleapis.com/auth/cloud-platform";

  private GcpScopes() {}
}
