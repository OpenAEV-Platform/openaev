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
@Table(name = "chaining_configurations")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChainingConfiguration implements Base {

  @Id
  @Column(name = "chaining_configuration_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @JsonProperty("chaining_configuration_id")
  @NotBlank
  private String id;

  // Rate limit
  @Column(name = "chaining_configuration_rate_limit_enabled", columnDefinition = "boolean")
  @JsonProperty("chaining_configuration_rate_limit_enabled")
  private boolean rateLimitEnabled;

  @Column(name = "chaining_configuration_max_attempts")
  @JsonProperty("chaining_configuration_max_attempts")
  @Min(1)
  @Max(99)
  private Integer maxAttempts;

  @Column(name = "chaining_configuration_max_temporal_rate_seconds")
  @JsonProperty("chaining_configuration_max_temporal_rate_seconds")
  @Min(1)
  @Max(59)
  private Long maxTemporalRateSeconds;

  // Timeout
  @Column(name = "chaining_configuration_timeout_enabled", columnDefinition = "boolean")
  @JsonProperty("chaining_configuration_timeout_enabled")
  private boolean timeoutEnabled;

  @Column(name = "chaining_configuration_timeout_seconds")
  @JsonProperty("chaining_configuration_timeout_seconds")
  @Min(0)
  @Max(86400) // 24h
  private Long timeoutSeconds;

  // Safe mode
  @Column(name = "chaining_configuration_safe_mode_enabled", columnDefinition = "boolean")
  @JsonProperty("chaining_configuration_safe_mode_enabled")
  private boolean safeModeEnabled;

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
