package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Builder
@Table(name = "conditions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Condition implements Base {

  @Id
  @Column(name = "condition_id")
  @JsonProperty("condition_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @Schema(description = "ID of the condition")
  private String id;

  @OneToOne
  @JoinColumn(name = "step_from_id", unique = true)
  @JsonIgnore
  @Schema(description = "Source step for this condition")
  private Step stepFrom;

  @Column(name = "condition_key")
  @JsonProperty("condition_key")
  @Schema(description = "Key")
  private String key;

  @Column(name = "condition_value")
  @JsonProperty("condition_value")
  @Schema(description = "Value")
  private String value;

  @Column(name = "condition_type")
  @JsonProperty("condition_type")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Schema(description = "Type")
  private ConditionType type;

  @OneToOne
  @JoinColumn(name = "step_id", unique = true, nullable = false)
  @JsonIgnore
  @Schema(description = "Step to which this condition belongs")
  private Step step;

  @JoinColumn(name = "condition_parent_id")
  @ManyToOne(fetch = FetchType.LAZY)
  @JsonIgnore
  @Schema(description = "Parent condition if this is a child condition")
  private Condition conditionParent;

  @OneToMany(
      fetch = FetchType.LAZY,
      mappedBy = "conditionParent",
      cascade = CascadeType.ALL,
      orphanRemoval = true)
  @JsonIgnore
  @Builder.Default
  @Schema(description = "Child conditions of this condition")
  private List<Condition> conditionChildren = new ArrayList<>();

  @CreationTimestamp
  @Column(name = "condition_created_at")
  @JsonProperty("condition_created_at")
  @Schema(description = "Creation timestamp")
  private Instant creationDate;

  @UpdateTimestamp
  @Column(name = "condition_updated_at")
  @JsonProperty("condition_updated_at")
  @Schema(description = "Last update timestamp")
  private Instant updateDate;
}
