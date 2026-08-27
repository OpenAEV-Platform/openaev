package io.openaev.utils.fixtures;

import io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE;
import io.openaev.database.model.AwsRegion;
import io.openaev.secrets.provider.SecretStoreRequest;

/** Fixtures for {@link SecretStoreRequest} payloads used by the secret handlers. */
public class SecretStoreRequestFixture {

  public static final AwsRegion AWS_DEFAULT_REGION = AwsRegion.EU_WEST_3;
  public static final AwsRegion AWS_OTHER_REGION = AwsRegion.US_EAST_1;

  public static final String AWS_ACCESS_KEY_ID = "AKIAIOSFODNN7EXAMPLE";
  public static final String AWS_OTHER_ACCESS_KEY_ID = "AKIAI44QH8DHBEXAMPLE";
  public static final String AWS_SECRET_ACCESS_KEY = "wJalrXUtnFEMI-K7MDENG-bPxRfiCYEXAMPLEKEY";
  public static final String AWS_OTHER_SECRET_ACCESS_KEY =
      "je7MtGbClwBF-2Zp9Utk-h3yCo8nvbEXAMPLEKEY";
  public static final String AWS_SESSION_TOKEN = "FwoGZXIvYXdzEEXAMPLESESSIONTOKEN";

  public static final String AWS_ROLE_ARN = "arn:aws:iam::123456789012:role/openaev-simulation";
  public static final String AWS_OTHER_ROLE_ARN = "arn:aws:iam::123456789012:role/openaev-audit";
  public static final String AWS_EXTERNAL_ID = "openaev-external-id-1234";
  public static final String AWS_SOURCE_PROFILE_ACCESS_KEY_ID = "AKIAIOSFODNN7SOURCE0";
  public static final String AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY =
      "sourceProfileSecret-K7MDENG-bPxRfiCYEXAMPLE";

  private SecretStoreRequestFixture() {}

  /** A request carrying no value at all — every optional field is {@code null}. */
  public static SecretStoreRequest emptyRequest() {
    return new SecretStoreRequest(
        null, null, null, null, null, null, null, null, null, null, null, null, null);
  }

  // -- AWS_ACCESS_KEY --

  /** A complete, valid AWS_ACCESS_KEY request (without session token). */
  public static SecretStoreRequest awsAccessKeyRequest() {
    return awsAccessKeyRequest(AWS_DEFAULT_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, null);
  }

  /** A complete, valid AWS_ACCESS_KEY request including a temporary session token. */
  public static SecretStoreRequest awsAccessKeyRequestWithSessionToken() {
    return awsAccessKeyRequest(
        AWS_DEFAULT_REGION, AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN);
  }

  public static SecretStoreRequest awsAccessKeyRequest(
      AwsRegion awsDefaultRegion,
      String awsAccessKeyId,
      String awsSecretAccessKey,
      String awsSessionToken) {
    return new SecretStoreRequest(
        null,
        null,
        null,
        null,
        awsDefaultRegion,
        awsAccessKeyId,
        awsSecretAccessKey,
        awsSessionToken,
        null,
        null,
        null,
        null,
        null);
  }

  // -- AWS_ASSUME_ROLE --

  /** A complete, valid AWS_ASSUME_ROLE request relying on the instance default identity. */
  public static SecretStoreRequest awsAssumeRoleInstanceDefaultRequest() {
    return awsAssumeRoleRequest(
        AWS_DEFAULT_REGION,
        AWS_ROLE_ARN,
        AWS_EXTERNAL_ID,
        AWS_SOURCE_IDENTITY_TYPE.INSTANCE_DEFAULT,
        null,
        null);
  }

  /** A complete, valid AWS_ASSUME_ROLE request relying on a static source access key. */
  public static SecretStoreRequest awsAssumeRoleStaticAccessKeyRequest() {
    return awsAssumeRoleRequest(
        AWS_DEFAULT_REGION,
        AWS_ROLE_ARN,
        AWS_EXTERNAL_ID,
        AWS_SOURCE_IDENTITY_TYPE.STATIC_ACCESS_KEY,
        AWS_SOURCE_PROFILE_ACCESS_KEY_ID,
        AWS_SOURCE_PROFILE_SECRET_ACCESS_KEY);
  }

  public static SecretStoreRequest awsAssumeRoleRequest(
      AwsRegion awsDefaultRegion,
      String awsRoleArn,
      String awsExternalId,
      AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
      String awsSourceProfileAccessKeyId,
      String awsSourceProfileSecretAccessKey) {
    return new SecretStoreRequest(
        null,
        null,
        null,
        null,
        awsDefaultRegion,
        null,
        null,
        null,
        awsRoleArn,
        awsExternalId,
        awsSourceIdentityType,
        awsSourceProfileAccessKeyId,
        awsSourceProfileSecretAccessKey);
  }
}
