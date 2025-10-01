package io.openaev.database.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.audit.ModelBaseListener;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "security_coverage_send_job")
@EntityListeners(ModelBaseListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SecurityCoverageSendJob implements Base {

  @Id
  @Column(name = "security_coverage_send_job_id")
  @GeneratedValue(generator = "UUID")
  @UuidGenerator
  @EqualsAndHashCode.Include
  @JsonProperty("security_assessment_id")
  private String id;

  @Column(name = "security_coverage_send_job_status")
  @JsonProperty("security_coverage_send_job_status")
  private String status = "PENDING";

  @OneToOne
  @JoinColumn(name = "security_coverage_send_job_simulation", nullable = false)
  @JsonProperty("security_coverage_send_job_simulation")
  private Exercise simulation;

  @UpdateTimestamp
  @Column(name = "security_coverage_send_job_updated_at")
  @JsonProperty("security_coverage_send_job_updated_at")
  private Instant updatedAt;
}
