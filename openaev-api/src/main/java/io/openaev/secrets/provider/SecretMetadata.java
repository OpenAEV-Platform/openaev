package io.openaev.secrets.provider;

import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.AwsRegion;
import io.openaev.database.model.HashSecret;

/**
 * Safe metadata returned by secret providers for credential display/update helpers.
 *
 * <p>It intentionally excludes any secret value (password/hash), even encrypted.
 */
public record SecretMetadata(
    String username,
    HashSecret.HASH_ALGORITHM hashAlgorithm,
    AwsRegion awsDefaultRegion,
    String awsAccessKeyId,
    String awsRoleArn,
    AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
    String awsSourceProfileAccessKeyId) {

  public static SecretMetadata empty() {
    return new SecretMetadata(null, null, null, null, null, null, null);
  }

  public static SecretMetadata forUsername(String username) {
    return new SecretMetadata(username, null, null, null, null, null, null);
  }

  public static SecretMetadata forHashAlgorithm(HashSecret.HASH_ALGORITHM hashAlgorithm) {
    return new SecretMetadata(null, hashAlgorithm, null, null, null, null, null);
  }

  public static SecretMetadata forAwsAccessKey(AwsRegion awsDefaultRegion, String awsAccessKeyId) {
    return new SecretMetadata(null, null, awsDefaultRegion, awsAccessKeyId, null, null, null);
  }

  public static SecretMetadata forAwsAssumeRole(
      AwsRegion awsDefaultRegion,
      String awsRoleArn,
      AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType,
      String awsSourceProfileAccessKeyId) {
    return new SecretMetadata(
        null,
        null,
        awsDefaultRegion,
        null,
        awsRoleArn,
        awsSourceIdentityType,
        awsSourceProfileAccessKeyId);
  }
}
