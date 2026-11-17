package io.openaev.api.credentials.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.CredentialSecretReference;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Builder;

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

  /**
   * One field of a credential form.
   *
   * <p>{@code endpoint} marks a URL, URI or authority host: pointing it elsewhere than the stored
   * value would replay the stored secrets there. {@code resetOnEndpointChange} marks a write-only
   * secret the handler then requires to be sent again, so the form empties it.
   */
  @Builder
  public record CredentialContractField(
      @JsonProperty("field_name") @NotNull String fieldName,
      @JsonProperty("field_type") CredentialContractFieldType fieldType,
      @JsonProperty("required") boolean required,
      @JsonProperty("choices") List<String> choices,
      @JsonProperty("mandatory_condition_field") String mandatoryConditionField,
      @JsonProperty("mandatory_condition_value") String mandatoryConditionValue,
      @JsonProperty("visible_condition_field") String visibleConditionField,
      @JsonProperty("visible_condition_value") String visibleConditionValue,
      @JsonProperty("default_value") String defaultValue,
      @JsonProperty("field_description") String fieldDescription,
      @JsonProperty("accepted_formats") List<String> acceptedFormats,
      @JsonProperty("endpoint") boolean endpoint,
      @JsonProperty("reset_on_endpoint_change") boolean resetOnEndpointChange) {}
}
