package io.openaev.rest.reporting.service;

import io.openaev.database.model.ReportingGeneration;
import io.openaev.database.model.User;

/**
 * Rendering engine of the reporting module: turns a PENDING {@link ReportingGeneration} into a
 * stored document (SUCCESS) or an ERROR. The real implementation ({@link
 * PlaywrightReportingRenderer}) renders asynchronously on a dedicated executor and returns
 * immediately; {@link NoopReportingRenderer} stays as a synchronous fallback bean.
 */
public interface ReportingRenderer {

  /**
   * Renders the given generation.
   *
   * @param generation the freshly persisted PENDING generation
   * @param actingUser the user identity the render runs under (the requesting user for MANUAL
   *     triggers, the schedule owner for SCHEDULED ones); may be null, in which case the render
   *     fails with an explicit error
   */
  void render(ReportingGeneration generation, User actingUser);
}
