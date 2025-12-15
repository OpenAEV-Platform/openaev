package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.List;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Builder
@Table(name = "conditions")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Condition {

  @Id
  @Column(name = "condition_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @Schema(description = "ID of the step")
  private String id;

  @Column(name = "condition_key")
  @Schema(description = "Key")
  private String key;

  @Column(name = "condition_value")
  @Schema(description = "Value")
  private String value;

  @Column(name = "condition_type")
  @Schema(description = "Type")
  @Enumerated(EnumType.STRING)
  private CONDITION_TYPE type;

  @OneToOne(mappedBy = "id")
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
