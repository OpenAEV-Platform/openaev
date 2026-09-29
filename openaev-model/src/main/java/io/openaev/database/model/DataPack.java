package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "datapacks")
public class DataPack implements TenantBase {

  @EmbeddedId
  @JsonProperty("datapack_tenant_id_relationship")
  private DatapackTenantId compositeId = new DatapackTenantId();

  @Override
  public Tenant getTenant() {
    return compositeId.getTenant();
  }

  @Override
  public void setTenant(Tenant tenant) {
    compositeId.setTenant(tenant);
  }

  @Override
  public String getId() {
    return compositeId.getId();
  }

  @Override
  public void setId(String id) {
    compositeId.setId(id);
  }
}
