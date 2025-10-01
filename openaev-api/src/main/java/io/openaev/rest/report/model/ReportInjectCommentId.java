package io.openaev.rest.report.model;

import jakarta.persistence.Embeddable;
import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.Data;

@Data
@Embeddable
public class ReportInjectCommentId implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String injectId;
  private UUID reportId;

  public ReportInjectCommentId() {
    // Default constructor
  }
}
