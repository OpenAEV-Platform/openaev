package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(CredentialSecretReference.CREDENTIAL_TYPE)
@EntityListeners(ModelBaseListener.class)
public class CredentialSecretReference extends SecretReference {
  public static final String CREDENTIAL_TYPE = "CREDENTIAL";

  @Column(name = "secret_reference_credential_type")
  @JsonProperty("secret_reference_credential_type")
  @NotBlank
  private String credentialType;

  @Column(name = "secret_reference_credential_auth_method")
  @JsonProperty("secret_reference_credential_auth_method")
  @NotBlank
  private String credentialAuthMethod;
}
