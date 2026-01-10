package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
  @NotNull
  private JsonNode value;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connector_instance_id", nullable = false)
  @JsonIgnore
  @NotNull
  @JsonSerialize(using = MonoIdSerializer.class)
  private ConnectorInstancePersisted connectorInstance;

  @Column(name = "connector_instance_configuration_is_encrypted")
  @JsonProperty("connector_instance_configuration_is_encrypted")
  private boolean isEncrypted = false;
}
