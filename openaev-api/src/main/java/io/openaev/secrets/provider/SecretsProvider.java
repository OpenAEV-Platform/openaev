package io.openaev.secrets.provider;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.BaseConnectorEntity;
import io.openaev.database.model.SecretReference;
import io.openaev.database.model.TenantIdBase;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

public abstract class SecretsProvider extends BaseConnectorEntity implements TenantIdBase {

  public static final String SERVICE_NAME = "secrets-provider";

  @JsonProperty("secrets_provider_id")
  @Getter
  @Setter
  private String id;

  @JsonProperty("secrets_provider_name")
  @Getter
  @Setter
  private String name;

  @JsonIgnore @Getter @Setter private String tenantId;

  @Override
  @JsonProperty("secrets_provider_type")
  public String getType() {
    return super.getType();
  }

  protected SecretsProvider(String id, String name, String type) {
    this.id = id;
    this.name = name;
    this.setType(type);
  }

  // -- SecretProvider default implementations  --

  public SecretMetadata getSecretMetadata(@NotNull SecretReference secretReference) {
    throw new UnsupportedOperationException(
        "Retrieve secret main information is not supported for this provider");
  }

  public SecretReference store(
      @NotNull SecretReference secretReference, @NotNull SecretStoreRequest request) {
    throw new UnsupportedOperationException(
        "This secret backend does not support storing secrets.");
  }

  public SecretReference update(
      @NotNull SecretReference secretReference, @NotNull SecretStoreRequest request) {
    throw new UnsupportedOperationException(
        "This secret backend does not support updating secrets.");
  }

  public void delete(@NotNull SecretReference secretReference) {
    throw new UnsupportedOperationException(
        "This secret backend does not support deleting secrets.");
  }

  /**
   * Prepares a liveness check for one credential, to be run outside any transaction.
   *
   * <p>Opt-in, like {@code SecretHandler#validateConnection}: a backend with nothing to probe needs
   * no change and reports {@link SecretConnectionResult#unsupported()}, which the run then leaves
   * completely untouched in database.
   *
   * <p>Called INSIDE the background job's transactional phase, so an implementation may read what
   * it needs here — but the {@link SecretConnectionProbe} it returns must be detached, since it
   * runs with no session (see {@link SecretConnectionProbe}).
   *
   * @param secretReference the reference to check
   * @return the probe to run, never null
   */
  public SecretConnectionProbe prepareConnectionCheck(@NotNull SecretReference secretReference) {
    return SecretConnectionProbe.of(SecretConnectionResult.unsupported());
  }

  public static class Placeholder extends SecretsProvider {
    public Placeholder() {
      super("placeholder", "Placeholder", SecretsProviderType.PLACEHOLDER.type);
    }
  }
}
