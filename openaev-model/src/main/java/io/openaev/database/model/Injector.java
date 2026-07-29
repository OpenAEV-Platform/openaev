package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLHStoreType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.healthcheck.enums.ExternalServiceDependency;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Getter
@Setter
@Entity
@Table(name = "injectors")
@EntityListeners({ModelBaseListener.class})
@IdClass(ConnectorCompositeId.class)
/**
 * Fully on v2 tenant isolation (TenantStatementInspector + can_access_tenant). The v1
 * {@code @Filter("tenantFilter")} and {@code TenantIdBaseListener} were removed at go-live: the
 * inspector scopes every query, and write attribution is explicit via TenantWriteScopeResolver. Do
 * not re-add either.
 */
public class Injector extends BaseConnectorEntity implements TenantIdBase {

  @Id
  @Column(name = "injector_id")
  @JsonProperty("injector_id")
  @NotBlank
  private String id;

  @Id
  @Column(name = "tenant_id")
  @JsonIgnore
  private String tenantId;

  @Column(name = "injector_name")
  @JsonProperty("injector_name")
  @NotBlank
  private String name;

  @Column(name = "injector_type")
  @JsonProperty("injector_type")
  @NotBlank
  private String type;

  @Column(name = "injector_category")
  @JsonProperty("injector_category")
  private String category;

  @Column(name = "injector_external")
  @JsonProperty("injector_external")
  private boolean external = false;

  @Column(name = "injector_custom_contracts")
  @JsonProperty("injector_custom_contracts")
  private boolean customContracts = false;

  @Column(name = "injector_executor_commands")
  @JsonProperty("injector_executor_commands")
  @Type(PostgreSQLHStoreType.class)
  private Map<String, String> executorCommands = new HashMap<>();

  @Column(name = "injector_executor_clear_commands")
  @JsonProperty("injector_executor_clear_commands")
  @Type(PostgreSQLHStoreType.class)
  private Map<String, String> executorClearCommands = new HashMap<>();

  @Column(name = "injector_payloads")
  @JsonProperty("injector_payloads")
  private boolean payloads = false;

  @Column(name = "injector_created_at")
  @JsonProperty("injector_created_at")
  @NotNull
  private Instant createdAt = now();

  @Column(name = "injector_updated_at")
  @JsonProperty("injector_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @Queryable(filterable = true, searchable = true)
  @Type(StringArrayType.class)
  @Column(name = "injector_dependencies", columnDefinition = "text[]")
  @JsonProperty("injector_dependencies")
  private ExternalServiceDependency[] dependencies;

  @OneToMany(mappedBy = "injector", fetch = FetchType.LAZY)
  @JsonIgnore
  private Set<InjectorInjectorContract> injectorContractLinks = new HashSet<>();

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.INJECTOR;

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
    return id.equals(base.getId());
  }

  @JsonIgnore
  public Set<InjectorContract> getContracts() {
    return this.injectorContractLinks.stream()
        .map(InjectorInjectorContract::getInjectorContract)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  // All link writes go through the owning side (InjectorContract.injectorLinks, which carries the
  // cascade). This side stays a read-only inverse loaded by Hibernate, so merging an injector never
  // reconciles an unsaved link and never fails with "Unable to find InjectorInjectorContract".
  public void linkContract(InjectorContract contract) {
    boolean alreadyLinked =
        contract.getInjectorLinks().stream()
            .anyMatch(l -> Objects.equals(l.getInjectorId(), this.id));
    if (!alreadyLinked) {
      contract.getInjectorLinks().add(new InjectorInjectorContract(this, contract));
    }
  }

  public void unlinkContract(InjectorContract contract) {
    contract.getInjectorLinks().removeIf(l -> Objects.equals(l.getInjectorId(), this.id));
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
