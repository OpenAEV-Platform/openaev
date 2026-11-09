package io.openaev.database.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@Entity
@Table(name = "finding_occurrences")
public class FindingOccurrence implements Base {

  @Id
  @Column(name = "finding_occurrence_id")
  @JsonProperty("finding_occurrence_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @NotBlank
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_occurrence_finding_id")
  @JsonIgnore
  @NotNull
  private Finding finding;

  // Nullable: the occurrence outlives a relaunch, which deletes its inject
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "finding_occurrence_inject_id")
  @JsonIgnore
  private Inject inject;

  @Column(name = "finding_occurrence_date")
  @JsonProperty("finding_occurrence_date")
  @NotNull
  private Instant date;

  @Column(name = "finding_occurrence_field")
  @JsonProperty("finding_occurrence_field")
  private String field;

  @Column(name = "finding_occurrence_created_at", nullable = false, updatable = false)
  @JsonProperty("finding_occurrence_created_at")
  @NotNull
  private Instant createdAt = now();

  @Override
  public boolean isListened() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FindingOccurrence other)) return false;
    return id != null && id.equals(other.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }
}
