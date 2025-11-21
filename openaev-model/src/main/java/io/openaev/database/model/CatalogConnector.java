package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import io.openaev.database.audit.ModelBaseListener;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "catalog_connectors")
@EntityListeners(ModelBaseListener.class)
public class CatalogConnector implements Base {
  public enum CONNECTOR_TYPE {
    COLLECTOR,
    INJECTOR,
    EXECUTOR
  }

  @Id
  @NotNull
  @Column(name = "catalog_connector_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("connector_id")
  @Schema(description = "Connector ID")
  private String id;

  @NotBlank
  @Column(name = "catalog_connector_title", unique = true)
  @JsonProperty("connector_title")
  @Schema(description = "Connector title")
  private String title;

  @Column(name = "catalog_connector_slug")
  @JsonProperty("catalog_connector_slug")
  @Schema(description = "Connector slug")
  private String slug;

  @Column(name = "catalog_connector_description")
  @JsonProperty("catalog_connector_description")
  @Schema(description = "Connector description")
  private String description;

  @Column(name = "catalog_connector_short_description")
  @JsonProperty("catalog_connector_short_description")
  @Schema(description = "Connector description")
  private String shortDescription;

  @Column(name = "catalog_connector_logo_url")
  @JsonProperty("catalog_connector_logo_url")
  @Schema(description = "Connector logo")
  private String logoUrl;

  @Type(ListArrayType.class)
  @Column(name = "catalog_connector_use_cases")
  @JsonProperty("catalog_connector_use_cases")
  @Schema(description = "Connector use cases")
  private Set<String> useCases = new HashSet<>();

  @Column(name = "catalog_connector_verified")
  @JsonProperty("catalog_connector_verified")
  @Schema(description = "Connector verified")
  private boolean isVerified;

  @Column(name = "catalog_connector_last_verified_date")
  @JsonProperty("catalog_connector_last_verified_date")
  @Schema(description = "Connector last verified date")
  private Instant lastVerifiedDate;

  @Column(name = "catalog_connector_playbook_supported")
  @JsonProperty("catalog_connector_playbook_supported")
  @Schema(description = "Connector playbook supported")
  private boolean isPlaybookSupported;

  @Column(name = "catalog_connector_max_confidence_level")
  @JsonProperty("catalog_connector_max_confidence_level")
  @Schema(description = "Connector max confidence level")
  private Integer maxConfidenceLevel;

  @Column(name = "catalog_connector_support_version")
  @JsonProperty("catalog_connector_support_version")
  @Schema(description = "Connector support version")
  private String supportVersion;

  @Column(name = "catalog_connector_subscription_link")
  @JsonProperty("catalog_connector_subscription_link")
  @Schema(description = "Connector subscription link")
  private String subscriptionLink;

  @Column(name = "catalog_connector_source_code")
  @JsonProperty("catalog_connector_source_code")
  @Schema(description = "Connector source code")
  private String sourceCode;

  @Column(name = "catalog_connector_manager_supported")
  @JsonProperty("catalog_connector_manager_supported")
  @Schema(description = "Connector manager supported")
  private boolean isManagerSupported;

  @Column(name = "catalog_connector_container_version")
  @JsonProperty("catalog_connector_container_version")
  @Schema(description = "Connector container version")
  private String containerVersion;

  @Column(name = "catalog_connector_container_image")
  @JsonProperty("catalog_connector_container_image")
  @Schema(description = "Connector container image")
  private String containerImage;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "catalog_connector_type")
  @JsonProperty("catalog_connector_type")
  @Schema(description = "Connector type")
  private CONNECTOR_TYPE containerType;

  @Column(name = "catalog_connector_class_name")
  @JsonProperty("catalog_connector_class_name")
  @Schema(description = "Connector class name")
  private String className;

  @Column(name = "catalog_connector_deleted_at")
  @JsonProperty("catalog_connector_deleted_at")
  @Schema(description = "Connector deleted at")
  private Instant deletedAt;

  @OneToMany(
      mappedBy = "catalogConnector",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("catalog_connector_configuration")
  @NotNull
  private Set<CatalogConnectorConfiguration> catalogConnectorConfigurations = new HashSet<>();

  @OneToMany(
      mappedBy = "catalogConnector",
      fetch = FetchType.LAZY,
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonProperty("catalog_connector_instances")
  @NotNull
  private Set<ConnectorInstance> instances = new HashSet<>();
}
