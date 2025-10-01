package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InjectDependencyIdInput {

  @JsonProperty("inject_parent_id")
  private String injectParentId;

  @JsonProperty("inject_children_id")
  private String injectChildrenId;
}
