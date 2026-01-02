package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
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
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Schema(description = "ID of the condition")
  private String id;

  @OneToOne
  @JoinColumn(name = "step_from_id", unique = true, nullable = true)
  private Step stepFrom;

  @Column(name = "condition_key")
  @Schema(description = "Key")
  private String key;

  @Column(name = "condition_value")
  @Schema(description = "Value")
  private String value;

  @Column(name = "condition_type")
  @Schema(description = "Type")
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  private CONDITION_TYPE type;

  @OneToOne
  @JoinColumn(name = "step_id", unique = true, nullable = false)
  private Step step;

  @JoinColumn(name = "condition_parent_id")
  @ManyToOne(fetch = FetchType.LAZY)
  private Condition conditionParent;

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "id")
  private List<Condition> conditionChildren;

  @CreationTimestamp
  @Column(name = "condition_created_at")
  @JsonProperty("condition_created_at")
  private Instant creationDate;

  @UpdateTimestamp
  @Column(name = "condition_updated_at")
  @JsonProperty("condition_updated_at")
  private Instant updateDate;
}
