package io.openaev.database.model;

/**
 * AWS account id shared by every AWS credential subtype.
 *
 * <p>Both {@link AwsAccessKeySecret} and {@link AwsAssumeRoleSecret} carry an account id, and the
 * form contract describes it the same way for both, so the description lives here rather than being
 * duplicated in each contract.
 */
public final class AwsAccountId {

  /** Hint shown under the account id input, as an i18n key. */
  public static final String FORM_DESCRIPTION = "This value is mandatory for some injectors";

  private AwsAccountId() {}
}
