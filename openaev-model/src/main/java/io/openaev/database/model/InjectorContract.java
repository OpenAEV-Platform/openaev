package io.openaev.database.model;

import static java.time.Instant.now;
import static java.util.Optional.ofNullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLHStoreType;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.converter.ContentConverter;
import io.openaev.helper.MonoIdDeserializer;
import io.openaev.helper.MultiIdListDeserializer;
import io.openaev.helper.MultiIdSetDeserializer;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "injectors_contracts")
@EntityListeners(ModelBaseListener.class)
public class InjectorContract implements Base {

  @Id
  @Column(name = "injector_contract_id")
  @JsonProperty("injector_contract_id")
  @NotBlank
  private String id;

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

  @Column(name = "injector_contract_needs_executor")
  @JsonProperty("injector_contract_needs_executor")
  private Boolean needsExecutor = false;

  @Type(StringArrayType.class)
  @Enumerated(EnumType.STRING)
  @Column(name = "injector_contract_platforms", columnDefinition = "text[]")
  @JsonProperty("injector_contract_platforms")
  @Queryable(filterable = true)
  private Endpoint.PLATFORM_TYPE[] platforms = new Endpoint.PLATFORM_TYPE[0];

  @Queryable(filterable = true, dynamicValues = true, path = "payload.executionArch")
  @JsonProperty("injector_contract_arch")
  @Enumerated(EnumType.STRING)
  public Payload.PAYLOAD_EXECUTION_ARCH getArch() {
    return ofNullable(getPayload()).map(payload -> payload.getExecutionArch()).orElse(null);
  }

  @ManyToOne(fetch = FetchType.EAGER)
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
  @Queryable(sortable = true)
  @UpdateTimestamp
  private Instant updatedAt = now();

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "injector_id")
  @JsonSerialize(using = MonoIdDeserializer.class)
  @JsonProperty("injector_contract_injector")
  @Queryable(filterable = true, dynamicValues = true)
  @NotNull
  @Schema(type = "string")
  private Injector injector;

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "injectors_contracts_attack_patterns",
      joinColumns = @JoinColumn(name = "injector_contract_id"),
      inverseJoinColumns = @JoinColumn(name = "attack_pattern_id"))
  @JsonSerialize(using = MultiIdListDeserializer.class)
  @JsonProperty("injector_contract_attack_patterns")
  @Queryable(searchable = true, filterable = true, path = "attackPatterns.externalId")
  private List<AttackPattern> attackPatterns = new ArrayList<>();

  // UpdatedAt now used to sync with linked object
  public void setAttackPatterns(List<AttackPattern> attackPatterns) {
    this.updatedAt = now();
    this.attackPatterns = attackPatterns;
  }

  @ArraySchema(schema = @Schema(type = "string"))
  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "injectors_contracts_vulnerabilities",
      joinColumns = @JoinColumn(name = "injector_contract_id"),
      inverseJoinColumns = @JoinColumn(name = "vulnerability_id"))
  @JsonSerialize(using = MultiIdSetDeserializer.class)
  @JsonProperty("injector_contract_vulnerabilities")
  @Queryable(searchable = true, filterable = true, path = "vulnerabilities.externalId")
  private Set<Cve> vulnerabilities = new HashSet<>();

  // UpdatedAt now used to sync with linked object
  public void setVulnerabilities(Set<Cve> vulnerabilities) {
    this.updatedAt = now();
    this.vulnerabilities = vulnerabilities;
  }

  @Column(name = "injector_contract_atomic_testing")
  @JsonProperty("injector_contract_atomic_testing")
  @Queryable(filterable = true)
  private boolean isAtomicTesting;

  @Column(name = "injector_contract_import_available")
  @JsonProperty("injector_contract_import_available")
  @Queryable(filterable = true)
  private boolean isImportAvailable;

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.INJECTOR_CONTRACT;

  @JsonProperty("injector_contract_injector_type")
  private String getInjectorType() {
    return this.getInjector() != null ? this.getInjector().getType() : null;
  }

  @JsonProperty("injector_contract_injector_type_name")
  private String getInjectorName() {
    return this.getInjector() != null ? this.getInjector().getName() : null;
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
    if (o == null || !Base.class.isAssignableFrom(o.getClass())) {
      return false;
    }
    Base base = (Base) o;
    return id.equals(base.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
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
