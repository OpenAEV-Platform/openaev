package io.openaev.database.model;

import static java.time.Instant.now;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLHStoreType;
import io.openaev.annotation.Queryable;
import io.openaev.context.TenantContext;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.database.converter.ContentConverter;
import io.openaev.helper.CompositeIdResolvableI;
import io.openaev.helper.MonoIdDeserializerHelper;
import io.openaev.helper.MultiIdListSerializer;
import io.openaev.helper.MultiIdSetSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "injectors_contracts")
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class InjectorContract implements TenantBase, CompositeIdResolvableI {

  public static final String ID_FIELD_NAME = "id";
  public static final String COMPOSITE_ID_FIELD_NAME = "compositeId";

  // -- Delegate accessors for Base / TenantBase interfaces --
  @EmbeddedId @JsonIgnore private InjectorContractId compositeId = new InjectorContractId();

  @Override
  @JsonProperty("injector_contract_id")
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

  @Column(name = "injector_contract_external_id", unique = true)
  @JsonProperty("injector_contract_external_id")
  @Nullable
  private String externalId;

  @Column(name = "injector_contract_labels")
  @JsonProperty("injector_contract_labels")
  @Type(PostgreSQLHStoreType.class)
  @Queryable(searchable = true, filterable = true, sortable = true)
  private Map<String, String> labels = new HashMap<>();

  @Column(name = "injector_contract_manual")
  @JsonProperty("injector_contract_manual")
  private Boolean manual;

  @JsonProperty("injector_contract_manual")
  public boolean getManualEffective() {
    return Boolean.TRUE.equals(manual);
  }

  @Column(name = "injector_contract_content")
  @JsonProperty("injector_contract_content")
  @NotBlank
  private String content;

  @Column(name = "injector_contract_content", insertable = false, updatable = false)
  @Convert(converter = ContentConverter.class)
  private ObjectNode convertedContent;

  @Column(name = "injector_contract_custom")
  @JsonProperty("injector_contract_custom")
  private Boolean custom = false;

  @JsonProperty("injector_contract_custom")
  public boolean getCustomEffective() {
    return Boolean.TRUE.equals(custom);
  }

  @Column(name = "injector_contract_needs_executor")
  @JsonProperty("injector_contract_needs_executor")
  private Boolean needsExecutor = false;

  @JsonProperty("injector_contract_needs_executor")
  public boolean getNeedsExecutorEffective() {
    return Boolean.TRUE.equals(needsExecutor);
  }

  @Type(StringArrayType.class)
  @Enumerated(EnumType.STRING)
  @Column(name = "injector_contract_platforms", columnDefinition = "text[]")
  @JsonProperty("injector_contract_platforms")
  @Queryable(filterable = true)
  private Endpoint.PLATFORM_TYPE[] platforms = new Endpoint.PLATFORM_TYPE[0];

  @JsonProperty("injector_contract_platforms")
  public Endpoint.PLATFORM_TYPE[] getInjectorContractPlatformEffective() {
    return platforms;
  }

  @Queryable(
      filterable = true,
      path = "payload.executionArch",
      refEnumClazz = Payload.PAYLOAD_EXECUTION_ARCH.class)
  @JsonProperty("injector_contract_arch")
  @Enumerated(EnumType.STRING)
  public Payload.PAYLOAD_EXECUTION_ARCH getArch() {
    return ofNullable(getPayload()).map(Payload::getExecutionArch).orElse(null);
  }

  @Queryable(
      filterable = true,
      path = "payload.status",
      refEnumClazz = Payload.PAYLOAD_STATUS.class)
  @JsonProperty("injector_contract_payload_status")
  @Enumerated(EnumType.STRING)
  public Payload.PAYLOAD_STATUS getPayloadStatus() {
    return ofNullable(getPayload()).map(Payload::getStatus).orElse(null);
  }

  @Schema(implementation = String[].class)
  @Getter
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "injector_contract_tags",
      joinColumns = {
        @JoinColumn(name = "injector_contract_id", referencedColumnName = "injector_contract_id"),
        @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id")
      },
      inverseJoinColumns = @JoinColumn(name = "tag_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonDeserialize(contentUsing = MonoIdDeserializerHelper.class)
  @JsonProperty("injector_contract_tags")
  @Queryable(filterable = true, dynamicValues = true, path = "tags.id")
  private Set<Tag> tags = new HashSet<>();

  // UpdatedAt now used to sync with linked object
  public void setTags(Set<Tag> tags) {
    this.updatedAt = now();
    this.tags = tags;
  }

  @Queryable(filterable = true)
  @OneToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "injector_contract_payload")
  @JsonProperty("injector_contract_payload")
  private Payload payload;

  @Column(name = "injector_contract_created_at")
  @JsonProperty("injector_contract_created_at")
  @NotNull
  @CreationTimestamp
  private Instant createdAt = now();

  @Column(name = "injector_contract_updated_at")
  @JsonProperty("injector_contract_updated_at")
  @NotNull
  @Queryable(filterable = true, sortable = true)
  @UpdateTimestamp
  private Instant updatedAt = now();

  @ManyToMany(mappedBy = "contracts", fetch = FetchType.EAGER)
  @JsonIgnore
  private List<Injector> injectors = new ArrayList<>();

  /**
   * Convenience method: returns the first linked injector, or null. All injectors sharing a
   * contract have the same type, so this is safe for type/name lookups. TODO : remove this method
   * when multi connector is ready
   */
  @JsonIgnore
  @Deprecated
  public Injector getFirstInjector() {
    return (injectors != null && !injectors.isEmpty()) ? injectors.getFirst() : null;
  }

  /**
   * Sets the injector reference on this contract (inverse side only). Safe to call on transient
   * contracts — does NOT modify the owning side ({@code Injector.contracts}), so it will not cause
   * Hibernate auto-flush issues.
   *
   * <p>After the contract is persisted, update the owning side directly via {@code
   * injector.getContracts().add(contract)} for join-table persistence.
   */
  public void addInjector(Injector injector) {
    if (injector != null && !this.injectors.contains(injector)) {
      if (!this.injectors.isEmpty()
          && !this.injectors.getFirst().getType().equals(injector.getType())) {
        throw new IllegalArgumentException(
            "Cannot link injector of type "
                + injector.getType()
                + " to contract already linked to type "
                + this.injectors.getFirst().getType());
      }
      this.injectors.add(injector);
    }
  }

  /**
   * Sets the injector reference on this contract (inverse side only). Safe to call on transient
   * contracts — does NOT modify the owning side ({@code Injector.contracts}), so it will not cause
   * Hibernate auto-flush issues.
   *
   * <p>After the contract is persisted, update the owning side directly via {@code
   * injector.getContracts().add(contract)} for join-table persistence.
   */
  public void addInjectors(List<Injector> injectors) {
    if (injectors != null) {
      injectors.forEach(this::addInjector);
    }
  }

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "injectors_contracts_attack_patterns",
      joinColumns = {
        @JoinColumn(name = "injector_contract_id", referencedColumnName = "injector_contract_id"),
        @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id")
      },
      inverseJoinColumns = @JoinColumn(name = "attack_pattern_id"))
  @JsonSerialize(using = MultiIdListSerializer.class)
  @JsonDeserialize(contentUsing = MonoIdDeserializerHelper.class)
  @JsonProperty("injector_contract_attack_patterns")
  @Queryable(searchable = true, filterable = true, path = "attackPatterns.externalId")
  private List<AttackPattern> attackPatterns = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setAttackPatterns(List<AttackPattern> attackPatterns) {
    this.updatedAt = now();
    this.attackPatterns = attackPatterns;
  }

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "injectors_contracts_domains",
      joinColumns = {
        @JoinColumn(name = "injector_contract_id", referencedColumnName = "injector_contract_id"),
        @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id")
      },
      inverseJoinColumns = @JoinColumn(name = "domain_id"))
  @JsonProperty("injector_contract_domains")
  @Queryable(filterable = true, dynamicValues = true, paths = "domains.id")
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonDeserialize(contentUsing = MonoIdDeserializerHelper.class)
  private Set<Domain> domains = new HashSet<>();

  @Schema(implementation = String[].class)
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "injectors_contracts_vulnerabilities",
      joinColumns = {
        @JoinColumn(name = "injector_contract_id", referencedColumnName = "injector_contract_id"),
        @JoinColumn(name = "tenant_id", referencedColumnName = "tenant_id")
      },
      inverseJoinColumns = @JoinColumn(name = "vulnerability_id"))
  @JsonSerialize(using = MultiIdSetSerializer.class)
  @JsonDeserialize(contentUsing = MonoIdDeserializerHelper.class)
  @JsonProperty("injector_contract_vulnerabilities")
  @Queryable(searchable = true, filterable = true, path = "vulnerabilities.externalId")
  private Set<Vulnerability> vulnerabilities = new HashSet<>();

  // UpdatedAt now used to sync with linked object
  public void setVulnerabilities(Set<Vulnerability> vulnerabilities) {
    this.updatedAt = now();
    this.vulnerabilities = vulnerabilities;
  }

  // Fixes a bug due to a new version of jackson and lombok
  // cf: https://github.com/projectlombok/lombok/issues/3978
  @Getter(onMethod_ = @JsonProperty("injector_contract_atomic_testing"))
  @JsonProperty("injector_contract_atomic_testing")
  @Column(name = "injector_contract_atomic_testing")
  @Queryable(filterable = true)
  private boolean isAtomicTesting;

  @JsonProperty("injector_contract_atomic_testing")
  public boolean getAtomicTestingEffective() {
    return Boolean.TRUE.equals(isAtomicTesting);
  }

  // Fixes a bug due to a new version of jackson and lombok
  // cf: https://github.com/projectlombok/lombok/issues/3978
  @Getter(onMethod_ = @JsonProperty("injector_contract_import_available"))
  @JsonProperty("injector_contract_import_available")
  @Column(name = "injector_contract_import_available")
  @Queryable(filterable = true)
  private boolean isImportAvailable;

  @JsonProperty("injector_contract_import_available")
  public boolean getImportAvailableEffective() {
    return isImportAvailable;
  }

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.INJECTOR_CONTRACT;

  /** Returns all linked injector IDs. */
  @JsonProperty("injector_contract_injectors")
  @Schema(implementation = String[].class)
  @Queryable(filterable = true, dynamicValues = true, path = "injectors.id")
  private List<String> getInjectorIds() {
    return injectors != null
        ? new ArrayList<>(injectors.stream().map(Injector::getId).toList())
        : Collections.emptyList();
  }

  /** Returns a map of injector ID → injector name for all linked injectors. */
  @JsonProperty("injector_contract_injector_names")
  private Map<String, String> getInjectorNames() {
    return injectors != null
        ? injectors.stream()
            .collect(
                Collectors.toMap(
                    Injector::getId, Injector::getName, (a, b) -> a, LinkedHashMap::new))
        : Collections.emptyMap();
  }

  @JsonProperty("injector_contract_injector_type")
  public String getInjectorType() {
    Injector first = getFirstInjector();
    return first != null ? first.getType() : null;
  }

  @JsonIgnore
  @JsonProperty("injector_contract_kill_chain_phases")
  @Queryable(filterable = true, dynamicValues = true, path = "attackPatterns.killChainPhases.id")
  public List<KillChainPhase> getKillChainPhases() {
    return getAttackPatterns().stream()
        .flatMap(attackPattern -> attackPattern.getKillChainPhases().stream())
        .distinct()
        .toList();
  }

  @JsonIgnore
  @Override
  public boolean isUserHasAccess(User user) {
    return user.isAdmin();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !InjectorContract.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    InjectorContract that = (InjectorContract) o;
    return Objects.equals(compositeId, that.compositeId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(compositeId);
  }

  @Override
  public Object resolveCompositeId(String rawId, DeserializationContext ctxt) {
    String tenantId = TenantContext.getCurrentTenant();
    InjectorContractId compositeId = new InjectorContractId();
    compositeId.setId(rawId);
    compositeId.setTenantId(tenantId);
    return compositeId;
  }

  // -- INJECTOR CONTRACT CONTENT --

  public static final String CONTRACT_CONTENT_FIELDS = "fields";
  public static final String CONTRACT_ELEMENT_CONTENT_KEY = "key";
  public static final String CONTRACT_ELEMENT_CONTENT_TYPE = "type";
  public static final String CONTRACT_ELEMENT_CONTENT_CARDINALITY = "cardinality";
  public static final String CONTRACT_ELEMENT_CONTENT_MANDATORY = "mandatory";
  public static final String CONTRACT_ELEMENT_CONTENT_MANDATORY_GROUPS = "mandatoryGroups";
  public static final String CONTRACT_ELEMENT_CONTENT_MANDATORY_CONDITIONAL_FIELDS =
      "mandatoryConditionFields";
  public static final String CONTRACT_ELEMENT_CONTENT_MANDATORY_CONDITIONAL_VALUES =
      "mandatoryConditionValues";
  public static final String DEFAULT_VALUE_FIELD = "defaultValue";
  public static final String PREDEFINED_EXPECTATIONS = "predefinedExpectations";

  public static final String CONTRACT_ELEMENT_CONTENT_KEY_TEAMS = "teams";
  public static final String CONTRACT_ELEMENT_CONTENT_KEY_ASSETS = "assets";
  public static final String CONTRACT_ELEMENT_CONTENT_KEY_ASSET_GROUPS = "asset_groups";
  public static final String CONTRACT_ELEMENT_CONTENT_KEY_ARTICLES = "articles";
  public static final String CONTRACT_ELEMENT_CONTENT_KEY_CHALLENGES = "challenges";
  public static final String CONTRACT_ELEMENT_CONTENT_KEY_ATTACHMENTS = "attachments";
  public static final String CONTRACT_ELEMENT_CONTENT_KEY_EXPECTATIONS = "expectations";
  public static final String CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_PROPERTY = "targeted-property";
  public static final String CONTRACT_ELEMENT_CONTENT_KEY_TARGETED_ASSET_SEPARATOR =
      "targeted-asset-separator";

  public static final String CONTRACT_ELEMENT_CONTENT_TYPE_ASSET = "asset";
  public static final String CONTRACT_ELEMENT_CONTENT_TYPE_ASSET_GROUP = "asset-group";
  public static final String CONTRACT_ELEMENT_CONTENT_TYPE_TEAM = "team";
  public static final String CONTRACT_ELEMENT_CONTENT_TYPE_EXPECTATION = "expectation";

  public static final List<String> CONTRACT_ELEMENT_CONTENT_KEY_NOT_DYNAMIC =
      List.of(
          CONTRACT_ELEMENT_CONTENT_KEY_TEAMS,
          CONTRACT_ELEMENT_CONTENT_KEY_ARTICLES,
          CONTRACT_ELEMENT_CONTENT_KEY_CHALLENGES,
          CONTRACT_ELEMENT_CONTENT_KEY_ATTACHMENTS);
}
