package io.openaev.database.model;

import static jakarta.persistence.DiscriminatorType.STRING;
import static java.time.Instant.now;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.Auditable;
import io.openaev.database.audit.AuditableListener;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.openaev.helper.CollectorTypeNameSerializer;
import io.openaev.helper.MonoIdDeserializerHelper;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.jsonapi.IncludeOption;
import io.openaev.jsonapi.JsonApiExtraAttributes;
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

@Data
@Entity
@Table(name = "payloads")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "payload_type", discriminatorType = STRING)
@EntityListeners({ModelBaseListener.class, AuditableListener.class})
// payloads is fully on v2 (statement inspector + can_access_tenant); no v1 @Filter, no
// TenantBaseListener (#6437, #6430). A create path that forgets to stamp the tenant fails loudly
// on the NOT NULL constraint instead of being silently defaulted to the ambient tenant.
@Schema(
    discriminatorProperty = "payload_type",
    oneOf = {
      Command.class,
      Executable.class,
      FileDrop.class,
      DnsResolution.class,
      NetworkTraffic.class,
      AiAttack.class
    },
    discriminatorMapping = {
      @DiscriminatorMapping(value = Command.COMMAND_TYPE, schema = Command.class),
      @DiscriminatorMapping(value = Executable.EXECUTABLE_TYPE, schema = Executable.class),
      @DiscriminatorMapping(value = FileDrop.FILE_DROP_TYPE, schema = FileDrop.class),
      @DiscriminatorMapping(
          value = DnsResolution.DNS_RESOLUTION_TYPE,
          schema = DnsResolution.class),
      @DiscriminatorMapping(
          value = NetworkTraffic.NETWORK_TRAFFIC_TYPE,
          schema = NetworkTraffic.class),
      @DiscriminatorMapping(value = AiAttack.AI_ATTACK_TYPE, schema = AiAttack.class)
    })
@Grantable(Grant.GRANT_RESOURCE_TYPE.PAYLOAD)
public class Payload implements GrantableBase, TenantBase, Auditable, JsonApiExtraAttributes {

  private static final int DEFAULT_NUMBER_OF_ACTIONS_FOR_PAYLOAD = 1;

  public enum PAYLOAD_SOURCE {
    COMMUNITY,
    FILIGRAN,
    MANUAL
  }

  public enum PAYLOAD_STATUS {
    UNVERIFIED,
    VERIFIED,
    DEPRECATED,
    REVOKED
  }

  public enum PAYLOAD_EXECUTION_ARCH {
    x86_64,
    arm64,
    ALL_ARCHITECTURES,
  }

  @Id
  @ControlledUuidGeneration
  @Column(name = "payload_id")
  @JsonProperty("payload_id")
  @NotBlank
  private String id;

  @Column(name = "payload_type", insertable = false, updatable = false)
  @JsonProperty("payload_type")
  @Setter(NONE)
  private String type;

  @Queryable(filterable = true, searchable = true, sortable = true)
  @Column(name = "payload_name")
  @JsonProperty("payload_name")
  @NotBlank
  private String name;

  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_description")
  @JsonProperty("payload_description")
  private String description;

  @Queryable(filterable = true, searchable = true)
  @Type(StringArrayType.class)
  @Column(name = "payload_platforms", columnDefinition = "text[]")
  @JsonProperty("payload_platforms")
  @NotEmpty
  private PLATFORM_TYPE[] platforms = new PLATFORM_TYPE[0];

  @Setter
  @Column(name = "payload_cleanup_executor")
  @JsonProperty("payload_cleanup_executor")
  private String cleanupExecutor;

  @Setter
  @Column(name = "payload_cleanup_command")
  @JsonProperty("payload_cleanup_command")
  private String cleanupCommand;

  @Getter
  @Column(name = "payload_elevation_required")
  @JsonProperty("payload_elevation_required")
  private boolean elevationRequired;

  @Type(JsonType.class)
  @Column(name = "payload_arguments", nullable = false)
  @JsonProperty("payload_arguments")
  private List<PayloadArgument> arguments = new ArrayList<>();

  @Type(JsonType.class)
  @Column(name = "payload_prerequisites", nullable = false)
  @JsonProperty("payload_prerequisites")
  private List<PayloadPrerequisite> prerequisites = new ArrayList<>();

  @Queryable(filterable = true, searchable = true)
  @Type(StringArrayType.class)
  @Column(name = "payload_expectations", columnDefinition = "text[]")
  @JsonProperty("payload_expectations")
  private EXPECTATION_TYPE[] expectations;

