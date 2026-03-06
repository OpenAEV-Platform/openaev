package io.openaev.rest.workflow.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ScopeInput {

  @JsonProperty("workflow_scope")
  private String workflowScope;

  @JsonProperty("workflow_timeout")
  private Long workflowTimeout;
}
