package io.openaev.database.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Correlation progress tracker (ADR-007). Maps 1:1 the {@code workflow_state_correlation_progress}
 * table added in the chunk 1 migration. Keyed by the business {@code correlation_hash} (not a
 * technical id): it tracks which keys are present for a given correlation and whether it is
 * complete and ready to fire.
 */
@Entity
@Table(name = "workflow_state_correlation_progress")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkflowStateCorrelationProgress {

  /** Business primary key: never generated. */
  @Id
  @Column(name = "correlation_hash")
  @EqualsAndHashCode.Include
  private byte[] correlationHash;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_state_id", nullable = false)
  private WorkflowState workflowState;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "keys_present", columnDefinition = "text[]", nullable = false)
  @Builder.Default
  private List<String> keysPresent = new ArrayList<>();

  @Column(name = "is_complete", nullable = false)
  private boolean isComplete;
}
