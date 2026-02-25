package io.openaev.database.model;

import static io.openaev.database.model.Tenant.DEFAULT_TENANT_UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "datapacks")
public class DataPack {
  @Id
  @Column(name = "datapack_id", updatable = false, nullable = false)
  @JsonProperty("datapack_id")
  @NotBlank
  private String id;

  @ManyToOne
  @JoinColumn(name = "tenant_id")
  @JsonIgnore
  @NotNull
  private Tenant tenant = new Tenant(DEFAULT_TENANT_UUID);
}
