package io.openaev.database.model;

import static io.openaev.database.model.Secret.SECRET_TYPE.AZURE_MANAGED_IDENTITY_VALUE;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Entity
@DiscriminatorValue(AZURE_MANAGED_IDENTITY_VALUE)
@EntityListeners(ModelBaseListener.class)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class AzureManagedIdentitySecret extends Secret {

  @Column(name = "secret_azure_client_id")
  @JsonProperty("secret_azure_client_id")
  private String azureClientId;

  @Column(name = "secret_azure_subscription_id")
  @JsonProperty("secret_azure_subscription_id")
  private String azureSubscriptionId;

  @Column(name = "secret_azure_environment")
  @JsonProperty("secret_azure_environment")
  @NotNull
  private String azureEnvironment;
}
