package io.openaev.database.model;

import static io.openaev.database.model.Secret.SECRET_TYPE.AWS_ASSUME_ROLE_VALUE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Entity
@DiscriminatorValue(AWS_ASSUME_ROLE_VALUE)
@EntityListeners(ModelBaseListener.class)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class AwsAssumeRoleSecret extends Secret {

  public enum AWS_SOURCE_IDENTITY_TYPE {
    STATIC_ACCESS_KEY,
    INSTANCE_DEFAULT
  }

  @Column(name = "secret_aws_default_region")
  @JsonProperty("secret_aws_default_region")
  @Enumerated(EnumType.STRING)
  private AwsRegion awsDefaultRegion;

  @Column(name = "secret_aws_role_arn")
  @JsonProperty("secret_aws_role_arn")
  @NotNull
  private String awsRoleArn;

  @Column(name = "secret_aws_source_identity_type")
  @JsonProperty("secret_aws_source_identity_type")
  @Enumerated(EnumType.STRING)
  @NotNull
  private AWS_SOURCE_IDENTITY_TYPE awsSourceIdentityType;

  @Column(name = "secret_aws_external_id")
  @JsonProperty("secret_aws_external_id")
  @JsonIgnore
  private String awsExternalId;

  @Column(name = "secret_aws_source_profile_access_key_id")
  @JsonProperty("secret_aws_source_profile_access_key_id")
  private String awsSourceProfileAccessKeyId;

  @Column(name = "secret_aws_source_profile_secret_access_key")
  @JsonIgnore
  private String awsSourceProfileSecretAccessKey;
}
