package io.openaev.database.model;

import static io.openaev.database.model.Secret.SECRET_TYPE.AZURE_SERVICE_PRINCIPAL_VALUE;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@DiscriminatorValue(AZURE_SERVICE_PRINCIPAL_VALUE)
@EntityListeners(ModelBaseListener.class)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class AzureServicePrincipalSecret extends Secret {

  @Column(name = "secret_azure_client_id")
  @JsonProperty("secret_azure_client_id")
  @NotNull
  private String azureClientId;

  @Column(name = "secret_azure_client_secret")
  @JsonIgnore
  @NotNull
  private String azureClientSecret;

  @Column(name = "secret_azure_tenant_id")
  @JsonIgnore
  @NotNull
  private String azureTenantId;

  @Column(name = "secret_azure_subscription_id")
  @JsonIgnore
  private String azureSubscriptionId;

  @Column(name = "secret_azure_environment")
  @JsonProperty("secret_azure_environment")
  @NotNull
  private String azureEnvironment;
}
