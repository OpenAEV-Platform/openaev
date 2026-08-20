package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(SecretReference.SECRET_REFERENCE_TYPE.CREDENTIAL_VALUE)
@EntityListeners(ModelBaseListener.class)
public class CredentialSecretReference extends SecretReference {

  public enum CREDENTIAL_AUTH_METHOD {
    USERNAME_PASSWORD,
    HASH
  }

  public enum CREDENTIAL_TYPE {
    IDENTITY,
  }

  @Column(name = "secret_reference_credential_type")
  @JsonProperty("secret_reference_credential_type")
  @Queryable(filterable = true)
  @NotNull
  @Enumerated(EnumType.STRING)
  private CREDENTIAL_TYPE credentialType;

  @Column(name = "secret_reference_credential_auth_method")
  @JsonProperty("secret_reference_credential_auth_method")
  @Queryable(filterable = true)
  @NotNull
  @Enumerated(EnumType.STRING)
  private CREDENTIAL_AUTH_METHOD credentialAuthMethod;
}
