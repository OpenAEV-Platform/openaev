package io.openaev.rest.report.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.openaev.database.model.Inject;
import io.openaev.helper.MonoIdSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Entity
@Table(name = "report_inject_comment")
public class ReportInjectComment {
  @EmbeddedId @JsonIgnore private ReportInjectCommentId compositeId = new ReportInjectCommentId();

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("injectId")
  @JoinColumn(name = "inject_id")
  @JsonIgnore // Ignore Inject object in JSON
  @JsonSerialize(using = MonoIdSerializer.class)
  @NotNull
  @Schema(type = "string")
  private Inject inject;

  @ManyToOne(fetch = FetchType.LAZY)
  @MapsId("reportId")
  @JoinColumn(name = "report_id")
  @JsonIgnore // Ignore Inject object in JSON
  @JsonSerialize(using = MonoIdSerializer.class)
  @NotNull
  @Schema(type = "string")
  private Report report;

  @Column(name = "comment")
  @JsonProperty("report_inject_comment")
  private String comment;

  @JsonProperty("inject_id")
  public String getInjectId() {
    return inject != null ? inject.getId() : null; // Customize serialization to return ID
  }

  @JsonProperty("report_id")
  public String getReportId() {
    return report != null ? report.getId() : null; // Customize serialization to return ID
  }
}
