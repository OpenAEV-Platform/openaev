package io.openaev.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for AWS S3 integration.
 *
 * <p>This configuration class manages AWS-specific settings for S3 storage, including IAM role
 * authentication and custom STS endpoint configuration. These settings are used when running in AWS
 * environments with IAM role-based authentication.
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * openaev:
 *   s3:
 *     use-aws-role: true
 *     sts-endpoint: https://sts.amazonaws.com
 * }</pre>
 *
 * @see MinioConfig
 */
@Component
@Data
public class S3Config {

  /**
   * Whether to use AWS IAM role-based authentication instead of access keys.
   *
   * <p>When enabled, the application will use the IAM role assigned to the EC2 instance, ECS task,
   * or Lambda function for S3 access, eliminating the need for explicit credentials.
   */
  @JsonProperty("use-aws-role")
  @Value("${openaev.s3.use-aws-role:false}")
  private boolean useAwsRole;

  /**
   * Custom AWS Security Token Service (STS) endpoint URL.
   *
   * <p>This is typically used in private VPC configurations or when using VPC endpoints for AWS
   * services. If not specified, the default AWS STS endpoint will be used.
   */
  @JsonProperty("sts-endpoint")
  @Value("${openaev.s3.sts-endpoint:#{null}}")
  private String stsEndpoint;
}
