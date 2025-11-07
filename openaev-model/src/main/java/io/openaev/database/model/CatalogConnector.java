package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.array.StringArrayType;
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
import org.checkerframework.common.aliasing.qual.Unique;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "catalog_connectors")
@EntityListeners(ModelBaseListener.class)
public class CatalogConnector implements Base {

  @Id
  @Column(name = "connector_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("connector_id")
  @Schema(description = "Connector ID")
  private String id;

  @Unique
  @NotBlank
  @Column(name = "connector_title")
  @JsonProperty("connector_title")
  @Schema(description = "Connector title")
  private String connectorTitle;

  @Column(name = "connector_slug")
  @JsonProperty("connector_slug")
  @Schema(description = "Connector slug")
  private String connectorSlug;

  @Column(name = "connector_description")
  @JsonProperty("connector_description")
  @Schema(description = "Connector description")
  private String connectorDescription;

  @Column(name = "connector_short_description")
  @JsonProperty("connector_short_description")
  @Schema(description = "Connector description")
  private String connectorShortDescription;

  @Column(name = "connector_logo")
  @JsonProperty("connector_logo")
  @Schema(description = "Connector logo")
  private String connectorLogo;

  @Type(StringArrayType.class)
  @Column(name = "connector_use_cases")
  @JsonProperty("connector_use_cases")
  @Schema(description = "Connector use_cases")
  private String[] connectorUseCases;

  @Column(name = "connector_verified")
  @JsonProperty("connector_verified")
  @Schema(description = "Connector verified")
  private boolean connectorVerified;

  @Column(name = "connector_last_verified_date")
  @JsonProperty("connector_last_verified_date")
  @Schema(description = "Connector last verified date")
  private Instant connectorLastVerifiedDate;

  @Column(name = "connector_playbook_supported")
  @JsonProperty("connector_playbook_supported")
  @Schema(description = "Connector playbook supported")
  private boolean connectorPlaybookSupported;

  @Column(name = "connector_max_confidence_level")
  @JsonProperty("connector_max_confidence_level")
  @Schema(description = "Connector max confidence level")
  private Integer connectorMaxConfidenceLevel;

  @Column(name = "connector_support_version")
  @JsonProperty("connector_support_version")
  @Schema(description = "Connector support version")
  private String connectorSupportVersion;

  @Column(name = "connector_subscription_link")
  @JsonProperty("connector_subscription_link")
  @Schema(description = "Connector subscription link")
  private String connectorSubscriptionLink;

  @Column(name = "connector_source_code")
  @JsonProperty("connector_source_code")
  @Schema(description = "Connector source code")
  private String connectorSourceCode;

  @Column(name = "connector_manager_supported")
  @JsonProperty("connector_manager_supported")
  @Schema(description = "Connector manager supported")
  private boolean connectorManagerSupported;

  @Column(name = "connector_container_version")
  @JsonProperty("connector_container_version")
  @Schema(description = "Connector container version")
  private String connectorContainerVersion;

  @Column(name = "connector_container_image")
  @JsonProperty("connector_container_image")
  @Schema(description = "Connector container image")
  private String connectorContainerImage;

  @Column(name = "connector_container_type")
  @JsonProperty("connector_container_type")
  @Schema(description = "Connector container type")
  private String connectorContainerType;

  @OneToMany(
      mappedBy = "catalogConnector",
      fetch = FetchType.EAGER,
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
  private Set<ConnectorInstance> configurations = new HashSet<>();
}
