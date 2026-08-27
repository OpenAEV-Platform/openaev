package io.openaev.database.model;

import static io.openaev.database.model.Secret.SECRET_TYPE.GCP_OAUTH2_VALUE;

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

/**
 * A GCP OAuth 2.0 identity: the platform acts on behalf of a Google user who granted consent once.
 *
 * <p>The client secret and the refresh token are the credential — both are stored encrypted, never
 * serialized back to the API, and never rendered in any log line. The client id and the project id
 * are plain identifiers, echoed back to prefill the edit form.
 */
@Getter
@Setter
@Entity
@DiscriminatorValue(GCP_OAUTH2_VALUE)
@EntityListeners(ModelBaseListener.class)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class GcpOAuth2Secret extends Secret {

  @Column(name = "secret_gcp_scope")
  @JsonProperty("secret_gcp_scope")
  @NotNull
  private String scope;

  @Column(name = "secret_gcp_oauth_client_id")
  @JsonProperty("secret_gcp_oauth_client_id")
  @NotNull
  private String oauthClientId;

  @Column(name = "secret_gcp_oauth_client_secret")
  @JsonIgnore
  @NotNull
  private String oauthClientSecret;

  @Column(name = "secret_gcp_oauth_refresh_token")
  @JsonIgnore
  @NotNull
  private String oauthRefreshToken;

  @Column(name = "secret_gcp_project_id")
  @JsonProperty("secret_gcp_project_id")
  private String projectId;
}
