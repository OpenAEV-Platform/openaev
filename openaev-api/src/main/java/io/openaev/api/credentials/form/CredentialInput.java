package io.openaev.api.credentials.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.CredentialSecretReference;
import io.openaev.database.model.HashSecret;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CredentialInput(
    @JsonProperty("credential_name") @NotBlank String credentialName,
    @JsonProperty("credential_type") @NotNull
        CredentialSecretReference.CREDENTIAL_TYPE credentialType,
    @JsonProperty("credential_auth_method") @NotNull
        CredentialSecretReference.CREDENTIAL_AUTH_METHOD credentialAuthMethod,
    @JsonProperty("credential_description") String credentialDescription,
    @JsonProperty("credential_username") String credentialUsername,
    @JsonProperty("credential_password") String credentialPassword,
    @JsonProperty("credential_hash_algorithm") HashSecret.HASH_ALGORITHM credentialHashAlgorithm,
    @JsonProperty("credential_hash") String credentialHash,
    @JsonProperty("credential_tags") List<String> credentialTagIds) {}
