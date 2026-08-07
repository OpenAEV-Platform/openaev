package io.openaev.api.credentials.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.CredentialSecretReference;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CredentialContractOutput(
    @JsonProperty("credential_type") @NotNull
        CredentialSecretReference.CREDENTIAL_TYPE credentialType,
    @JsonProperty("credential_auth_method") @NotNull
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD credentialAuthMethod,
    @JsonProperty("fields") List<CredentialContractField> fields) {

  public enum CredentialContractFieldType {
    text,
    password,
    select,
    number,
    checkbox,
  }

  public record CredentialContractField(
      @JsonProperty("field_name") String fieldName,
      @JsonProperty("field_type") CredentialContractFieldType fieldType,
      @JsonProperty("required") boolean required,
      @JsonProperty("choices") List<String> choices) {}
}
