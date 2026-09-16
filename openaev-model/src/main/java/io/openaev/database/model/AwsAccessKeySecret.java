package io.openaev.database.model;

import static io.openaev.database.model.Secret.SECRET_TYPE.AWS_ACCESS_KEY_VALUE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Entity
@DiscriminatorValue(AWS_ACCESS_KEY_VALUE)
@EntityListeners(ModelBaseListener.class)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class AwsAccessKeySecret extends Secret {

  @Column(name = "secret_aws_default_region")
  @JsonProperty("secret_aws_default_region")
  @Enumerated(EnumType.STRING)
  private AwsRegion awsDefaultRegion;

  @Column(name = "secret_aws_access_key_id")
  @JsonProperty("secret_aws_access_key_id")
  @NotNull
  private String awsAccessKeyId;

  @Column(name = "secret_aws_secret_access_key")
  @JsonIgnore
  @NotNull
  private String awsSecretAccessKey;

  @Column(name = "secret_aws_session_token")
  @JsonIgnore
  private String awsSessionToken;
}
