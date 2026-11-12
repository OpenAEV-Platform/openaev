package io.openaev.database.raw;

import java.util.Set;

/**
 * Spring Data projection interface for the chained-workflow fields of a scenario.
 *
 * <p>A workflow inherits its owner's tenant and can never move, so these fields are read in the
 * scenario's own tenant whatever the caller's scope, apart from the rest of the scenario's detail.
 *
 * @see RawScenario
 */
public interface RawScenarioWorkflowFields {

  /**
   * Returns the id of the scenario's workflow template, if any.
   *
   * @return the workflow id, or {@code null} when the scenario carries no workflow
   */
  String getWorkflow_id();

  /**
   * Returns the platforms the workflow's template steps target, if any.
   *
   * @return the workflow's platforms, or {@code null} when none could be derived from its steps
   */
  Set<String> getWorkflow_platforms();
}
