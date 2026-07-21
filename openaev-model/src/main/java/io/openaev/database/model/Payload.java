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
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.database.model.BaseInjectExpectation.EXPECTATION_TYPE;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.openaev.helper.CollectorTypeNameSerializer;
import io.openaev.helper.MonoIdDeserializerHelper;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.jsonapi.IncludeOption;
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

@Data
@Entity
@Table(name = "payloads")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "payload_type", discriminatorType = STRING)
@EntityListeners({ModelBaseListener.class, TenantBaseListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
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
public class Payload implements GrantableBase, TenantBase {

  private static final int DEFAULT_NUMBER_OF_ACTIONS_FOR_PAYLOAD = 1;

  public enum PAYLOAD_SOURCE {
    COMMUNITY,
    FILIGRAN,
    MANUAL
  }

  public enum PAYLOAD_STATUS {
    UNVERIFIED,
    VERIFIED,
    DEPRECATED
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

  @Setter
  @Type(JsonType.class)
  @Column(name = "payload_arguments")
  @JsonProperty("payload_arguments")
  private List<PayloadArgument> arguments = new ArrayList<>();

  @Setter
  @Type(JsonType.class)
  @Column(name = "payload_prerequisites")
  @JsonProperty("payload_prerequisites")
  private List<PayloadPrerequisite> prerequisites = new ArrayList<>();

  @Setter
  @Column(name = "payload_external_id")
  @JsonProperty("payload_external_id")
  private String externalId;

  @Setter
  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_source")
  @Enumerated(EnumType.STRING)
  @JsonProperty("payload_source")
  @NotNull
  private PAYLOAD_SOURCE source;

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
  @Type(JsonType.class)
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

  // -- COLLECTOR TYPE --

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "payload_collector_type")
  @JsonSerialize(using = CollectorTypeNameSerializer.class)
  @JsonProperty("payload_collector_type")
  @IncludeOption(key = "exclude from payload export")
  @Schema(implementation = String.class)
  private CollectorType collectorType;

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

  // -- AUDIT --

  @CreationTimestamp
  @Column(name = "payload_created_at")
  @JsonProperty("payload_created_at")
  @NotNull
  private Instant createdAt = now();

  @UpdateTimestamp
  @Queryable(filterable = true, sortable = true)
  @Column(name = "payload_updated_at")
  @JsonProperty("payload_updated_at")
  @NotNull
  private Instant updatedAt = now();

  @JsonIgnore
  public String getCollectorTypeValue() {
    return this.collectorType != null ? this.collectorType.getName() : null;
  }

  @Transient
  public PayloadType getTypeEnum() {
    return PayloadType.fromString(type);
  }

  // Clear zip password for an encrypted malware sample, populated only in the
  // per-execution executable-payload response delivered to the implant over TLS.
  // Never persisted and never present in listing/detail serializations.
  @Transient
  @JsonProperty("payload_sample_zip_password")
  private String sampleZipPassword;

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
