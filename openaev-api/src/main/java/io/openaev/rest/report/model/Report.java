package io.openaev.rest.report.model;

import static java.time.Instant.now;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.audit.ModelBaseListener;
import io.openaev.database.model.Base;
import io.openaev.database.model.Exercise;
import io.openaev.helper.MonoIdSerializer;
import io.openaev.helper.MultiModelSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Data
@Entity
@Table(name = "reports")
@EntityListeners(ModelBaseListener.class)
public class Report implements Base {

  @Id
  @Column(name = "report_id")
  @JsonProperty("report_id")
  @GeneratedValue
  @UuidGenerator
  @NotNull
  private UUID id;

  @Column(name = "report_name")
  @JsonProperty("report_name")
  @NotBlank
  private String name;

  @Column(name = "report_global_observation")
  @JsonProperty("report_global_observation")
  private String globalObservation;

  @CreationTimestamp
  @Column(name = "report_created_at")
  @JsonProperty("report_created_at")
  @NotNull
  private Instant creationDate = now();

  @UpdateTimestamp
  @Column(name = "report_updated_at")
  @JsonProperty("report_updated_at")
  @NotNull
  private Instant updateDate = now();

  @OneToMany(
      mappedBy = "report",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @JsonProperty("report_informations")
  private List<ReportInformation> reportInformations = new ArrayList<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinTable(
      name = "reports_exercises",
      joinColumns = @JoinColumn(name = "report_id"),
      inverseJoinColumns = @JoinColumn(name = "exercise_id"))
  @JsonProperty("report_exercise")
  @JsonSerialize(using = MonoIdSerializer.class)
  @Schema(type = "string")
  private Exercise exercise;

  @OneToMany(
      mappedBy = "report",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  @JsonProperty("report_injects_comments")
  @JsonSerialize(using = MultiModelSerializer.class)
  private List<ReportInjectComment> reportInjectsComments = new ArrayList<>();

  @Override
  public String getId() {
    return this.id != null ? this.id.toString() : "";
  }

  @Override
  public void setId(String id) {
    this.id = UUID.fromString(id);
  }

  @Override
  public String toString() {
    return name;
  }
}
