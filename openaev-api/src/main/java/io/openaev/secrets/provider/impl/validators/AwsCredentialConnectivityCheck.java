package io.openaev.secrets.provider.impl.validators;

import io.openaev.database.model.AwsAssumeRoleSecret;
import io.openaev.database.model.AwsRegion;
import io.openaev.secrets.provider.SecretConnectionResult;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.GetCallerIdentityRequest;
import software.amazon.awssdk.services.sts.model.StsException;

/** Probes AWS access key credentials with STS. */
@Component
@RequiredArgsConstructor
public class AwsCredentialConnectivityCheck {
  private static final Set<String> AUTH_FAILED_CODES =
      Set.of(
          "ExpiredToken",
          "ExpiredTokenException",
          "IncompleteSignature",
          "InvalidAccessKeyId",
          "InvalidClientTokenId",
          "InvalidSecurityToken",
          "SignatureDoesNotMatch",
          "UnrecognizedClientException");

  private static final Set<String> PERMISSION_DENIED_CODES =
      Set.of("AccessDenied", "AccessDeniedException", "NotAuthorized", "UnauthorizedOperation");

  private static final Set<String> FORMAT_ERROR_CODES =
      Set.of(
          "InvalidParameterCombination",
          "InvalidParameterValue",
          "InvalidQueryParameter",
          "MalformedPolicyDocument",
          "MalformedQueryString",
          "MissingAction",
          "MissingParameter",
          "PackedPolicyTooLarge",
          "ValidationError");

  private static final Set<String> TIMEOUT_CODES =
      Set.of("RequestExpired", "RequestTimeout", "RequestTimeoutException");

  private static final Set<String> TRANSIENT_NETWORK_CODES =
      Set.of("InternalFailure", "ServiceUnavailable", "Throttling", "ThrottlingException");

  private final AwsCredentialConnectivityCheckFactory awsCredentialsFactory;

  /**
   * Validates an AWS access key credential by calling STS {@code GetCallerIdentity}.
   *
   * @param region the configured AWS region
   * @param accessKeyId access key id
   * @param secretAccessKey decrypted secret access key
   * @param sessionToken optional decrypted session token
   * @return a definitive active/inactive outcome
   */
  public SecretConnectionResult validateAccessKey(
      AwsRegion region, String accessKeyId, String secretAccessKey, String sessionToken) {
    AwsCredentials credentials =
        awsCredentialsFactory.forAccessKey(accessKeyId, secretAccessKey, sessionToken);

    try (StsClient verifyClient =
        awsCredentialsFactory.stsClient(region, StaticCredentialsProvider.create(credentials))) {
      verifyClient.getCallerIdentity(GetCallerIdentityRequest.builder().build());
      return SecretConnectionResult.active();
    } catch (RuntimeException e) {
      return mapValidationFailure(e);
    }
  }

  /**
   * Validates an AWS assume-role credential by assuming the role and probing GetCallerIdentity.
   *
   * @param region configured AWS region
   * @param roleArn role to assume
   * @param externalId optional external id
   * @param sourceIdentityType source identity type
   * @param sourceProfileAccessKeyId static source access key id
   * @param sourceProfileSecretAccessKey static source secret access key
   * @return a definitive active/inactive outcome
   */
  public SecretConnectionResult validateAssumeRole(
      AwsRegion region,
      String roleArn,
      String externalId,
      AwsAssumeRoleSecret.AWS_SOURCE_IDENTITY_TYPE sourceIdentityType,
      String sourceProfileAccessKeyId,
      String sourceProfileSecretAccessKey) {
    AwsCredentialsProvider sourceProvider =
        awsCredentialsFactory.getSourceCredentialsProvider(
            sourceIdentityType, sourceProfileAccessKeyId, sourceProfileSecretAccessKey);

    try (StsClient assumeRoleClient = awsCredentialsFactory.stsClient(region, sourceProvider)) {
      AssumeRoleResponse assumeRoleResponse =
          assumeRoleClient.assumeRole(awsCredentialsFactory.assumeRoleRequest(roleArn, externalId));
      AwsSessionCredentials sessionCredentials =
          (AwsSessionCredentials)
              awsCredentialsFactory.forAccessKey(
                  assumeRoleResponse.credentials().accessKeyId(),
                  assumeRoleResponse.credentials().secretAccessKey(),
                  assumeRoleResponse.credentials().sessionToken());

      try (StsClient verifyClient =
          awsCredentialsFactory.stsClient(
              region, StaticCredentialsProvider.create(sessionCredentials))) {
        verifyClient.getCallerIdentity(GetCallerIdentityRequest.builder().build());
        return SecretConnectionResult.active();
      }
    } catch (RuntimeException e) {
      return mapValidationFailure(e);
    }
  }

  private SecretConnectionResult mapValidationFailure(RuntimeException failure) {
    if (isTimeout(failure)) {
      return SecretConnectionResult.timeout();
    }

    if (failure instanceof StsException stsFailure) {
      return mapStsFailure(stsFailure);
    }

    if (failure instanceof IllegalArgumentException || failure instanceof ClassCastException) {
      return SecretConnectionResult.formatError();
    }

    if (failure instanceof SdkClientException) {
      return SecretConnectionResult.networkError();
    }

    return SecretConnectionResult.unknown();
  }

  private SecretConnectionResult mapStsFailure(StsException failure) {
    String errorCode = errorCodeOf(failure);
    int statusCode = statusCodeOf(failure);

    if (AUTH_FAILED_CODES.contains(errorCode)) {
      return SecretConnectionResult.authFailed();
    }
    if (PERMISSION_DENIED_CODES.contains(errorCode)) {
      return SecretConnectionResult.permissionDenied();
    }
    if (TIMEOUT_CODES.contains(errorCode)) {
      return SecretConnectionResult.timeout();
    }
    if (TRANSIENT_NETWORK_CODES.contains(errorCode)) {
      return SecretConnectionResult.networkError();
    }
    if (FORMAT_ERROR_CODES.contains(errorCode)) {
      return SecretConnectionResult.formatError();
    }

    if (statusCode == 401) {
      return SecretConnectionResult.authFailed();
    }
    if (statusCode == 403) {
      return SecretConnectionResult.permissionDenied();
    }
    if (statusCode == 400) {
      return SecretConnectionResult.formatError();
    }
    if (statusCode == 408) {
      return SecretConnectionResult.timeout();
    }
    if (statusCode == 429 || statusCode >= 500) {
      return SecretConnectionResult.networkError();
    }

    return SecretConnectionResult.unknown();
  }

  private static String errorCodeOf(AwsServiceException failure) {
    return failure.awsErrorDetails() != null ? failure.awsErrorDetails().errorCode() : "";
  }

  private static int statusCodeOf(AwsServiceException failure) {
    return failure.statusCode();
  }

  private static boolean isTimeout(Throwable failure) {
    for (Throwable current = failure; current != null; current = current.getCause()) {
      if (current instanceof ApiCallTimeoutException
          || current instanceof ApiCallAttemptTimeoutException
          || current instanceof TimeoutException
          || current instanceof java.net.SocketTimeoutException) {
        return true;
      }
      if (current.getCause() == current) {
        break;
      }
    }
    return false;
  }
}
