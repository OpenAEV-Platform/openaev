package io.openaev.utils.fixtures;

/**
 * Canned AWS STS answers, for a local server standing in for the sovereign or mocked cloud an
 * endpoint override points to. STS speaks the AWS query protocol: form-encoded requests carrying an
 * {@code Action}, XML responses.
 */
public final class AwsStsResponseFixture {

  public static final String STS_CONTENT_TYPE = "text/xml";

  public static final String GET_CALLER_IDENTITY_RESPONSE =
      """
      <GetCallerIdentityResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
        <GetCallerIdentityResult>
          <Arn>arn:aws:iam::123456789012:user/mock</Arn>
          <UserId>AIDAMOCK</UserId>
          <Account>123456789012</Account>
        </GetCallerIdentityResult>
        <ResponseMetadata><RequestId>mock-request</RequestId></ResponseMetadata>
      </GetCallerIdentityResponse>
      """;

  public static final String ASSUME_ROLE_RESPONSE =
      """
      <AssumeRoleResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
        <AssumeRoleResult>
          <Credentials>
            <AccessKeyId>ASIAMOCK</AccessKeyId>
            <SecretAccessKey>mock-secret-access-key</SecretAccessKey>
            <SessionToken>mock-session-token</SessionToken>
            <Expiration>2099-01-01T00:00:00Z</Expiration>
          </Credentials>
          <AssumedRoleUser>
            <AssumedRoleId>AROAMOCK:oaev</AssumedRoleId>
            <Arn>arn:aws:sts::123456789012:assumed-role/mock/oaev</Arn>
          </AssumedRoleUser>
        </AssumeRoleResult>
        <ResponseMetadata><RequestId>mock-request</RequestId></ResponseMetadata>
      </AssumeRoleResponse>
      """;

  public static final String INVALID_CLIENT_TOKEN_RESPONSE =
      """
      <ErrorResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
        <Error>
          <Type>Sender</Type>
          <Code>InvalidClientTokenId</Code>
          <Message>The security token included in the request is invalid.</Message>
        </Error>
        <RequestId>mock-request</RequestId>
      </ErrorResponse>
      """;

  private AwsStsResponseFixture() {}

  /**
   * @param requestBody the form-encoded body STS received
   * @param action the STS action name
   * @return whether the request calls that action
   */
  public static boolean isStsAction(String requestBody, String action) {
    return requestBody.contains("Action=" + action + "&")
        || requestBody.endsWith("Action=" + action);
  }
}
