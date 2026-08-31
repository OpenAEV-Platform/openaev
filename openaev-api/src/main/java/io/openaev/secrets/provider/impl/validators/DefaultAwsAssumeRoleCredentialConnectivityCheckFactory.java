package io.openaev.secrets.provider.impl.validators;

import static io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.STATIC_ACCESS_KEY;

import io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE;
import io.openaev.database.model.AwsRegion;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.Credentials;

/** Default {@link AwsAssumeRoleCredentialConnectivityCheckFactory}, backed by the AWS SDK. */
@Component
public class DefaultAwsAssumeRoleCredentialConnectivityCheckFactory
    implements AwsAssumeRoleCredentialConnectivityCheckFactory {

  @Override
  public AwsCredentialsProvider sourceCredentialsProvider(
      AWS_SOURCE_IDENTITY_TYPE sourceIdentityType,
      String sourceProfileAccessKeyId,
      String sourceProfileSecretAccessKey) {
    if (STATIC_ACCESS_KEY.equals(sourceIdentityType)) {
      AwsBasicCredentials basicCredentials =
          AwsBasicCredentials.create(sourceProfileAccessKeyId, sourceProfileSecretAccessKey);
      return StaticCredentialsProvider.create(basicCredentials);
    }
    return DefaultCredentialsProvider.create();
  }

  @Override
  public AssumeRoleRequest assumeRoleRequest(String roleArn, String externalId) {
    return AssumeRoleRequest.builder()
        .roleArn(roleArn)
        .roleSessionName("oaev")
        .externalId(externalId)
        .build();
  }

  @Override
  public AwsSessionCredentials sessionCredentials(Credentials credentials) {
    return AwsSessionCredentials.create(
        credentials.accessKeyId(), credentials.secretAccessKey(), credentials.sessionToken());
  }

  @Override
  public StsClient stsClient(AwsRegion region, AwsCredentialsProvider credentialsProvider) {
    return StsClient.builder()
        .region(Region.of(region.code()))
        .credentialsProvider(credentialsProvider)
        .build();
  }
}
