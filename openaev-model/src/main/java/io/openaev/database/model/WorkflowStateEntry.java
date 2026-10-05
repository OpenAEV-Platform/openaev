package io.openaev.database.model;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Normalized WorkflowState entry (ADR-010). Maps 1:1 the {@code workflow_state_entries} table added
 * by the {@code V6_20260908080000000} migration. One row = one normalized entry belonging to a
 * {@link WorkflowState}, discriminated by {@link EntryType}. A correlated tuple is stored as one
 * {@link EntryType#CORRELATED} row per field, all sharing the same {@link #correlationHash} and
 * {@link #correlationType}.
 */
@Entity
@Table(name = "workflow_state_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class WorkflowStateEntry {

  public enum EntryType {
    INPUT,
    CORRELATED,
    HASH_EXECUTION
  }

  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @EqualsAndHashCode.Include
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_state_id", nullable = false)
  private WorkflowState workflowState;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false)
  private EntryType entryType;

  @Column(name = "entry_key", nullable = false)
  private String entryKey;

  @Column(name = "entry_value", nullable = false)
  private String entryValue;

  /** MurmurHash3-128 (hex) of the tuple. Non-null only for {@link EntryType#CORRELATED} rows. */
  @Column(name = "correlation_hash")
  private String correlationHash;

  /**
   * Business type of the tuple ({@code ContractOutputType.name()}). Non-null only for {@link
   * EntryType#CORRELATED} rows.
   */
  @Column(name = "correlation_type")
  private String correlationType;

  @Column(name = "created_at", nullable = false, updatable = false)
  @CreationTimestamp
  private Instant createdAt;
}
