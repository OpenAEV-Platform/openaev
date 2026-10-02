package io.openaev.database.model;

import static io.openaev.database.model.Secret.SECRET_TYPE.GCP_SERVICE_ACCOUNT_VALUE;

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
 * A GCP service account identity: a JSON key file holding every authentication detail.
 *
 * <p>The key file itself is the credential — it is stored encrypted and never serialized back to
 * the API, nor rendered in any log line.
 */
@Getter
@Setter
@Entity
@DiscriminatorValue(GCP_SERVICE_ACCOUNT_VALUE)
@EntityListeners(ModelBaseListener.class)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true, onlyExplicitlyIncluded = true)
public class GcpServiceAccountSecret extends Secret {

  @Column(name = "secret_gcp_scope")
  @JsonProperty("secret_gcp_scope")
  @NotNull
  private String scope;

  /** Encrypted content of the uploaded service account key file. */
  @Column(name = "secret_gcp_private_key_json")
  @JsonIgnore
  @NotNull
  private byte[] privateKeyJson;

  @Column(name = "secret_gcp_project_id")
  @JsonProperty("secret_gcp_project_id")
  private String projectId;
}
