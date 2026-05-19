package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

@Data
@Embeddable
public class DatapackTenantId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Column(name = "datapack_id", updatable = false, nullable = false)
  @JsonProperty("datapack_id")
  @NotBlank
  private String id;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  public DatapackTenantId() {
    // Default constructor
  }

  public DatapackTenantId(String id, Tenant tenant) {
    this.id = id;
    this.tenant = tenant;
  }
}
