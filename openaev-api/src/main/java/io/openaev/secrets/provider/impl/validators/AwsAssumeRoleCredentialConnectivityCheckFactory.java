package io.openaev.secrets.provider.impl.validators;

import io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE;
import io.openaev.database.model.AwsRegion;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.Credentials;

/** Builds AWS SDK objects used to probe a stored AWS assume-role credential. */
public interface AwsAssumeRoleCredentialConnectivityCheckFactory {

  /**
   * Builds the source credentials provider used to call STS AssumeRole.
   *
   * @param sourceIdentityType source identity type
   * @param sourceProfileAccessKeyId static source access key id
   * @param sourceProfileSecretAccessKey static source secret access key
   * @return source credentials provider
   */
  AwsCredentialsProvider sourceCredentialsProvider(
      AWS_SOURCE_IDENTITY_TYPE sourceIdentityType,
      String sourceProfileAccessKeyId,
      String sourceProfileSecretAccessKey);

  /**
   * Builds the AssumeRole request.
   *
   * @param roleArn role to assume
   * @param externalId optional external id
   * @return assume-role request
   */
  AssumeRoleRequest assumeRoleRequest(String roleArn, String externalId);

  /**
   * Builds session credentials from STS temporary credentials.
   *
   * @param credentials STS credentials
   * @return session credentials
   */
  AwsSessionCredentials sessionCredentials(Credentials credentials);

  /**
   * Builds an STS client for the given region/provider.
   *
   * @param region AWS region
   * @param credentialsProvider credentials provider
   * @return STS client
   */
  StsClient stsClient(AwsRegion region, AwsCredentialsProvider credentialsProvider);
}
