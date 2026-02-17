package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "connector_instance_logs")
@EntityListeners(ModelBaseListener.class)
public class ConnectorInstanceLog implements Base {
  @Id
  @Column(name = "connector_instance_log_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("connector_instance_log_id")
  @NotBlank
  private String id;

  @Column(name = "connector_instance_log")
  @JsonProperty("connector_instance_log")
  @Schema(description = "Connector instance log")
  private String log;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "connector_instance_id", nullable = false)
  @JsonIgnore
  @NotNull
  @JsonSerialize(using = MonoIdSerializer.class)
  private ConnectorInstancePersisted connectorInstance;

  @Column(name = "connector_instance_log_created_at")
  @JsonProperty("connector_instance_log_created_at")
  @Schema(description = "Connector instance log created at")
  private Instant connector_instance_log_created_at = Instant.now();
}
