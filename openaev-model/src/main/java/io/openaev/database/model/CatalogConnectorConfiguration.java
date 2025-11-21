package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdDeserializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "catalog_connectors_configuration")
@EntityListeners(ModelBaseListener.class)
public class CatalogConnectorConfiguration implements Base {
  public enum CONNECTOR_CONFIGURATION_TYPE {
    ARRAY,
    BOOLEAN,
    INTEGER,
    OBJECT,
    STRING
  }

  public enum CONNECTOR_CONFIGURATION_FORMAT {
    DATE,
    DATETIME,
    DURATION,
    EMAIL,
    PASSWORD,
    URI
  }

  @Id
  @Column(name = "connector_configuration_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("connector_configuration_id")
  @Schema(description = "Connector ID")
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connector_configuration_catalog_id", nullable = false)
  @JsonIgnore
  @Schema(description = "Catalog connector")
  @NotNull
  @JsonSerialize(using = MonoIdDeserializer.class)
  private CatalogConnector catalogConnector;

  @Column(name = "connector_configuration_key")
  @JsonProperty("connector_configuration_key")
  @NotNull
  @Schema(description = "Connector configuration key")
  private String connectorConfigurationKey;

  @Type(JsonType.class)
  @Column(name = "connector_configuration_default", columnDefinition = "jsonb")
  @JsonProperty("connector_configuration_default")
  @Schema(description = "Connector configuration default")
  private JsonNode connectorConfigurationDefault;

  @Column(name = "connector_configuration_description")
  @JsonProperty("connector_configuration_description")
  @Schema(description = "Connector configuration description")
  private String connectorConfigurationDescription;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "connector_configuration_type")
  @JsonProperty("connector_configuration_type")
  @NotNull
  @Schema(description = "Connector configuration type")
  private CONNECTOR_CONFIGURATION_TYPE connectorConfigurationType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "connector_configuration_format")
  @JsonProperty("connector_configuration_format")
  @Schema(description = "Connector configuration format")
  private CONNECTOR_CONFIGURATION_FORMAT connectorConfigurationFormat;

  @Type(ListArrayType.class)
  @Column(name = "connector_configuration_enum")
  @JsonProperty("connector_configuration_enum")
  @Schema(description = "Connector configuration enum")
  private Set<String> connectorConfigurationEnum = new HashSet<>();

  @Column(name = "connector_configuration_writeonly")
  @JsonProperty("connector_configuration_writeonly")
  @Schema(description = "Connector configuration write only")
  private boolean connectorConfigurationWriteOnly;

  @Column(name = "connector_configuration_required")
  @JsonProperty("connector_configuration_required")
  @Schema(description = "Connector configuration required")
  private boolean connectorConfigurationRequired;
}
