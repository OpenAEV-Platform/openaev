package io.openaev.rest.inject.form;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.openaev.database.model.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InjectDependencyInput {

  @JsonProperty("dependency_relationship")
  private InjectDependencyIdInput relationship;

  @JsonProperty("dependency_condition")
  private InjectDependencyConditions.InjectDependencyCondition conditions;

  public InjectDependency toInjectDependency(
      @NotNull final Inject inject, @NotNull final Inject injectParent) {
    InjectDependency dependency = new InjectDependency();
    dependency.setInjectDependencyCondition(this.getConditions());
    dependency.setCompositeId(new InjectDependencyId());
    dependency.getCompositeId().setInjectChildren(inject);
    dependency.getCompositeId().setInjectParent(injectParent);
    return dependency;
  }
}
