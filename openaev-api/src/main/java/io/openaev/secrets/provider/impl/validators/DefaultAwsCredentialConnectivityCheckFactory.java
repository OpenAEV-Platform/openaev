package io.openaev.secrets.provider.impl.validators;

import static io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE.STATIC_ACCESS_KEY;

import io.openaev.database.model.AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE;
import io.openaev.database.model.AwsRegion;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

/** Default {@link AwsCredentialConnectivityCheckFactory}, backed by the AWS SDK. */
@Component
public class DefaultAwsCredentialConnectivityCheckFactory
    implements AwsCredentialConnectivityCheckFactory {

  @Value("${openaev.credentials.status-validation.timeout-seconds:10}")
  private int timeoutSeconds;

  @Override
  public AwsCredentials forAccessKey(
      String accessKeyId, String secretAccessKey, String sessionToken) {
    if (sessionToken != null && !sessionToken.isBlank()) {
      return AwsSessionCredentials.create(accessKeyId, secretAccessKey, sessionToken);
    }
    return AwsBasicCredentials.create(accessKeyId, secretAccessKey);
  }

  @Override
  public AwsCredentialsProvider getSourceCredentialsProvider(
      AWS_SOURCE_IDENTITY_TYPE sourceIdentityType,
      String sourceProfileAccessKeyId,
      String sourceProfileSecretAccessKey) {
    if (STATIC_ACCESS_KEY.equals(sourceIdentityType)) {
      AwsBasicCredentials basicCredentials =
          AwsBasicCredentials.create(sourceProfileAccessKeyId, sourceProfileSecretAccessKey);
      return StaticCredentialsProvider.create(basicCredentials);
    }
    return DefaultCredentialsProvider.builder().build();
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
  public StsClient stsClient(AwsRegion region, AwsCredentialsProvider credentialsProvider) {
    Duration timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    return StsClient.builder()
        .region(Region.of(region.code()))
        .credentialsProvider(credentialsProvider)
        .overrideConfiguration(
            ClientOverrideConfiguration.builder()
                .apiCallAttemptTimeout(timeout)
                .apiCallTimeout(timeout)
                .build())
        .build();
  }
}
