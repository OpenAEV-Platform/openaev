package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdDeserializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "connector_instance_configurations")
@EntityListeners(ModelBaseListener.class)
public class ConnectorInstanceConfiguration implements Base {

  @Id
  @Column(name = "connector_instance_configuration_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("connector_instance_configuration_id")
  @NotBlank
  private String id;

  @Column(name = "connector_instance_configuration_key")
  @JsonProperty("connector_instance_configuration_key")
  @NotBlank
  private String key;

  @Column(name = "connector_instance_configuration_value", columnDefinition = "jsonb")
  @Type(JsonType.class)
  @JsonProperty("connector_instance_configuration_value")
  @NotBlank
  private JsonNode value;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connector_instance_id", nullable = false)
  @JsonIgnore
  @NotNull
  @JsonSerialize(using = MonoIdDeserializer.class)
  private ConnectorInstance connectorInstance;
}
