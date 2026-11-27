package io.openaev.architecture.fixture;

import io.openaev.database.model.Exercise;
import io.openaev.service.chaining.WorkflowOwnerScope;
import java.util.function.BiConsumer;

/** Violates the caller allowlist: calls WorkflowOwnerScope without being listed. */
public class OwnerScopeOutsideAllowlistFixture {

  private final WorkflowOwnerScope workflowOwnerScope;

  public OwnerScopeOutsideAllowlistFixture(WorkflowOwnerScope workflowOwnerScope) {
    this.workflowOwnerScope = workflowOwnerScope;
  }

  public void runInOwnerTenant(Exercise simulation) {
    workflowOwnerScope.run(simulation, () -> {});
  }

  public BiConsumer<Exercise, WorkflowOwnerScope.ScopedAction<RuntimeException>> asReference() {
    return workflowOwnerScope::run;
  }
}
