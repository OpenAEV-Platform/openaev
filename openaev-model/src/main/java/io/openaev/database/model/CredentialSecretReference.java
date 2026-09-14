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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotNull;
import java.util.EnumSet;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@Entity
@DiscriminatorValue(SecretReference.SECRET_REFERENCE_TYPE.CREDENTIAL_VALUE)
@EntityListeners(ModelBaseListener.class)
public class CredentialSecretReference extends SecretReference {

  private static final Map<CREDENTIAL_TYPE, EnumSet<CREDENTIAL_AUTH_METHOD>>
      ALLOWED_AUTH_METHODS_BY_TYPE =
          Map.of(
              CREDENTIAL_TYPE.IDENTITY,
                  EnumSet.of(CREDENTIAL_AUTH_METHOD.USERNAME_PASSWORD, CREDENTIAL_AUTH_METHOD.HASH),
              CREDENTIAL_TYPE.CLOUD_AWS,
                  EnumSet.of(
                      CREDENTIAL_AUTH_METHOD.AWS_ACCESS_KEY,
                      CREDENTIAL_AUTH_METHOD.AWS_ASSUME_ROLE),
              CREDENTIAL_TYPE.CLOUD_AZURE,
                  EnumSet.of(
                      CREDENTIAL_AUTH_METHOD.AZURE_SERVICE_PRINCIPAL,
                      CREDENTIAL_AUTH_METHOD.AZURE_MANAGED_IDENTITY),
              CREDENTIAL_TYPE.CLOUD_GCP,
                  EnumSet.of(
                      CREDENTIAL_AUTH_METHOD.GCP_SERVICE_ACCOUNT,
                      CREDENTIAL_AUTH_METHOD.GCP_OAUTH2));

  public enum CREDENTIAL_AUTH_METHOD {
    USERNAME_PASSWORD,
    HASH,
    AWS_ACCESS_KEY,
    AWS_ASSUME_ROLE,
    AZURE_SERVICE_PRINCIPAL,
    AZURE_MANAGED_IDENTITY,
    GCP_SERVICE_ACCOUNT,
    GCP_OAUTH2
  }

  public enum CREDENTIAL_TYPE {
    IDENTITY,
    CLOUD_AWS,
    CLOUD_AZURE,
    CLOUD_GCP,
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

  public void validateCredentialCombinationOrThrow() {
    if (credentialType == null || credentialAuthMethod == null) {
      return;
    }
    EnumSet<CREDENTIAL_AUTH_METHOD> allowedMethods =
        ALLOWED_AUTH_METHODS_BY_TYPE.get(credentialType);
    if (allowedMethods == null || !allowedMethods.contains(credentialAuthMethod)) {
      throw new IllegalArgumentException(
          "Invalid credential_type/credential_auth_method combination: "
              + credentialType
              + " / "
              + credentialAuthMethod);
    }
  }

  @PrePersist
  @PreUpdate
  private void validateCredentialCombination() {
    validateCredentialCombinationOrThrow();
  }
}
