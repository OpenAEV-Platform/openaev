package io.openaev.database.model;

import static jakarta.persistence.DiscriminatorType.STRING;
import static java.time.Instant.now;
import static lombok.AccessLevel.NONE;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.annotation.ControlledUuidGeneration;
import io.openaev.annotation.Queryable;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.audit.TenantBaseListener;
import io.openaev.database.model.Endpoint.PLATFORM_TYPE;
import io.openaev.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openaev.helper.CollectorTypeNameSerializer;
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
      NetworkTraffic.class
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
          schema = NetworkTraffic.class)
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

  @JsonIgnore
  public Optional<Document> getAttachedDocument() {
    return Optional.empty();
  }

  @JsonIgnore
  public List<String> getArgumentsDocumentsIds() {
    return this.getArguments().stream()
        .filter(payloadArgument -> payloadArgument.getType().equals("document"))
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
