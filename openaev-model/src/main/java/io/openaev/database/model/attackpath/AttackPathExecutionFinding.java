package io.openaev.database.model.attackpath;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

/**
 * Many-to-many link keeping which execution produced which finding (issue 6647). Not tenant-aware:
 * a pure join table, filtered through the tenant-scoped {@code attackpath_finding} it is joined to,
 * as with OpenAEV's other join tables. It preserves the exact finding→action trace even when the
 * render dedups the finding node.
 */
@Getter
@Setter
@Entity
@Table(name = "attackpath_execution_finding")
@IdClass(AttackPathExecutionFinding.AttackPathExecutionFindingId.class)
public class AttackPathExecutionFinding {

  @Id
  @Column(name = "execution_id")
  private String executionId;

  @Id
  @Column(name = "finding_id")
  private String findingId;

  /** Composite primary key (execution_id, finding_id). */
  public static class AttackPathExecutionFindingId implements Serializable {

    private String executionId;
    private String findingId;

    public AttackPathExecutionFindingId() {}

    public AttackPathExecutionFindingId(String executionId, String findingId) {
      this.executionId = executionId;
      this.findingId = findingId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof AttackPathExecutionFindingId that)) {
        return false;
      }
      return Objects.equals(executionId, that.executionId)
          && Objects.equals(findingId, that.findingId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(executionId, findingId);
    }
  }
}
