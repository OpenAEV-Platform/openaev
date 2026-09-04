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
    /**
     * A file upload. A field declared with this type never travels inside the JSON {@code input}
     * part: it is sent as a dedicated multipart part named after its {@code field_name}.
     */
    file,
  }

  public record CredentialContractField(
      @JsonProperty("field_name") @NotNull String fieldName,
      @JsonProperty("field_type") CredentialContractFieldType fieldType,
      @JsonProperty("required") boolean required,
      @JsonProperty("choices") List<String> choices,
      @JsonProperty("mandatory_condition_field") String mandatoryConditionField,
      @JsonProperty("mandatory_condition_value") String mandatoryConditionValue,
      @JsonProperty("visible_condition_field") String visibleConditionField,
      @JsonProperty("visible_condition_value") String visibleConditionValue,
      @JsonProperty("default_value") String defaultValue) {

    /**
     * Convenience constructor for the fields carrying no default value, so contracts declared
     * before {@code default_value} existed stay untouched.
     */
    public CredentialContractField(
        String fieldName,
        CredentialContractFieldType fieldType,
        boolean required,
        List<String> choices,
        String mandatoryConditionField,
        String mandatoryConditionValue,
        String visibleConditionField,
        String visibleConditionValue) {
      this(
          fieldName,
          fieldType,
          required,
          choices,
          mandatoryConditionField,
          mandatoryConditionValue,
          visibleConditionField,
          visibleConditionValue,
          null);
    }
  }
}
