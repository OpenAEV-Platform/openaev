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
import io.openaev.helper.MonoIdSerializer;
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

  // -- Author (user / team / organization) --
  // Every injector contract has an author. It is stored directly on the contract
  // (payload-less built-in contracts are authored by Filigran, custom contracts
  // by their creator) AND falls back to the underlying payload's author for
  // payload-based contracts whose author lives on the payload. The three typed
  // getters are output-only (id per type); a SINGLE filterable getter ORs across
  // every contract- and payload-level author path so the UI exposes one "Author"
  // filter (autocomplete grouped by type) rather than several. --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "injector_contract_author_user")
  @JsonIgnore
  private User authorUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "injector_contract_author_team")
  @JsonIgnore
  private Team authorTeam;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "injector_contract_author_organization")
  @JsonIgnore
  private Organization authorOrganization;

  @JsonProperty("injector_contract_payload_author_user")
  @JsonSerialize(using = MonoIdSerializer.class)
  public User getPayloadAuthorUser() {
    if (authorUser != null) {
      return authorUser;
    }
    return ofNullable(getPayload()).map(Payload::getAuthorUser).orElse(null);
  }

  @JsonProperty("injector_contract_payload_author_team")
  @JsonSerialize(using = MonoIdSerializer.class)
  public Team getPayloadAuthorTeam() {
    if (authorTeam != null) {
      return authorTeam;
    }
    return ofNullable(getPayload()).map(Payload::getAuthorTeam).orElse(null);
  }

  @JsonProperty("injector_contract_payload_author_organization")
  @JsonSerialize(using = MonoIdSerializer.class)
  public Organization getPayloadAuthorOrganization() {
    if (authorOrganization != null) {
      return authorOrganization;
    }
    return ofNullable(getPayload()).map(Payload::getAuthorOrganization).orElse(null);
  }

  // Single polymorphic author filter: resolves to the id of whichever author is
  // set (contract-level first, then payload-level). paths() makes FilterUtilsJpa
  // OR the predicate across every author FK.
  @Queryable(
      filterable = true,
      dynamicValues = true,
      paths = {
        "authorUser.id",
        "authorTeam.id",
        "authorOrganization.id",
        "payload.authorUser.id",
        "payload.authorTeam.id",
        "payload.authorOrganization.id"
      })
  @JsonProperty("injector_contract_payload_author")
  public String getPayloadAuthor() {
    if (authorUser != null) {
      return authorUser.getId();
    }
    if (authorTeam != null) {
      return authorTeam.getId();
    }
    if (authorOrganization != null) {
      return authorOrganization.getId();
    }
    Payload contractPayload = getPayload();
    if (contractPayload == null) {
      return null;
    }
    if (contractPayload.getAuthorUser() != null) {
      return contractPayload.getAuthorUser().getId();
    }
    if (contractPayload.getAuthorTeam() != null) {
      return contractPayload.getAuthorTeam().getId();
    }
    if (contractPayload.getAuthorOrganization() != null) {
      return contractPayload.getAuthorOrganization().getId();
    }
    return null;
  }

  // NOTE: do NOT add @Fetch(FetchMode.SUBSELECT) to the collections of this entity. Contracts are
  // loaded through Inject's EAGER @JoinColumnsOrFormulas association; subselect-fetching their
  // collections re-renders the loading query's SQL AST and triggers an infinite recursion
  // (StackOverflowError) in Hibernate's AbstractSqlAstWalker on paginated inject searches.
  // hibernate.default_batch_fetch_size keeps these collections batched instead.
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

  // UpdatedAt is synced manually with linked objects because join-table changes do not dirty this
  // row. Only bump when contents actually change: an unconditional bump forces an UPDATE (and an
  // SSE restream) on every no-op collector/injector upsert (#6778).
  public void setTags(Set<Tag> tags) {
    if (Base.haveSameIds(this.tags, tags)) {
      // Keep the stored collection: swapping in an equal one dereferences the persistent
      // collection and makes Hibernate rewrite every join row for nothing.
      return;
    }
    this.updatedAt = now();
    this.tags = tags;
  }

  @Queryable(filterable = true)
  @OneToOne(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE, orphanRemoval = true)
  @JoinColumn(name = "injector_contract_payload")
  @JsonProperty("injector_contract_payload")
  private Payload payload;

  // Never updatable: a source-driven upsert that goes through a merge (registration rebuilds the
  // contract from the declaration, so the incoming instance carries a fresh createdAt) would
  // otherwise overwrite the stored creation date, dirty the row, bump @UpdateTimestamp and
  // restream the contract to every connected browser.
  @Column(name = "injector_contract_created_at", updatable = false)
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

  @OneToMany(
      mappedBy = "injectorContract",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
  @JsonIgnore
  private List<InjectorInjectorContract> injectorLinks = new ArrayList<>();

  @JsonIgnore
  public List<Injector> getInjectors() {
    return this.injectorLinks.stream()
        .map(InjectorInjectorContract::getInjector)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public void setInjectors(List<Injector> injectors) {
    clearInjectors();
    if (injectors != null) {
      injectors.forEach(this::addInjector);
    }
  }

  /** Removes all injector links from this owning-side collection. */
  public void clearInjectors() {
    this.injectorLinks.clear();
  }

  /** Unlinks a single injector from this contract. */
  public void removeInjector(Injector injector) {
    if (injector == null) {
      return;
    }
    this.injectorLinks.removeIf(l -> Objects.equals(l.getInjectorId(), injector.getId()));
  }

  /**
   * Convenience method: returns the first linked injector, or null. All injectors sharing a
   * contract have the same type, so this is safe for type/name lookups. TODO : remove this method
   * when multi connector is ready
   */
  @JsonIgnore
  @Deprecated
  public Injector getFirstInjector() {
    List<Injector> current = getInjectors();
    return current.isEmpty() ? null : current.getFirst();
  }

  /**
   * Links an injector to this contract through the {@link InjectorInjectorContract} join entity on
   * this owning side. The join row is persisted by the cascade on {@code injectorLinks} when the
   * contract is saved. Idempotent, and enforces that all injectors on a contract share the same
   * type.
   */
  public void addInjector(Injector injector) {
    if (injector == null) {
      return;
    }
    // The cross-tenant invariant is enforced in the InjectorInjectorContract constructor below, the
    // single point where every link is created (so linkContract is covered too).
    List<Injector> current = getInjectors();
    if (current.contains(injector)) {
      return;
    }
    if (!current.isEmpty() && !current.getFirst().getType().equals(injector.getType())) {
      throw new IllegalArgumentException(
          "Cannot link injector of type "
              + injector.getType()
              + " to contract already linked to type "
              + current.getFirst().getType());
    }
    this.injectorLinks.add(new InjectorInjectorContract(injector, this));
  }

  /** Links each injector to this contract via {@link #addInjector(Injector)}. */
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

  // UpdatedAt synced with linked objects; only bump on real changes (see setTags)
  public void setAttackPatterns(List<AttackPattern> attackPatterns) {
    if (Base.haveSameIds(this.attackPatterns, attackPatterns)) {
      return;
    }
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

  // Source-driven upserts re-apply the SAME domains on every cycle (an injector re-registers every
  // ~40s, a collector re-upserts its payloads on every run). Handing Hibernate a new Set instance
  // dereferences the persistent collection, so it rewrites every join row even when the contents
  // are identical - pure churn on injectors_contracts_domains. Keep the stored collection when the
  // ids match (see setTags).
  public void setDomains(Set<Domain> domains) {
    if (!Base.haveSameIds(this.domains, domains)) {
      this.domains = domains;
    }
  }

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

  // UpdatedAt synced with linked objects; only bump on real changes (see setTags)
  public void setVulnerabilities(Set<Vulnerability> vulnerabilities) {
    if (Base.haveSameIds(this.vulnerabilities, vulnerabilities)) {
      return;
    }
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
  @Queryable(filterable = true, dynamicValues = true, path = "injectorLinks.injector.id")
  private List<String> getInjectorIds() {
    return getInjectors().stream()
        .filter(Objects::nonNull)
        .map(Injector::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /** Returns a map of injector ID → injector name for all linked injectors. */
  @JsonProperty("injector_contract_injector_names")
  private Map<String, String> getInjectorNames() {
    return getInjectors().stream()
        .filter(i -> i != null && i.getId() != null && i.getName() != null)
        .collect(
            Collectors.toMap(Injector::getId, Injector::getName, (a, b) -> a, LinkedHashMap::new));
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
  public static final String AVAILABLE_EXPECTATIONS = "availableExpectations";
  public static final String IS_PREDEFINED_EXPECTATION = "expectation_is_predefined";

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
