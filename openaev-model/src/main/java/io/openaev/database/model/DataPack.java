package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.TenantBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Getter
@Setter
@Entity
@Table(name = "datapacks")
@EntityListeners(TenantBaseListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class DataPack implements TenantBase {
  @Id
  @Column(name = "datapack_id", updatable = false, nullable = false)
  @JsonProperty("datapack_id")
  @NotBlank
  private String id;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;
}