  /**
   * Optional map of expectation type to the security platform types expected to fulfil it (e.g.
   * {@code {"DETECTION": ["EDR","XDR","SIEM"], "PREVENTION": ["EDR","XDR"]}}). Empty or absent
   * means "any security platform" (legacy behaviour). Used to pre-seed only the relevant collectors
   * when this payload's predefined expectations are instantiated on an inject.
   */
  @Setter
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload_expected_security_platforms", columnDefinition = "jsonb")
  @JsonProperty("payload_expected_security_platforms")
  private Map<EXPECTATION_TYPE, List<SecurityPlatform.SECURITY_PLATFORM_TYPE>>
      expectedSecurityPlatforms = new HashMap<>();

  @Setter
  @Queryable(filterable = true)
  @Column(name = "payload_status")
  @Enumerated(EnumType.STRING)
  @JsonProperty("payload_status")
  @NotNull
  private PAYLOAD_STATUS status;

  @Queryable(filterable = true, searchable = true)
  @Column(name = "payload_execution_arch", nullable = false)
  @JsonProperty("payload_execution_arch")
  @Enumerated(EnumType.STRING)
  @NotNull
  private PAYLOAD_EXECUTION_ARCH executionArch = Payload.PAYLOAD_EXECUTION_ARCH.ALL_ARCHITECTURES;

  @ManyToOne
  @JoinColumn(name = "tenant_id", updatable = false, nullable = false)
  @JsonIgnore
  private Tenant tenant;

  // -- IDENTITY --

