package io.openaev.executors.mde.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Minimal view of an MDE machine action (used to detect and cancel stale Live Response sessions).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MdeMachineAction {

  private String id;
  private String type;
  private String status;
  private String creationDateTimeUtc;
}
