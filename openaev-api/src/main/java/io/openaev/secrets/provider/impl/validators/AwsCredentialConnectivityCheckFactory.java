package io.openaev.secrets.provider.impl.validators;

import io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE;
import io.openaev.database.model.AwsRegion;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/** Builds the AWS SDK credentials used to probe a stored AWS access key. */
public interface AwsCredentialConnectivityCheckFactory {

  /**
   * Builds AWS credentials from the decrypted values stored by the secret.
   *
   * @param accessKeyId AWS access key id
   * @param secretAccessKey AWS secret access key
   * @param sessionToken optional session token
   * @return an AWS credentials instance matching the provided shape
   */
  AwsCredentials forAccessKey(String accessKeyId, String secretAccessKey, String sessionToken);

  /**
   * Builds the source credentials provider used to call STS AssumeRole.
   *
   * @param sourceIdentityType source identity type
   * @param sourceProfileAccessKeyId static source access key id
   * @param sourceProfileSecretAccessKey static source secret access key
   * @return source credentials provider
   */
  AwsCredentialsProvider getSourceCredentialsProvider(
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
   * Builds an STS client for the given region/provider.
   *
   * @param region AWS region
   * @param credentialsProvider credentials provider
   * @return STS client
   */
  StsClient stsClient(AwsRegion region, AwsCredentialsProvider credentialsProvider);
}