  // ADR-011: what every version shares. EAGER for the same reason as the collector type it holds:
  // it is serialized after the controller's transaction and tenant scope have closed.
  @ManyToOne(
      fetch = FetchType.EAGER,
      cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @JoinColumn(name = "identity_id", nullable = false)
  @JsonIgnore
  private PayloadIdentity identity;

  // -- AUTHOR (polymorphic: a payload is authored by a user, a team OR an
  // organization). The three FKs are mutually exclusive; collector-created
  // payloads are authored by the collector's organization, manually created
  // ones by the creating user. --

  // The author must never travel with payload/action exports: the JSON:API
  // exporter would embed the full user/team/organization resource and the
  // import would then re-create it (duplicate email/name in the target
  // environment). Authorship is environment-local by design.
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_author_user")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonDeserialize(using = MonoIdDeserializerHelper.class)
  @JsonProperty("payload_author_user")
  @IncludeOption(key = "exclude from payload export")
  @Queryable(dynamicValues = true, filterable = true, path = "authorUser.id")
  @Schema(description = "User author of the payload", type = "string")
  private User authorUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_author_team")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonDeserialize(using = MonoIdDeserializerHelper.class)
  @JsonProperty("payload_author_team")
  @IncludeOption(key = "exclude from payload export")
  @Queryable(dynamicValues = true, filterable = true, path = "authorTeam.id")
  @Schema(description = "Team author of the payload", type = "string")
  private Team authorTeam;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_author_organization")
  @JsonSerialize(using = MonoIdSerializer.class)
  @JsonDeserialize(using = MonoIdDeserializerHelper.class)
  @JsonProperty("payload_author_organization")
  @IncludeOption(key = "exclude from payload export")
  @Queryable(dynamicValues = true, filterable = true, path = "authorOrganization.id")
  @Schema(description = "Organization author of the payload", type = "string")
  private Organization authorOrganization;

  @OneToMany(
      mappedBy = "payload",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @JsonProperty("payload_detection_remediations")
  private List<DetectionRemediation> detectionRemediations = new ArrayList<>();

  // -- OUTPUT PARSERS

  @OneToMany(
      mappedBy = "payload",
      fetch = FetchType.EAGER,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("payload_output_parsers")
  private Set<OutputParser> outputParsers = new HashSet<>();

  @Getter
  @OneToMany
  @JoinColumn(
      name = "grant_resource",
      referencedColumnName = "payload_id",
      insertable = false,
      updatable = false)
  @SQLRestriction("grant_resource_type = 'PAYLOAD'") // Must be present in Grant.GRANT_RESOURCE_TYPE
  @JsonIgnore
  private List<Grant> grants = new ArrayList<>();

  // -- VERSIONING --
  // ADR-011: a version row never changes once created; every payload has a version.

  public static final String FIRST_VERSION = "1.0.0";

  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_version", nullable = false)
  @JsonProperty("payload_version")
  private String version = FIRST_VERSION;

  @Column(name = "payload_content_hash")
  @JsonProperty("payload_content_hash")
  private String contentHash;

  @Column(name = "payload_origin_id")
  @JsonProperty("payload_origin_id")
  private String originId;

  // -- AUDIT --

  @Column(name = "payload_created_at", updatable = false)
  @JsonProperty("payload_created_at")
  @NotNull
  private Instant createdAt = now();

  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_updated_at")
  @JsonProperty("payload_updated_at")
  @NotNull
  private Instant updatedAt = now();

  // -- IDENTITY DELEGATES: the payload JSON keeps its flat shape --

  /** The identity, a new local one when the payload has none yet. */
  @JsonIgnore
  public PayloadIdentity identityOrNew() {
    if (identity == null) {
      identity = new PayloadIdentity();
      identity.setTenant(tenant);
    }
    return identity;
  }

  // Every payload belongs to an identity, a new local one by default.
  @PrePersist
  void ensureIdentity() {
    identityOrNew();
  }

  public void setTenant(Tenant tenant) {
    this.tenant = tenant;
    if (identity != null && identity.getTenant() == null) {
      identity.setTenant(tenant);
    }
  }

  @JsonProperty("payload_external_id")
  @Queryable(filterable = true, path = "identity.externalId")
  public String getExternalId() {
    return identity == null ? null : identity.getExternalId();
  }

  @JsonProperty("payload_external_id")
  public void setExternalId(String externalId) {
    identityOrNew().setExternalId(externalId);
  }

  @JsonProperty("payload_source")
  @Queryable(filterable = true, sortable = true, path = "identity.source")
  public PAYLOAD_SOURCE getSource() {
    return identity == null ? null : identity.getSource();
  }

  @JsonProperty("payload_source")
  public void setSource(PAYLOAD_SOURCE source) {
    identityOrNew().setSource(source);
  }

  @JsonProperty("payload_collector_type")
  @JsonSerialize(using = CollectorTypeNameSerializer.class)
  @Schema(implementation = String.class)
  public CollectorType getCollectorType() {
    return identity == null ? null : identity.getCollectorType();
  }

  @JsonIgnore
  public void setCollectorType(CollectorType collectorType) {
    identityOrNew().setCollectorType(collectorType);
  }

  // The default version; never true on an uninstalled payload. Filtered through a dedicated filter.
  @JsonProperty(value = "payload_is_latest", access = JsonProperty.Access.READ_ONLY)
  public boolean isLatest() {
    return identity != null
        && !identity.isUninstalled()
        && id != null
        && id.equals(identity.getLatestPayloadId());
  }

  @JsonProperty(value = "payload_uninstalled", access = JsonProperty.Access.READ_ONLY)
  public boolean isUninstalled() {
    return identity != null && identity.isUninstalled();
  }

  /** Comes from the catalogue, hence read-only: replaces every former {@code version != null}. */
  @JsonIgnore
  public boolean isManaged() {
    return identity != null && identity.isManaged();
  }

  // The export format stays flat: the identity's external ID and source travel as attributes.
  @Override
  public Map<String, Object> extraAttributes() {
    Map<String, Object> attributes = new LinkedHashMap<>();
    attributes.put("payload_external_id", getExternalId());
    attributes.put("payload_source", getSource() == null ? null : getSource().name());
    return attributes;
  }

  @Override
  public void applyExtraAttributes(Map<String, Object> attributes) {
    if (attributes.get("payload_external_id") instanceof String externalId) {
      setExternalId(externalId);
    }
    if (attributes.get("payload_source") instanceof String source) {
      setSource(PAYLOAD_SOURCE.valueOf(source));
    }
  }

  @JsonIgnore
  public String getCollectorTypeValue() {
    CollectorType collectorType = getCollectorType();
    return collectorType != null ? collectorType.getName() : null;
  }

  @Transient
  public PayloadType getTypeEnum() {
    if (type != null) {
      return PayloadType.fromString(type);
    }
    // A row created in this session has no discriminator read back yet.
    jakarta.persistence.DiscriminatorValue value =
        org.hibernate.Hibernate.getClass(this)
            .getAnnotation(jakarta.persistence.DiscriminatorValue.class);
    return PayloadType.fromString(value == null ? null : value.value());
  }

  @JsonIgnore
  public Optional<Document> getAttachedDocument() {
    return Optional.empty();
  }

  @JsonIgnore
  public List<String> getArgumentsDocumentsIds() {
    return this.getArguments().stream()
        .filter(payloadArgument -> PrimitiveType.Document == payloadArgument.getType())
        .map(PayloadArgument::getDefaultValue)
        .toList();
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Getter(onMethod_ = @JsonIgnore)
  @Transient
  private final ResourceType resourceType = ResourceType.PAYLOAD;

  public Payload() {}

  public Payload(String id, String type, String name) {
    this.name = name;
    this.id = id;
    this.type = type;
  }

  public void setOutputParsers(final Set<OutputParser> outputParsers) {
    this.outputParsers.clear();
    outputParsers.forEach(this::addOutputParser);
  }

  public void addOutputParser(OutputParser outputParser) {
    if (outputParser != null) {
      outputParser.setPayload(this);
      this.outputParsers.add(outputParser);
    }
  }

  public void setDetectionRemediations(final List<DetectionRemediation> detectionRemediations) {
    this.detectionRemediations.clear();
    detectionRemediations.forEach(this::addDetectionRemediation);
  }

  public void addDetectionRemediation(DetectionRemediation detectionRemediation) {
    if (detectionRemediation != null) {
      detectionRemediation.setPayload(this);
      this.detectionRemediations.add(detectionRemediation);
    }
  }
}
