package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "chaining_configurations")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChainingConfiguration implements Base {

  @Id
  @UuidGenerator
  @Column(name = "chaining_configuration_id")
  @JsonProperty("chaining_configuration_id")
  @NotBlank
  private String id;

  @Type(JsonType.class)
  @Column(name = "chaining_configuration_rate_limit", columnDefinition = "jsonB")
  @JsonProperty("chaining_configuration_rate_limit")
  private ChainingRateLimit rateLimit;

  @Type(JsonType.class)
  @Column(name = "chaining_configuration_time_out", columnDefinition = "jsonB")
  @JsonProperty("chaining_configuration_time_out")
  private ChainingTimeOut timeOut;

  @Column(name = "chaining_configuration_enable_safe_mode", columnDefinition = "boolean")
  @JsonProperty("chaining_configuration_enable_safe_mode")
  private boolean isSafeMode;

  @CreationTimestamp
  @Column(name = "chaining_configuration_created_at")
  @JsonProperty("chaining_configuration_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "chaining_configuration_updated_at")
  @JsonProperty("chaining_configuration_updated_at")
  private Instant updatedAt;

  @OneToOne
  @JoinColumn(name = "chaining_configuration_workflow", referencedColumnName = "workflow_id")
  @JsonIgnore
  private Workflow workflow;
}
