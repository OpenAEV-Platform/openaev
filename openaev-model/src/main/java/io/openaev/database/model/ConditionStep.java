package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Setter
@Getter
@Entity
@Table(
    name = "conditions_steps",
    uniqueConstraints = {@UniqueConstraint(columnNames = {"condition_id", "step_id"})})
@EqualsAndHashCode
public class ConditionStep {

  @Id
  @Column(name = "condition_step_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "condition_id", nullable = false)
  @JsonIgnore
  private Condition condition;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "step_id", nullable = false)
  @JsonIgnore
  private Step step;

  @Column(name = "is_root", nullable = false)
  private boolean isRoot = false;
}
