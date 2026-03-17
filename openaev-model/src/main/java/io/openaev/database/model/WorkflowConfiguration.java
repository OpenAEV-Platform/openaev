package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "workflow_configurations")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkflowConfiguration implements Base {

  @Id
  @Column(name = "workflow_configuration_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("workflow_configuration_id")
  @NotBlank
  private String id;

  // Rate limit
  @Column(name = "workflow_configuration_rate_limit_enabled", columnDefinition = "boolean")
  @JsonProperty("workflow_configuration_rate_limit_enabled")
  private boolean rateLimitEnabled;

  @Column(name = "workflow_configuration_max_attempts")
  @JsonProperty("workflow_configuration_max_attempts")
  @Min(1)
  @Max(99)
  private Integer maxAttempts;

  @Column(name = "workflow_configuration_max_temporal_rate_seconds")
  @JsonProperty("workflow_configuration_max_temporal_rate_seconds")
  @Min(1)
  @Max(59)
  private Long maxTemporalRateSeconds;

  // Timeout
  @Column(name = "workflow_configuration_timeout_enabled", columnDefinition = "boolean")
  @JsonProperty("workflow_configuration_timeout_enabled")
  private boolean timeoutEnabled;

  @Column(name = "workflow_configuration_timeout_seconds")
  @JsonProperty("workflow_configuration_timeout_seconds")
  @Min(0)
  @Max(86400) // 24h
  private Long timeoutSeconds;

  // Safe mode
  @Column(name = "workflow_configuration_safe_mode_enabled", columnDefinition = "boolean")
  @JsonProperty("workflow_configuration_safe_mode_enabled")
  private boolean safeModeEnabled;

  @CreationTimestamp
  @Column(name = "workflow_configuration_created_at")
  @JsonProperty("workflow_configuration_created_at")
  private Instant createdAt;

  @UpdateTimestamp
  @Column(name = "workflow_configuration_updated_at")
  @JsonProperty("workflow_configuration_updated_at")
  private Instant updatedAt;

  @OneToOne
  @JoinColumn(name = "workflow_configuration_workflow", referencedColumnName = "workflow_id")
  @JsonIgnore
  private Workflow workflow;
}
