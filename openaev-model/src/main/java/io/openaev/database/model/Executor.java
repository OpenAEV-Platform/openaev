package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.helper.CompositeIdResolvableI;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;

@Getter
@Setter
@Entity
@Table(name = "executors")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Executor extends BaseConnectorEntity implements TenantBase, CompositeIdResolvableI {

  @EmbeddedId @JsonIgnore private ExecutorId compositeId = new ExecutorId();

  @Override
  @JsonProperty("executor_id")
  @NotBlank
  public String getId() {
    return compositeId.getId();
  }

  @Override
  public void setId(String id) {
    compositeId.setId(id);
  }

  @Override
  @JsonIgnore
  public Tenant getTenant() {
    String tenantId = compositeId.getTenantId();
    return tenantId != null ? new Tenant(tenantId) : null;
  }

  @Override
  public void setTenant(Tenant tenant) {
    compositeId.setTenantId(tenant != null ? tenant.getId() : null);
  }

  @JsonIgnore
  public String getTenantId() {
    return compositeId.getTenantId();
  }

  public void setTenantId(String tenantId) {
    compositeId.setTenantId(tenantId);
  }

  @Column(name = "executor_name")
  @JsonProperty("executor_name")
  @NotBlank
  private String name;

  @Column(name = "executor_type")
  @JsonProperty("executor_type")
  @NotBlank
  private String type;

  @Type(StringArrayType.class)
  @Column(name = "executor_platforms", columnDefinition = "text[]")
  @JsonProperty("executor_platforms")
  private String[] platforms = new String[0];

  @Column(name = "executor_doc")
  @JsonProperty("executor_doc")
  private String doc;

  @Column(name = "executor_background_color")
  @JsonProperty("executor_background_color")
  private String backgroundColor;

  @Column(name = "executor_created_at")
  @JsonProperty("executor_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "executor_updated_at")
  @JsonProperty("executor_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) return false;
    Base base = (Base) o;
    return getId().equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(compositeId);
  }

  @Override
  public Object resolveCompositeId(String rawId, DeserializationContext ctxt) {
    String tenantId = TenantContext.getCurrentTenant();
    ExecutorId id = new ExecutorId();
    id.setId(rawId);
    id.setTenantId(tenantId);
    return id;
  }
}
